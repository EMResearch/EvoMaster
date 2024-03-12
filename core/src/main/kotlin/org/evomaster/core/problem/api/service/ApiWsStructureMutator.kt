package org.evomaster.core.problem.api.service

import com.google.inject.Inject
import org.evomaster.client.java.controller.api.dto.database.execution.MongoFailedQuery
import org.evomaster.client.java.instrumentation.shared.ExternalServiceSharedUtils
import org.evomaster.core.EMConfig
import org.evomaster.core.Lazy
import org.evomaster.core.sql.SqlAction
import org.evomaster.core.sql.SqlActionUtils
import org.evomaster.core.sql.SqlInsertBuilder
import org.evomaster.core.mongo.MongoDbAction
import org.evomaster.core.problem.api.ApiWsIndividual
import org.evomaster.core.problem.enterprise.EnterpriseActionGroup
import org.evomaster.core.problem.externalservice.HostnameResolutionAction
import org.evomaster.core.problem.externalservice.httpws.service.HarvestActualHttpWsResponseHandler
import org.evomaster.core.problem.externalservice.httpws.service.HttpWsExternalServiceHandler
import org.evomaster.core.problem.externalservice.httpws.HttpExternalServiceAction
import org.evomaster.core.problem.externalservice.httpws.param.HttpWsResponseParam
import org.evomaster.core.search.EnvironmentAction
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.GroupsOfChildren
import org.evomaster.core.search.Individual
import org.evomaster.core.search.gene.sql.SqlForeignKeyGene
import org.evomaster.core.search.gene.sql.SqlPrimaryKeyGene
import org.evomaster.core.search.impact.impactinfocollection.ImpactsOfIndividual
import org.evomaster.core.search.service.mutator.MutatedGeneSpecification
import org.evomaster.core.search.service.mutator.StructureMutator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.math.max
import kotlin.math.min

/**
 * the abstract structure mutator for API based SUT, such as REST, GraphQL, RPC
 */
