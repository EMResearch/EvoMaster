package org.evomaster.core.problem.rest.service

import com.google.inject.Inject
import org.evomaster.client.java.controller.api.dto.database.operations.DataRowDto
import org.evomaster.core.EMConfig
import org.evomaster.core.database.DbAction
import org.evomaster.core.database.DbActionUtils
import org.evomaster.core.database.SqlInsertBuilder
import org.evomaster.core.database.schema.Table
import org.evomaster.core.problem.rest.*
import org.evomaster.core.problem.rest.auth.AuthenticationInfo
import org.evomaster.core.problem.rest.resource.*
import org.evomaster.core.problem.rest.resource.dependency.CreationChain
import org.evomaster.core.problem.rest.resource.dependency.PostCreationChain
import org.evomaster.core.problem.rest.util.RestResourceTemplateHandler
import org.evomaster.core.search.Action
import org.evomaster.core.search.gene.GeneUtils
import org.evomaster.core.search.gene.ImmutableDataHolderGene
import org.evomaster.core.search.gene.sql.SqlForeignKeyGene
import org.evomaster.core.search.gene.sql.SqlPrimaryKeyGene
import org.evomaster.core.search.service.AdaptiveParameterControl
import org.evomaster.core.search.service.Randomness
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * the class is used to manage all resources
 */
class ResourceManageService {

    companion object {
        val log: Logger = LoggerFactory.getLogger(ResourceManageService::class.java)
        private const val PROB_EXTRA_PATCH = 0.8
    }

    @Inject
    private lateinit var apc: AdaptiveParameterControl

    @Inject
    private lateinit var randomness: Randomness

    @Inject
    private lateinit var config: EMConfig

    @Inject
    private lateinit var dm : ResourceDepManageService

    /**
     * key is resource path
     * value is an abstract resource
     */
    private val resourceCluster : MutableMap<String, RestResourceNode> = mutableMapOf()

    /**
     * key is table name
     * value is a list of existing data of PKs in DB
     */
    private val dataInDB : MutableMap<String, MutableList<DataRowDto>> = mutableMapOf()

    /**
     * key is table name
     * value is the table
     */
    private val tables : MutableMap<String, Table> = mutableMapOf()

    private var sqlInsertBuilder : SqlInsertBuilder? = null

    /**
     * init resource nodes based on [actionCluster] and [sqlInsertBuilder]
     */
    fun initResourceNodes(actionCluster : MutableMap<String, Action>, sqlInsertBuilder: SqlInsertBuilder? = null) {
        if (resourceCluster.isNotEmpty()) return
        if (this.sqlInsertBuilder!= null) return

        this.sqlInsertBuilder = sqlInsertBuilder

        if(config.extractSqlExecutionInfo) sqlInsertBuilder?.extractExistingTables(tables)

        actionCluster.values.forEach { u ->
            if (u is RestCallAction) {
                val resource = resourceCluster.getOrPut(u.path.toString()) {
                    RestResourceNode(
                            u.path.copy(),
                            initMode =
                                if(config.probOfEnablingResourceDependencyHeuristics > 0.0 && config.doesApplyNameMatching)
                                    InitMode.WITH_DERIVED_DEPENDENCY
                                else if(config.doesApplyNameMatching) InitMode.WITH_TOKEN
                                else if (config.probOfEnablingResourceDependencyHeuristics > 0.0) InitMode.WITH_DEPENDENCY
                                else InitMode.NONE)
                }
                resource.actions.add(u)
            }
        }
        resourceCluster.values.forEach{it.initAncestors(getResourceCluster().values.toList())}

        resourceCluster.values.forEach{it.init()}

        if(config.extractSqlExecutionInfo && config.doesApplyNameMatching){
            dm.initRelatedTables(resourceCluster.values.toMutableList(), getTableInfo())

            if(config.probOfEnablingResourceDependencyHeuristics > 0.0)
                dm.initDependencyBasedOnDerivedTables(resourceCluster.values.toList(), getTableInfo())
        }
        if(config.doesApplyNameMatching && config.probOfEnablingResourceDependencyHeuristics > 0.0)
            dm.deriveDependencyBasedOnSchema(resourceCluster.values.toList())
    }


    /**
     * this function is used to initialized ad-hoc individuals for resource-based individual
     */
    fun createAdHocIndividuals(auth: AuthenticationInfo, adHocInitialIndividuals : MutableList<RestIndividual>){
        val sortedResources = resourceCluster.values.sortedByDescending { it.getTokenMap().size }.asSequence()

        //GET, PATCH, DELETE
        sortedResources.forEach { ar->
            ar.actions.filter { it is RestCallAction && it.verb != HttpVerb.POST && it.verb != HttpVerb.PUT }.forEach {a->
                val call = sampleOneAction(ar, a.copy() as RestAction)
                call.restActions.forEach { a->
                    if(a is RestCallAction) a.auth = auth
                }
                adHocInitialIndividuals.add(createAdHocSmartResource(mutableListOf(call)))
            }
        }

        //all POST with one post action
        sortedResources.forEach { ar->
            ar.actions.filter { it is RestCallAction && it.verb == HttpVerb.POST}.forEach { a->
                val call = sampleOneAction(ar, a.copy() as RestAction)
                call.restActions.forEach { (it as RestCallAction).auth = auth }
                adHocInitialIndividuals.add(createAdHocSmartResource(mutableListOf(call)))
            }
        }

        sortedResources
                .filter { it.actions.find { a -> a is RestCallAction && a.verb == HttpVerb.POST } != null && it.getPostChain()?.actions?.run { this.size > 1 }?:false  }
                .forEach { ar->
                    genPostChain(ar, config.maxTestSize)?.let {call->
                        call.restActions.forEach { (it as RestCallAction).auth = auth }
                        adHocInitialIndividuals.add(createAdHocSmartResource(mutableListOf(call)))
                    }
                }

        //PUT
        sortedResources.forEach { ar->
            ar.actions.filter { it is RestCallAction && it.verb == HttpVerb.PUT }.forEach {a->
                val call = sampleOneAction(ar, a.copy() as RestAction)
                call.restActions.forEach { (it as RestCallAction).auth = auth }
                adHocInitialIndividuals.add(createAdHocSmartResource(mutableListOf(call)))
            }
        }

        //template
        sortedResources.forEach { ar->
            ar.getTemplates().values.filter { t-> RestResourceTemplateHandler.isNotSingleAction(t.template) }
                    .forEach {ct->
                        val call = genCalls(ar, ct.template, config.maxTestSize, isAdHocInit = true)
                        call.restActions.forEach { if(it is RestCallAction) it.auth = auth }
                        adHocInitialIndividuals.add(createAdHocSmartResource(mutableListOf(call)))
                    }
        }

        if (config.isEnabledResourceWithSQL()){
            //SQL-action
            sortedResources.forEach { ar->
                if (ar.getSqlCreationPoints().isNotEmpty()){
                    ar.actions.forEach { action->
                        if (action is RestCallAction){
                            val call = genCalls(ar, action.verb.toString(), config.maxTestSize, forceInsert = true, createResource = true)
                            call.restActions.forEach { if(it is RestCallAction) it.auth = auth }
                            adHocInitialIndividuals.add(createAdHocSmartResource(mutableListOf(call)))
                        }
                    }
                }
            }
        }
    }

    private fun createAdHocSmartResource(calls : MutableList<RestResourceCalls>) : RestIndividual{
        val ind = RestIndividual(calls, SampleType.SMART_RESOURCE)
        if (!DbActionUtils.verifyActions(ind.seeDbAction())){
            ind.repairDBActions(sqlInsertBuilder, randomness)
        }
        return ind
    }

    /**
     * handle to generate an resource call for addition in [ind]
     * @return the generated resource call
     */
    fun handleAddResource(ind : RestIndividual, maxTestSize : Int) : RestResourceCalls {
        val existingRs = ind.getResourceCalls().map { it.getResourceNodeKey() }
        val candidate = randomness.choose(getResourceCluster().filterNot { r-> existingRs.contains(r.key) }.keys)
        return sampleAnyRestResourceCalls(resourceCluster[candidate]!!,maxTestSize)
    }

    /************************** sampling *********************************/

    /**
     * modify the template, but keep the same values
     */
    fun generateAnother(node: RestResourceNode, calls : RestResourceCalls, maxTestSize: Int) : RestResourceCalls?{
        val current = calls.template?.template?:RestResourceTemplateHandler.getStringTemplateByActions(calls.restActions.filterIsInstance<RestCallAction>())
        val rest = node.getTemplates().filter { it.value.template != current}
        if(rest.isEmpty()) return null
        val selected = randomness.choose(rest.keys)
        val call = genCalls(node, selected, maxTestSize)
        call.bindRestActionsWith(calls)
        call.repairGenesAfterMutation()
        return call
    }