abstract class ApiWsStructureMutator : StructureMutator() {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ApiWsStructureMutator::class.java)
    }

    // TODO: This will moved under ApiWsFitness once RPC and GraphQL support is completed
    @Inject
    protected lateinit var externalServiceHandler: HttpWsExternalServiceHandler

    @Inject
    protected lateinit var harvestResponseHandler: HarvestActualHttpWsResponseHandler

    override fun addAndHarvestExternalServiceActions(
        individual: EvaluatedIndividual<*>,
        /**
         * TODO add why
         */
        mutatedGenes: MutatedGeneSpecification?,
    ) : Boolean{

        if (config.externalServiceIPSelectionStrategy == EMConfig.ExternalServiceIPSelectionStrategy.NONE) {
            return false
        }

        val ind = individual.individual as? ApiWsIndividual
            ?: throw IllegalArgumentException("Invalid individual type")

        val esr = individual.fitness.getViewAccessedExternalServiceRequests()
        if (esr.isEmpty()) {
            //nothing to do
            return false
        }

        var anyHarvest = false

        val newActions: MutableList<HttpExternalServiceAction> = mutableListOf()

        ind.seeMainExecutableActions().forEachIndexed { index, action ->
            val parent = action.parent
            if (parent !is EnterpriseActionGroup<*>) {
                //TODO this should not really happen
                val msg = "Action is not inside an EnterpriseActionGroup"
                log.error(msg)
                throw RuntimeException(msg)
            }

            /*
                harvest the existing external service actions
             */
            parent.getExternalServiceActions().filterIsInstance<HttpExternalServiceAction>().forEach { e->
                anyHarvest = harvestResponseHandler.harvestExistingExternalActionIfNeverSeeded(e, config.probOfHarvestingResponsesFromActualExternalServices) || anyHarvest
            }

            // Adding the new [HttpExternalServiceAction] will be handled here. Handling
            // used and not used external service actions will be handled in Fitness using
            // [used] property in action.
            if (esr.containsKey(index)) {
                val requests = esr[index]

                if (requests!!.isNotEmpty()) {
                    val existingActions = parent.getExternalServiceActions()

                    val actions: MutableList<HttpExternalServiceAction> = mutableListOf()

                    // FIXME: We are not considering the requests served by the Default WireMock server.
                    //  However, since we add a dummy [HostnameResolution] action
                    //  (org/evomaster/core/problem/rest/service/AbstractRestFitness.kt:833), at the end
                    //  there is a test looking to connect to the service and expecting a response when external
                    //  service is available.
                    //  Which is causing few tests to fails under [HarvestingStrategyTest]

                    requests
                        .groupBy { it.absoluteURL }
                        .forEach { (url, grequests) ->
                            // here, we assume that the front external service actions should be accessed
                            val startingIndex = existingActions.filterIsInstance<HttpExternalServiceAction>().count { it.request.absoluteURL == url}
                            if (startingIndex < grequests.size){
                                (startingIndex until  grequests.size).forEach {i->
                                    val actualResponse = if (config.probOfHarvestingResponsesFromActualExternalServices == 0.0) null else harvestResponseHandler.getACopyOfActualResponse(grequests[i], config.probOfHarvestingResponsesFromActualExternalServices)
                                    anyHarvest = anyHarvest || (actualResponse != null)
                                    val a = externalServiceHandler.createExternalServiceAction(grequests[i], actualResponse as? HttpWsResponseParam)
                                    a.confirmUsed()
                                    actions.add(a)
                                }
                            }
                    }

                    if (actions.isNotEmpty()) {
                        newActions.addAll(actions)
                        parent.addChildrenToGroup(
                            actions,
                            GroupsOfChildren.EXTERNAL_SERVICES
                        )
                    }
                }
            }
        }

        // all actions should have local ids
        Lazy.assert {
            ind.seeAllActions().all { it.hasLocalId() }
        }

        if (log.isTraceEnabled)
            log.trace("{} existingExternalServiceData are added", newActions.size)

        // update impact based on added genes
        // TODO: Refactored this, Man to review the place where impacts get updated.
        if (mutatedGenes != null && newActions.isNotEmpty() && config.isEnabledArchiveGeneSelection()) {
            individual.updateImpactGeneDueToAddedExternalService(mutatedGenes, newActions)
        }

        return anyHarvest
    }

    fun <T : ApiWsIndividual> addInitializingActions(
        individual: EvaluatedIndividual<*>,
        mutatedGenes: MutatedGeneSpecification?,
        sampler: ApiWsSampler<T>
    ) {
        addInitializingSqlActions(individual, mutatedGenes, sampler)
        addInitializingMongoDbActions(individual, mutatedGenes, sampler)
        addInitializingHostnameResolutionActions(individual, mutatedGenes, sampler)
    }

    private fun <T: ApiWsIndividual> addInitializingMongoDbActions(
        individual: EvaluatedIndividual<*>,
        mutatedGenes: MutatedGeneSpecification?,
        sampler: ApiWsSampler<T>
    ) {
        if (!config.shouldGenerateMongoData()) {
            return
        }

        val ind = individual.individual as? T
            ?: throw IllegalArgumentException("Invalid individual type")

        val fw = individual.fitness.getViewOfAggregatedFailedFind()

        if (fw.isEmpty()) {
            return
        }

        val oldMongoDbActions = mutableListOf<EnvironmentAction>().plus(ind.seeInitializingActions())

        val addedMongoDbInsertions = handleFailedFind(ind, fw, mutatedGenes, sampler)


        ind.repairInitializationActions(randomness)
        // update impact based on added genes
        if (mutatedGenes != null && config.isEnabledArchiveGeneSelection()) {
            individual.updateImpactGeneDueToAddedInitializationGenes(
                mutatedGenes,
                oldMongoDbActions,
                addedMongoDbInsertions,
                ImpactsOfIndividual.MONGODB_ACTION_KEY,
                config
            )
        }
    }

    private fun <T : ApiWsIndividual> addInitializingHostnameResolutionActions(
        individual: EvaluatedIndividual<*>,
        mutatedGenes: MutatedGeneSpecification?,
        sampler: ApiWsSampler<T>
    ) {

        val ind = individual.individual as? T
            ?: throw IllegalArgumentException("Invalid individual type")

        val old = ind.seeInitializingActions().filterIsInstance<HostnameResolutionAction>()

        val addedInsertions: MutableList<EnvironmentAction> = mutableListOf()
        externalServiceHandler.getHostnameResolutionActions().forEach { a ->
            val hasActions = old.any { it.hostname == a.hostname && it.localIPAddress == a.localIPAddress }
            if (!hasActions) {
                addedInsertions.add(a)
            }

            // Removing the existing action added with the default WireMock address
            val defaultActions = old.filter { it.hostname == a.hostname && it.localIPAddress == ExternalServiceSharedUtils.RESERVED_RESOLVED_LOCAL_IP };
            if (defaultActions.isNotEmpty()) {
                ind.removeHostnameResolutionAction(defaultActions)
            }
        }

        individual.individual.addInitializingHostnameResolutionActions(actions = addedInsertions)

        // FIXME: Commented out now, since no Genes in the action
        // update impact based on added genes
//        if (mutatedGenes != null && config.isEnabledArchiveGeneSelection()) {
//            individual.updateImpactGeneDueToAddedInitializationGenes(
//                mutatedGenes,
//                old,
//                listOf(addedInsertions),
//                ImpactsOfIndividual.HOSTNAME_RESOLUTION_KEY,
//                config
//            )
//        }
    }

    private fun <T : ApiWsIndividual> addInitializingSqlActions(
        evaluatedIndividual: EvaluatedIndividual<*>,
        mutatedGenes: MutatedGeneSpecification?,
        sampler: ApiWsSampler<T>
    ) {
        if (!config.shouldGenerateSqlData()) {
            return
        }

        val ind = evaluatedIndividual.individual as? T
            ?: throw IllegalArgumentException("Invalid individual type")

        /**
         * This is done on an already evaluated individual from a PREVIOUS fitness evaluation.
         * IF, in the previous evaluation it uses a DB and some SELECTs did not return data, THEN
         * create new actions for setting up SQL data.
         *
         * So adding these new actions count as a sort of mutation operator, based on fitness feedback
         * from a PREVIOUS evaluation.
         * Recall, EXTREMELY IMPORTANT, once an individual is evaluated for fitness, we CANNOT change
         * its phenotype (otherwise the fitness value would be meaningless).
         */

        val fw = evaluatedIndividual.fitness.getViewOfAggregatedFailedWhere()
            //TODO likely to remove/change once we ll support VIEWs
            .filter { sampler.canInsertInto(it.key) }

        if (fw.isEmpty()) {
            return
        }

        val oldSqlActions = mutableListOf<EnvironmentAction>().plus(ind.seeInitializingActions())

        val addedSqlInsertions = handleFailedWhereSQL(ind, fw, mutatedGenes, sampler)

        ind.repairInitializationActions(randomness)
        // update impact based on added genes
        if (mutatedGenes != null && config.isEnabledArchiveGeneSelection()) {
            evaluatedIndividual.updateImpactGeneDueToAddedInitializationGenes(
                mutatedGenes,
                oldSqlActions,
                addedSqlInsertions,
                ImpactsOfIndividual.SQL_ACTION_KEY,
                config
            )
        }
    }

    private fun <T : ApiWsIndividual> handleFailedWhereSQL(
        ind: T,
        /**
         * Map of FAILED WHERE clauses. from table name key to column name values
         */
        fw: Map<String, Set<String>>,
        mutatedGenes: MutatedGeneSpecification?, sampler: ApiWsSampler<T>
    ): MutableList<List<SqlAction>>? {

        /*
            because there might exist representExistingData in db actions which are in between rest actions,
            we use seeDbActions() instead of seeInitializingActions() here

            TODO
            Man: with config.maximumExistingDataToSampleInD,
                we might remove the condition check on representExistingData.
         */
        if (ind.seeSqlDbActions().isEmpty()
            || !ind.seeSqlDbActions().any { it is SqlAction && it.representExistingData }
        ) {

            /*
                tmp solution to set maximum size of executing existing data in sql
             */
            val existing = if (config.maximumExistingDataToSampleInDb > 0
                && sampler.existingSqlData.size > config.maximumExistingDataToSampleInDb
            ) {
                randomness.choose(sampler.existingSqlData, config.maximumExistingDataToSampleInDb)
            } else {
                sampler.existingSqlData
            }.map { it.copy() } as List<EnvironmentAction>

            //add existing data only once
            ind.addInitializingDbActions(0, existing)

            //record newly added existing sql data
            mutatedGenes?.addedExistingDataInInitialization?.getOrPut(ImpactsOfIndividual.SQL_ACTION_KEY, { mutableListOf() })?.addAll(0, existing)

            if (log.isTraceEnabled)
                log.trace("{} existingSqlData are added", existing)
        }

        // add fw into dbInitialization
        val max = config.maxSqlInitActionsPerMissingData
        val initializingActions = ind.seeInitializingActions().filterIsInstance<SqlAction>()

        var missing = findMissing(fw, initializingActions)

        val addedSqlInsertions = if (mutatedGenes != null) mutableListOf<List<SqlAction>>() else null

        while (!missing.isEmpty()) {

            val first = missing.entries.first()

            val k = randomness.nextInt(1, max)

            (0 until k).forEach { _ ->
                val insertions = sampler.sampleSqlInsertion(first.key, first.value)
                /*
                    New action should be before existing one, but still after the
                    initializing ones
                 */
                ind.addInitializingDbActions(actions = insertions)

                if (log.isTraceEnabled)
                    log.trace("{} insertions are added", insertions.size)

                //record newly added insertions
                addedSqlInsertions?.add(insertions)
            }

            /*
                When we miss A and B, and we add for A, it can still happen that
                then B is covered as well. For example, if A has a non-null
                foreign key to B, then generating an action for A would also
                imply generating an action for B as well.
                So, we need to recompute "missing" each time
             */
            missing = findMissing(fw, ind.seeInitializingActions().filterIsInstance<SqlAction>())
        }

        if (config.generateSqlDataWithDSE) {
            //TODO DSE could be plugged in here
        }

        return addedSqlInsertions
    }

    private fun <T : ApiWsIndividual> handleFailedFind(
        ind: T,
        ff: List<MongoFailedQuery>,
        mutatedGenes: MutatedGeneSpecification?, sampler: ApiWsSampler<T>
    ): MutableList<List<MongoDbAction>>? {

        val addedMongoDbInsertions = if (mutatedGenes != null) mutableListOf<List<MongoDbAction>>() else null

        ff.forEach {
            val insertion = listOf(sampler.sampleMongoInsertion(it.database, it.collection, it.documentsType))
            ind.addInitializingMongoDbActions(actions = insertion)
            addedMongoDbInsertions?.add(insertion)
        }

        return addedMongoDbInsertions
    }

    private fun findMissing(fw: Map<String, Set<String>>, dbactions: List<SqlAction>): Map<String, Set<String>> {

        return fw.filter { e ->
            //shouldn't have already an action adding such SQL data
            dbactions
                .filter { !it.representExistingData }
                .none { a ->
                    a.table.name.equals(e.key, ignoreCase = true) && e.value.all { c ->
                        // either the selected column is already in existing action
                        (c != "*" && a.selectedColumns.any { x ->
                            x.name.equals(c, ignoreCase = true)
                        }) // or we want all, and existing action has all columns
                                || (c == "*" && a.table.columns.map { it.name.lowercase() }
                            .containsAll(a.selectedColumns.map { it.name.lowercase() }))
                    }
                }
        }
    }

    override fun mutateInitStructure(
        individual: Individual,
        evaluatedIndividual: EvaluatedIndividual<*>,
        mutatedGenes: MutatedGeneSpecification?,
        targets: Set<Int>
    ) {
        Lazy.assert { individual is ApiWsIndividual }
        individual as ApiWsIndividual

        /*
           modify one table at once, and add/remove [n] rows for it. however, never totally remove the table.
           note that if there is no any init sql, we randomly select one table to add.
        */

        val candidatesToMutate =
            individual.seeInitializingActions().filterIsInstance<SqlAction>().filterNot { it.representExistingData }
        val tables = candidatesToMutate.map { it.table.name }.run {
            ifEmpty { getSqlInsertBuilder()!!.getTableNames() }
        }

        val table = randomness.choose(tables)
        val total = tables.count { it == table }

        if (total == 1 || randomness.nextBoolean()) {
            // add action
            val num = randomness.nextInt(1, max(1, getMaxSizeOfMutatingInitAction()))
            val add = createInsertSqlAction(table, num)
            handleInitSqlAddition(individual, add, mutatedGenes)

        } else {
            // remove action
            val num = randomness.nextInt(1, max(1, min(total - 1, getMaxSizeOfMutatingInitAction())))
            val candidates = candidatesToMutate.filter { it.table.name == table }
            val remove = randomness.choose(candidates, num)

            handleInitSqlRemoval(individual, remove, mutatedGenes)
        }
    }

    /**
     * add specified actions (i.e., [add]) into initialization of [individual]
     */
    fun handleInitSqlAddition(
        individual: ApiWsIndividual,
        add: List<List<SqlAction>>,
        mutatedGenes: MutatedGeneSpecification?
    ) {
        individual.addInitializingDbActions(actions = add.flatten())
        mutatedGenes?.addedSqlActions?.addAll(add)
    }

    /**
     * remove specified actions (i.e., [remove]) from initialization of [individual]
     */
    fun handleInitSqlRemoval(
        individual: ApiWsIndividual,
        remove: List<SqlAction>,
        mutatedGenes: MutatedGeneSpecification?
    ) {
        val relatedRemove = mutableListOf<SqlAction>()
        relatedRemove.addAll(remove)
        remove.forEach {
            getRelatedRemoveDbActions(individual, it, relatedRemove)
        }
        val set = relatedRemove.filterNot { it.representExistingData }.toSet().toMutableList()
        mutatedGenes?.removedSqlActions?.addAll(set.map { it to individual.seeInitializingActions().indexOf(it) })
        individual.removeInitDbActions(set)
    }

    private fun getRelatedRemoveDbActions(
        ind: ApiWsIndividual,
        remove: SqlAction,
        relatedRemove: MutableList<SqlAction>
    ) {
        val pks = remove.seeTopGenes().flatMap { it.flatView() }.filterIsInstance<SqlPrimaryKeyGene>()
        val index = ind.seeInitializingActions().indexOf(remove)
        if (index < ind.seeInitializingActions().size - 1 && pks.isNotEmpty()) {

            val removeDbFKs = ind.seeInitializingActions().filterIsInstance<SqlAction>()
                .subList(index + 1, ind.seeInitializingActions().filterIsInstance<SqlAction>().size).filter {
                    it.seeTopGenes().flatMap { g -> g.flatView() }.filterIsInstance<SqlForeignKeyGene>()
                        .any { fk -> pks.any { pk -> fk.uniqueIdOfPrimaryKey == pk.uniqueId } }
                }
            relatedRemove.addAll(removeDbFKs)
            removeDbFKs.forEach {
                getRelatedRemoveDbActions(ind, it, relatedRemove)
            }
        }
    }

    /**
     * @param name is the table name
     * @param num is a number of table with [name] to be added
     */
    fun createInsertSqlAction(name: String, num: Int): List<List<SqlAction>> {
        getSqlInsertBuilder()
            ?: throw IllegalStateException("attempt to create resource with SQL but the sqlBuilder is null")
        if (num <= 0)
            throw IllegalArgumentException("invalid num (i.e.,$num) for creating resource")

        val extraConstraints = randomness.nextBoolean(apc.getExtraSqlDbConstraintsProbability())

        val enableSingleInsertionForTable = randomness.nextBoolean(config.probOfEnablingSingleInsertionForTable)

        val chosenColumns = if(config.forceSqlAllColumnInsertion){
            setOf("*")
        } else {
            setOf()
        }

        val list = (0 until num)
                .map { getSqlInsertBuilder()!!.createSqlInsertionAction(name,chosenColumns, mutableListOf(),true, extraConstraints, enableSingleInsertionForTable=enableSingleInsertionForTable) }
                .toMutableList()

        if (log.isTraceEnabled) {
            log.trace("at createDbActions, {} insertions are added, and they are {}", list.size,
                list.flatten().joinToString(",") {
                    it.getResolvedName()
                })
        }

        SqlActionUtils.randomizeDbActionGenes(list.flatten(), randomness)
        //FIXME refactoring
        list.flatten().forEach { it.seeTopGenes().forEach { g -> g.markAllAsInitialized() } }
        //FIXME broken elements are not removed from list
        SqlActionUtils.repairBrokenDbActionsList(list.flatten().toMutableList(), randomness)
        return list
    }

    abstract fun getSqlInsertBuilder(): SqlInsertBuilder?

    override fun canApplyInitStructureMutator(): Boolean {
        return config.isEnabledInitializationStructureMutation() && getSqlInsertBuilder() != null
    }
}