    fun randomRestResourceCalls(node: RestResourceNode, maxTestSize: Int): RestResourceCalls{
        val randomTemplates = node.getTemplates().filter { e->
            e.value.size in 1..maxTestSize
        }.map { it.key }
        if(randomTemplates.isEmpty()) return sampleOneAction(node)
        return genCalls(node, randomness.choose(randomTemplates),  maxTestSize)
    }

    private fun sampleIndResourceCall(node: RestResourceNode, maxTestSize: Int) : RestResourceCalls{
        node.selectTemplate({ call : CallsTemplate -> call.independent || (call.template == HttpVerb.POST.toString() && call.size > 1)}, randomness)?.let {
            return genCalls(node, it.template, maxTestSize, false)
        }
        return genCalls(node, HttpVerb.POST.toString(), maxTestSize)
    }


    fun sampleOneAction(node: RestResourceNode, verb : HttpVerb? = null) : RestResourceCalls{
        val al = node.sampleOneAvailableAction(verb, randomness)
        return sampleOneAction(node, al)
    }

    private fun sampleOneAction(node: RestResourceNode, action : RestAction) : RestResourceCalls{
        val copy = action.copy()
        RestActionHandlingUtil.randomizeActionGenes(copy as RestCallAction, randomness, node)

        val sampled = copy.verb.toString()
        val template = node.getTemplates()[sampled]
            ?: throw IllegalArgumentException("${copy.verb} is not one of templates of ${node.path}")
        val call =  RestResourceCalls(sampled, template, RestResourceInstance(node, copy.parameters), mutableListOf(copy))

        if(action is RestCallAction && action.verb == HttpVerb.POST){
            node.getCreation { c : CreationChain -> (c is PostCreationChain) }.let {
                if(it != null && (it as PostCreationChain).actions.size == 1 && it.isComplete()){
                    call.status = ResourceStatus.CREATED_REST
                }else{
                    call.status = ResourceStatus.NOT_FOUND_DEPENDENT
                }
            }
        }else
            call.status = ResourceStatus.NOT_EXISTING

        return call
    }

    /**
     * sample a resource call without a specific template
     * @param key a key of resource
     * @param maxTestSize a maximum size of rest actions in this resource calls
     * @param prioriIndependent specify whether to prioritize independent templates
     * @param prioriDependent specify whether to prioritize dependent templates
     */
    fun sampleAnyRestResourceCalls(key: String, maxTestSize: Int, prioriIndependent : Boolean = false, prioriDependent : Boolean = false): RestResourceCalls{
        val node = getResourceNodeFromCluster(key)
        return sampleAnyRestResourceCalls(node, maxTestSize, prioriIndependent, prioriDependent)
    }

    private fun sampleAnyRestResourceCalls(
        node: RestResourceNode, maxTestSize: Int, prioriIndependent : Boolean = false, prioriDependent : Boolean = false): RestResourceCalls {
        if (maxTestSize < 1 && prioriDependent == prioriIndependent && prioriDependent){
            throw IllegalArgumentException("unaccepted args")
        }
        val fchosen = node.getTemplates().filter { it.value.size <= maxTestSize }
        if(fchosen.isEmpty())
            return sampleOneAction(node)
        val chosen =
            if (prioriDependent)  fchosen.filter { !it.value.independent }
            else if (prioriIndependent) fchosen.filter { it.value.independent }
            else fchosen
        if (chosen.isEmpty())
            return genCalls(node, randomness.choose(fchosen).template, maxTestSize)
        return genCalls(node, randomness.choose(chosen).template,maxTestSize)
    }


    private fun sampleRestResourceCalls(node: RestResourceNode, template: String, maxTestSize: Int) : RestResourceCalls{
        assert(maxTestSize > 0)
        return genCalls(node, template, maxTestSize)
    }

    private fun genPostChain(node: RestResourceNode, maxTestSize: Int) : RestResourceCalls?{
        val template = node.getTemplates()["POST"]?:
        return null

        return genCalls(node, template.template, maxTestSize)
    }

    /**
     * generate ResourceCall for [node]
     * @param node the resource
     * @param template specify a template for the resource calls
     * @param maxTestSize is a maximum number of rest actions in this resource calls
     * @param checkSize specify whether to check the size constraint
     * @param createResource specify whether to force to prepare the resource for the calls
     * @param additionalPatch specify whether to add additional patch
     *
     * TODO update postCreation accordingly
     */
    fun genCalls(
        node: RestResourceNode,
        template : String,
        maxTestSize : Int = 1,
        createResource : Boolean = true,
        additionalPatch : Boolean = true,
        forceInsert : Boolean = false,
        isAdHocInit : Boolean = false
    ) : RestResourceCalls{

        if(!node.getTemplates().containsKey(template))
            throw IllegalArgumentException("$template does not exist in ${node.path}")

        val ats = RestResourceTemplateHandler.parseTemplate(template)
        val callsTemplate = node.getTemplates().getValue(template)

        val call = generateRestActionsForCalls(node, ats, template,callsTemplate, maxTestSize, createResource, additionalPatch)

        val sqlDueToPostFailure = doApplySQLForFailurePost(node, call)
        val withSql = config.isEnabledResourceWithSQL() && ((createResource && ats[0] != HttpVerb.POST) || forceInsert || sqlDueToPostFailure)

        if (isAdHocInit || !withSql)
            return call


        if (forceInsert && !hasDBHandler())
            throw IllegalStateException("force to employ SQL for resource creation, but there is no db")

        val created = handleDbActionForCall(call, forceInsert, false)
        if(created){
            call.status = ResourceStatus.CREATED_SQL
        }

        return call
    }

    private fun doApplySQLForFailurePost(node : RestResourceNode,call: RestResourceCalls) : Boolean{
        return config.isEnabledResourceWithSQL() && hasDBHandler() && call.status != ResourceStatus.CREATED_REST
                && node.getSqlCreationPoints().isNotEmpty() && randomness.nextBoolean(0.8)
    }

    private fun generateRestActionsForCalls(
        node: RestResourceNode,
        ats : Array<HttpVerb>,
        sampledTemplate : String,
        callsTemplate: CallsTemplate,
        maxTestSize : Int = 1,
        additionalPatch : Boolean = true,
        checkPostChain : Boolean = true
    ) : RestResourceCalls{

        //val ats = RestResourceTemplateHandler.parseTemplate(template)

        val result : MutableList<RestAction> = mutableListOf()
        var resource : RestResourceInstance? = null

        val skipBind : MutableList<RestAction> = mutableListOf()

        var isCreated = ResourceStatus.NOT_EXISTING

        if(ats[0] == HttpVerb.POST){
            val nonPostIndex = ats.indexOfFirst { it != HttpVerb.POST }
            val ac = node.getActionByHttpVerb( if(nonPostIndex==-1) HttpVerb.POST else ats[nonPostIndex])!!.copy() as RestCallAction
            RestActionHandlingUtil.randomizeActionGenes(ac, randomness, node)
            result.add(ac)

            isCreated = RestActionHandlingUtil.
                createResourcesFor(randomness, null, maxTestSize, ac, result , resourceCluster, node)

            if(checkPostChain && !callsTemplate.sizeAssured){
                node.getPostChain()?:throw IllegalStateException("fail to init post creation")
                val pair = node.checkDifferenceOrInit(postactions = (if(ac.verb == HttpVerb.POST) result else result.subList(0, result.size - 1)).map { (it as RestCallAction).copy() as RestCallAction}.toMutableList())
                if (!pair.first) {
                    RestResourceNode.log.warn("the post action are not matched with initialized post creation.")
                }
                else {
                    node.updateTemplateSize()
                }
            }

            val lastPost = result.last()
            resource = RestResourceInstance(node, (lastPost as RestCallAction).parameters)
            // if id info is not in the path, we need to further bind with fields
            skipBind.add(lastPost)
            //skipBind.addAll(result)
            if(nonPostIndex == -1){
                (1 until ats.size).forEach{ _ ->
                    result.add(lastPost.copy().also {
                        skipBind.add(it as RestAction)
                    } as RestAction)
                }
            }else{
                if(nonPostIndex != ats.size -1){
                    (nonPostIndex + 1 until ats.size).forEach {
                        val action = node.getActionByHttpVerb(ats[it])!!.copy() as RestCallAction
                        RestActionHandlingUtil.randomizeActionGenes(action, randomness, node)
                        result.add(action)
                    }
                }
            }

        }else{
            ats.forEach {at->
                val ac = (node.getActionByHttpVerb(at)?:throw IllegalArgumentException("cannot find $at verb in ${
                    node.actions.joinToString(
                        ","
                    ) { a -> a.getName() }
                }")).copy() as RestCallAction
                RestActionHandlingUtil.randomizeActionGenes(ac, randomness, node)
                result.add(ac)
            }

            if(resource == null)
                resource = node.createResourceInstance(result, randomness, skipBind)
            if(checkPostChain){
                callsTemplate.sizeAssured = (result.size  == callsTemplate.size)
            }
        }

        if(result.size > 1)
            result.filterNot { ac -> skipBind.contains(ac) }.forEach { ac ->
                if((ac as RestCallAction).parameters.isNotEmpty()){
                    ac.bindToSamePathResolution(ac.path, resource.params)
                }
            }

        assert(result.isNotEmpty())

        if(additionalPatch
            && randomness.nextBoolean(PROB_EXTRA_PATCH)
            &&!callsTemplate.independent
            && callsTemplate.template.contains(HttpVerb.PATCH.toString()) && result.size + 1 <= maxTestSize){

            val index = result.indexOfFirst { (it is RestCallAction) && it.verb == HttpVerb.PATCH }
            val copy = result.get(index).copy() as RestAction
            result.add(index, copy)
        }
        val calls = RestResourceCalls(sampledTemplate, callsTemplate, resource, result)

        calls.status = isCreated

        return calls
    }

    /**
     * sample an resource call which refers to [resourceKey]
     * @param resourceKey a key refers to an resource node
     * @param doesCreateResource whether to prepare an resource for the call
     * @param calls existing calls
     * @param forceInsert force to use insertion to prepare the resource, otherwise prior to use POST
     * @param bindWith the sampled resource call requires to bind values according to [bindWith]
     */
    fun sampleCall(resourceKey: String,
                   doesCreateResource: Boolean,
                   calls : MutableList<RestResourceCalls>,
                   size : Int, forceInsert: Boolean = false, bindWith : MutableList<RestResourceCalls>? = null){
        val ar = resourceCluster[resourceKey]
                ?: throw IllegalArgumentException("resource path $resourceKey does not exist!")

        var insertSql = forceInsert
        val candidateTemplate = if(!doesCreateResource || ar.allTemplatesAreIndependent()) randomness.choose(ar.getTemplates().keys)
                                else if (hasDBHandler() && ar.getSqlCreationPoints().isNotEmpty() && config.isEnabledResourceWithSQL() && randomness.nextBoolean(config.probOfApplySQLActionToCreateResources)){
                                    randomness.choose(ar.getTemplates().filter { it.value.isSingleAction() }.keys).also {
                                        insertSql = insertSql || true
                                    }
                                }else{
                                    randomness.choose(ar.getTemplates().filterNot { it.value.independent }.keys)
                                }

        if(!doesCreateResource && ar.getTemplate(candidateTemplate).independent){
            val call = sampleIndResourceCall(ar,size)
            calls.add(call)
            /*
                bind GET with existing data
             */
            if(hasDBHandler() && call.template?.template == HttpVerb.GET.toString() && randomness.nextBoolean(config.probOfApplySQLActionToCreateResources)){
                handleDbActionForCall( call, false, true)
            }
            return
        }

        val call = genCalls(ar, candidateTemplate, size,true, true, forceInsert = insertSql)
        calls.add(call)

        if(bindWith != null){
            dm.bindCallWithFront(call, bindWith)
        }
    }


    private fun generateDbActionForCall(forceInsert: Boolean, forceSelect: Boolean, dbActions: MutableList<DbAction>, relatedTables : List<String>) : Boolean{
        var failToGenDB = false

        relatedTables.forEach { tableName->
            var added = false
            val select = forceSelect || randomness.nextBoolean(config.probOfSelectFromDatabase)
            if (!forceInsert && select){
                if(getDataInDb(tableName) != null && getDataInDb(tableName)!!.isNotEmpty()){
                    added = generateSelectSql(tableName, dbActions)
                }
            }

            if (!added){
                added = generateInsertSql(tableName, dbActions)
            }
            failToGenDB = failToGenDB || !added
        }

        return failToGenDB
    }

    /**
     * regarding resource call handling, if there exist two resource calls in an individual
     * e.g., A and B. besides, A and B are related to two tables, i.e., TA and TB respectively.
     * during search, if there exist a possible dependency, e.g., B-> A, and we prepare resource with SQL.
     * For resource B, with SQL, to handle fk, we typically generate two SQL, one is for TB and other is for TA.
     * In this case, since the TA has been generated by A, we do not need to generate extra TA in TB handing.
     * So we shrink such SQL actions with this method.
     */
    private fun shrinkDbActions(dbActions: MutableList<DbAction>){
        val removedDbAction = mutableListOf<DbAction>()

        dbActions.forEachIndexed { index, dbAction ->
            if((0 until index).any { i -> dbActions[i].table.name == dbAction.table.name &&!dbActions[i].representExistingData })
                removedDbAction.add(dbAction)
        }

        if(removedDbAction.isNotEmpty()){
            dbActions.removeAll(removedDbAction)

            val previous = mutableListOf<DbAction>()
            val created = mutableListOf<DbAction>()

            dbActions.forEachIndexed { index, dbAction ->
                if(index != 0 && dbAction.table.foreignKeys.isNotEmpty() && dbAction.table.foreignKeys.find { fk -> removedDbAction.find { it.table.name == fk.targetTable } !=null } != null)
                    DbActionUtils.repairFK(dbAction, previous, created, getSqlBuilder(), randomness)
                previous.add(dbAction)
            }

            dbActions.addAll(0, created)
        }
    }


    private fun handleDbActionForCall(call: RestResourceCalls, forceInsert: Boolean, forceSelect: Boolean) : Boolean{

        val paramToTables = RestActionHandlingUtil.inference.generateRelatedTables(call, mutableListOf())
        if(paramToTables.isEmpty()) return false

        val relatedTables = paramToTables.values.flatMap { it.map { g->g.tableName } }.toSet()
        val reducedRelatedTables = removeDuplicatedTables(relatedTables)

        val dbActions = mutableListOf<DbAction>()
        var failToGenDb = generateDbActionForCall( forceInsert = forceInsert, forceSelect = forceSelect, dbActions = dbActions, relatedTables = reducedRelatedTables.toList())

        if (!verifyRelatedDbActions(dbActions, relatedTables)){
            log.debug("here")
            dbActions.clear()
            failToGenDb = generateDbActionForCall( forceInsert = forceInsert, forceSelect = forceSelect, dbActions = dbActions, relatedTables = relatedTables.toList())
        }

        if(failToGenDb) return false

        //temporal
        val db = dbActions.map { it }.toMutableList()

        if(dbActions.isNotEmpty()){
            val removed = repairDbActionsForResource(dbActions)
            if(!verifyRelatedDbActions(dbActions, relatedTables)){
                log.debug("here")
                repairDbActionsForResource(db)
            }
            /*
             Note that since we prepare data for rest actions, we bind values of dbaction based on rest actions.
             */
            RestActionHandlingUtil.bindCallWithDBAction(call,dbActions, paramToTables, dbRemovedDueToRepair = removed)

            call.dbActions.addAll(dbActions)
        }
        return paramToTables.isNotEmpty() && !failToGenDb
    }

    private fun verifyRelatedDbActions(dbActions: MutableList<DbAction>, tables: Set<String>) : Boolean{

        val missing = tables.filter { t-> dbActions.none { d-> d.table.name.equals(t, ignoreCase = true) } }
        if (missing.isNotEmpty())
            log.warn("missing rows of tables {} to be created.", missing.joinToString(",") { it })
        return missing.isEmpty()
    }


    /**
     *  repair dbaction of resource call after standard mutation
     *  Since standard mutation does not change structure of a test, the involved tables
     *  should be same with previous.
     */
    fun repairRestResourceCalls(call: RestResourceCalls) {
        call.repairGenesAfterMutation()
    }
    /*********************************** database ***********************************/

    private fun selectToDataRowDto(dbAction : DbAction, tableName : String) : DataRowDto{
        dbAction.seeGenes().forEach { assert((it is SqlPrimaryKeyGene || it is ImmutableDataHolderGene || it is SqlForeignKeyGene)) }
        val set = dbAction.seeGenes().filterIsInstance<SqlPrimaryKeyGene>().map { ((it as SqlPrimaryKeyGene).gene as ImmutableDataHolderGene).value }.toSet()
        return randomness.choose(getDataInDb(tableName)!!.filter { it.columnData.toSet() == set })
    }

    // SQL can be used to create a resource only if sqlInsertBuilder is not null and config.probOfApplySQLActionToCreateResources > 0.0
    private fun hasDBHandler() : Boolean = sqlInsertBuilder!=null && (config.probOfApplySQLActionToCreateResources > 0.0)

    private fun snapshotDB(){
        if(hasDBHandler()){
            sqlInsertBuilder!!.extractExistingPKs(dataInDB)
        }
    }

    fun repairDbActionsForResource(dbActions: MutableList<DbAction>) : Boolean{
        /**
         * First repair SQL Genes (i.e. SQL Timestamps)
         */
        GeneUtils.repairGenes(dbActions.flatMap { it.seeGenes() })
        return DbActionUtils.repairBrokenDbActionsList(dbActions, randomness)
    }


    private fun generateSelectSql(tableName : String, dbActions: MutableList<DbAction>, forceDifferent: Boolean = false, withDbAction: DbAction?=null) : Boolean{
        if(dbActions.map { it.table.name }.contains(tableName)) return true

        assert(getDataInDb(tableName) != null && getDataInDb(tableName)!!.isNotEmpty())
        assert(!forceDifferent || withDbAction == null)

        val columns = if(forceDifferent && withDbAction!!.representExistingData){
            selectToDataRowDto(withDbAction, tableName)
        }else {
            randomness.choose(getDataInDb(tableName)!!)
        }

        val selectDbAction = sqlInsertBuilder!!.extractExistingByCols(tableName, columns)
        selectDbAction?:return false

        dbActions.add(selectDbAction)
        return true
    }

    private fun generateInsertSql(tableName : String, dbActions: MutableList<DbAction>) : Boolean{
        val insertDbAction =
                sqlInsertBuilder!!
                        .createSqlInsertionAction(tableName, forceAll = true)

        if (log.isTraceEnabled){
            log.trace("at generateInserSql, {} insertions are added, and they are {}", insertDbAction.size,
                insertDbAction.joinToString(",") {
                    if (it is DbAction) it.getResolvedName() else it.getName()
                })
        }

        DbActionUtils.randomizeDbActionGenes(insertDbAction, randomness)

        DbActionUtils.repairFkForInsertions(insertDbAction)

        if (insertDbAction.isEmpty()) return false
        dbActions.addAll(insertDbAction)

        return true
    }

    /*********************************** utility ***********************************/

    fun getResourceCluster()  = resourceCluster.toMap()

    fun getResourceNodeFromCluster(key : String) : RestResourceNode = resourceCluster[key]?: throw IllegalArgumentException("cannot find the resource with a key $key")

    fun getTableInfo() = tables.toMap()

    fun getSqlBuilder() : SqlInsertBuilder?{
        //if(!hasDBHandler()) return null
        return sqlInsertBuilder
    }

    private fun notEmptyDb() = getSqlBuilder() != null && getSqlBuilder()!!.anyTable()

    private fun getDataInDb(tableName: String) : MutableList<DataRowDto>?{
        if (dataInDB.isEmpty()) snapshotDB()
        val found = dataInDB.filterKeys { k-> k.equals(tableName, ignoreCase = true) }.keys
        if (found.isEmpty()) return null
        assert(found.size == 1)
        return dataInDB.getValue(found.first())
    }

    fun getTableByName(name : String) = tables.keys.find { it.equals(name, ignoreCase = true) }?.run { tables[this] }


    /**
     * @return sorted [tables] based on their relationships
     * for instance, Table A refer to Table B, then in the returned list, A should be before B.
     */
    fun sortTableBasedOnFK(tables : Set<String>) : List<Table>{
        return tables.mapNotNull { getTableByName(it) }.sortedWith(
            Comparator { o1, o2 ->
                when {
                    o1.foreignKeys.any { t-> t.targetTable.equals(o2.name,ignoreCase = true) } -> 1
                    o2.foreignKeys.any { t-> t.targetTable.equals(o1.name,ignoreCase = true) } -> -1
                    else -> 0
                }
            }
        )
    }

    private fun removeDuplicatedTables(tables: Set<String>) : List<String>{
        val tableInfo = tables.map { getTableByName(it) }
        val removed = tables.filter {table->
            tableInfo.any {t->
                t?.foreignKeys?.any { f-> f.targetTable.equals(table, ignoreCase = true) } == true
            }
        }
        val modified = tables.toMutableSet()
        modified.removeAll(removed)
        return modified.toList()
    }

    fun getNumSQLResource() : Int{
        return apc.getExploratoryValue(config.maxSqlInitActionsPerResource, 1)
    }
}