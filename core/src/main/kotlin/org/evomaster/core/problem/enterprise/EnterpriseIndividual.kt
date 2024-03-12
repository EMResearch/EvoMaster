package org.evomaster.core.problem.enterprise

import org.evomaster.core.Lazy
import org.evomaster.core.search.action.Action
import org.evomaster.core.search.action.ActionComponent
import org.evomaster.core.search.action.ActionFilter
import org.evomaster.core.sql.SqlAction
import org.evomaster.core.sql.SqlActionUtils
import org.evomaster.core.mongo.MongoDbAction
import org.evomaster.core.problem.api.ApiWsIndividual
import org.evomaster.core.problem.externalservice.ApiExternalServiceAction
import org.evomaster.core.problem.externalservice.HostnameResolutionAction
import org.evomaster.core.search.*
import org.evomaster.core.search.gene.utils.GeneUtils
import org.evomaster.core.search.service.Randomness
import org.evomaster.core.search.tracer.TrackOperator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*


/**
 * Top-level representation for a test case involving an enterprise application.
 * An enterprise application could be a single web service (eg REST,GraphQL and RPC),
 * a whole microservice architecture (eg, accessed via an API Gateway), or a backend for
 * a frontend application (eg web pages on browser or mobile app).
 *
 * Regardless of the enterprise type, a common need is the ability to initialize data into databases
 * (eg SQL and NoSQL) before the "actions" on the SUT are executed (eg API calls and browser clicks).
 *
 * Setting up mocks of external services (eg using WireMock when the SUT communicate with another API) is done
 * per action, and not here in the initialization phase.
 */
abstract class EnterpriseIndividual(
    val sampleType: SampleType,
    /**
     * a tracked operator to manipulate the individual (nullable)
     */
    trackOperator: TrackOperator? = null,
    /**
     * an index of individual indicating when the individual is initialized during the search
     * negative number means that such info is not collected
     */
    index: Int = -1,
    /**
     * a list of children of the individual
     */
    children: MutableList<out ActionComponent>,
    childTypeVerifier: EnterpriseChildTypeVerifier,
    /**
     * if no group definition is specified, then it is assumed that all action are for the MAIN group
     */
    groups : GroupsOfChildren<StructuralElement> = getEnterpriseTopGroups(children,children.size,0, 0, 0)
) : Individual(
    trackOperator,
    index,
    children,
    childTypeVerifier,
    groups
) {
    companion object{
        private val log : Logger = LoggerFactory.getLogger(ApiWsIndividual::class.java)

        /**
         * Return group definition for the given children.
         * The first [sizeSQL] are assumed to be database actions, followed by [sizeMain] main actions
         */
        fun getEnterpriseTopGroups(
            children: List<ActionComponent>,
            sizeMain: Int,
            sizeSQL: Int,
            sizeMongo: Int,
            sizeDNS: Int
        ) : GroupsOfChildren<StructuralElement>{

            if(children.size != sizeSQL +sizeMongo + sizeDNS + sizeMain){
                throw IllegalArgumentException("Group size mismatch. Expected a total of ${children.size}, but" +
                        " got main=$sizeMain,  sql=$sizeSQL, mongo=$sizeMongo, dns=$sizeDNS")
            }
            if(sizeSQL < 0){
                throw IllegalArgumentException("Negative size for sizeSQL: $sizeSQL")
            }
            if(sizeMongo < 0){
                throw IllegalArgumentException("Negative size for sizeMongo: $sizeMain")
            }
            if(sizeDNS < 0){
                throw IllegalArgumentException("Negative size for sizeDNS: $sizeMain")
            }
            if(sizeMain < 0){
                throw IllegalArgumentException("Negative size for sizeMain: $sizeMain")
            }

            /*
                TODO in future ll need to refactor to handle multiple databases, possibly handled with
                one action group per database...
                but is it really common that a SUT directly access several databases of same kind?
             */

            val startIndexSQL = children.indexOfFirst { a -> a is SqlAction }
            val endIndexSQL = children.indexOfLast { a -> a is SqlAction }
            val db = ChildGroup<StructuralElement>(GroupsOfChildren.INITIALIZATION_SQL,{e -> e is ActionComponent && e.flatten().all { a -> a is SqlAction }},
                if(sizeSQL==0) -1 else startIndexSQL , if(sizeSQL==0) -1 else endIndexSQL
            )

            val startIndexMongo = children.indexOfFirst { a -> a is MongoDbAction }
            val endIndexMongo = children.indexOfLast { a -> a is MongoDbAction }
            val mongodb = ChildGroup<StructuralElement>(GroupsOfChildren.INITIALIZATION_MONGO,{e -> e is ActionComponent && e.flatten().all { a -> a is MongoDbAction }},
                if(sizeMongo==0) -1 else startIndexMongo , if(sizeMongo==0) -1 else endIndexMongo
            )

            val startIndexDns = children.indexOfFirst { a -> a is HostnameResolutionAction }
            val endIndexDns = children.indexOfLast { a -> a is HostnameResolutionAction }
            val dns = ChildGroup<StructuralElement>(GroupsOfChildren.INITIALIZATION_DNS,{e -> e is ActionComponent && e.flatten().all { a -> a is HostnameResolutionAction }},
                if(sizeDNS==0) -1 else startIndexDns , if(sizeDNS==0) -1 else endIndexDns
            )

            val initSize = sizeSQL+sizeMongo+sizeDNS

            val main = ChildGroup<StructuralElement>(GroupsOfChildren.MAIN, {e -> e !is EnvironmentAction },
                if(sizeMain == 0) -1 else initSize, if(sizeMain == 0) -1 else initSize + sizeMain - 1)

            return GroupsOfChildren(children, listOf(db, mongodb, dns, main))
        }
    }

    /**
     * a list of db actions for its Initialization
     */
    private val sqlInitialization: List<SqlAction>
        get() {
            return groupsView()!!.getAllInGroup(GroupsOfChildren.INITIALIZATION_SQL)
                .flatMap { (it as ActionComponent).flatten() }
                .map { it as SqlAction }
        }


    /**
     * Make sure that no secondary type is used in the main actions, and that only [EnterpriseActionGroup]
     * are used.
     *
     * Ideally, a flattening should not impact fitness, but, in few cases, it might :(
     * This happens eg in Rest Resource, if a middle SQL action is moved into initialization group and impact
     * state of previous REST actions (an example of this did happen for example for CrossFkEMTest...).
     * This means that, after a flattening, to be on safe side should recompute fitness
     */
    fun ensureFlattenedStructure(){

        val before = seeAllActions().size

        doFlattenStructure()

        //make sure the flattening worked
        Lazy.assert { isFlattenedStructure() }
        //no base action should have been lost
        Lazy.assert { seeAllActions().size == before }
    }

    protected open fun doFlattenStructure(){
        //for most types, there is nothing to do.
        //can be overridden if needed
    }

    private fun isFlattenedStructure() : Boolean{
        return groupsView()!!.getAllInGroup(GroupsOfChildren.MAIN).all { it is EnterpriseActionGroup<*> }
    }


    final override fun seeActions(filter: ActionFilter) : List<Action>{
        return when (filter) {
            ActionFilter.ALL -> seeAllActions()
            ActionFilter.MAIN_EXECUTABLE -> groupsView()!!.getAllInGroup(GroupsOfChildren.MAIN)
                .flatMap { (it as ActionComponent).flatten() }
                .filter { it !is SqlAction && it !is ApiExternalServiceAction }
            ActionFilter.INIT ->
                groupsView()!!
                    .getAllInGroup(GroupsOfChildren.INITIALIZATION_SQL).flatMap { (it as ActionComponent).flatten() } + groupsView()!!
                    .getAllInGroup(GroupsOfChildren.INITIALIZATION_MONGO).flatMap { (it as ActionComponent).flatten()}+ groupsView()!!
                    .getAllInGroup(GroupsOfChildren.INITIALIZATION_DNS).flatMap { (it as ActionComponent).flatten()}
            // WARNING: this can still return DbAction, MongoDbAction and External ones...
            ActionFilter.NO_INIT -> groupsView()!!.getAllInGroup(GroupsOfChildren.MAIN).flatMap { (it as ActionComponent).flatten() }
            ActionFilter.ONLY_SQL -> seeAllActions().filterIsInstance<SqlAction>()
            ActionFilter.ONLY_MONGO -> seeAllActions().filterIsInstance<MongoDbAction>()
            ActionFilter.NO_SQL -> seeAllActions().filter { it !is SqlAction }
            ActionFilter.ONLY_DB -> seeAllActions().filter { it is SqlAction || it is MongoDbAction }
            ActionFilter.NO_DB -> seeAllActions().filter { it !is SqlAction && it !is MongoDbAction }
            ActionFilter.ONLY_EXTERNAL_SERVICE -> seeAllActions().filterIsInstance<ApiExternalServiceAction>()
            ActionFilter.NO_EXTERNAL_SERVICE -> seeAllActions().filter { it !is ApiExternalServiceAction }.filter { it !is HostnameResolutionAction }
            ActionFilter.ONLY_DNS -> groupsView()!!.getAllInGroup(GroupsOfChildren.INITIALIZATION_DNS).flatMap { (it as ActionComponent).flatten()}
        }
    }

    fun seeMainActionComponents() : List<ActionComponent>{
        return groupsView()!!.getAllInGroup(GroupsOfChildren.MAIN) as List<ActionComponent>
    }


    fun addMainActionInEmptyEnterpriseGroup(relativePosition: Int = -1, action: Action){
        val main = GroupsOfChildren.MAIN
        val g = EnterpriseActionGroup(mutableListOf(action), action.javaClass)

        if (relativePosition < 0) {
            addChildToGroup(g, main)
        } else{
            val base = groupsView()!!.startIndexForGroupInsertionInclusive(main)
            val position = base + relativePosition
            addChildToGroup(position, action, main)
        }
    }

    fun removeMainActionGroupAt(relativePosition: Int){
        val main = GroupsOfChildren.MAIN
        val base = groupsView()!!.startIndexForGroupInsertionInclusive(main)
        val position = base + relativePosition
        killChildByIndex(position)
    }

    override fun removeMainExecutableAction(relativeIndex: Int){
        removeMainActionGroupAt(relativeIndex)
    }

    /**
     * @return SQL all actions before relativeIndex (exclusive) in GroupsOfChildren.MAIN
     */
    fun seeSQLActionBeforeIndex(relativeIndex: Int): List<SqlAction>{
        val main = GroupsOfChildren.MAIN
        val base = groupsView()!!.startIndexForGroupInsertionInclusive(main)
        return (0 until relativeIndex).flatMap {
            (children[base + it] as ActionComponent).flatten()
        }.filterIsInstance<SqlAction>()
    }

    /**
     * return a list of all db actions in [this] individual
     * that include all initializing actions plus db actions among main actions.
     *
     * NOTE THAT if EMConfig.probOfApplySQLActionToCreateResources is 0.0, this method
     * would be same with [seeInitializingActions]
     */
    fun seeSqlDbActions() : List<SqlAction> = seeActions(ActionFilter.ONLY_SQL) as List<SqlAction>

    fun seeMongoDbActions() : List<MongoDbAction> = seeActions(ActionFilter.ONLY_MONGO) as List<MongoDbAction>

    /**
     * return a list of all external service actions in [this] individual
     * that include all the initializing actions among the main actions
     */
    fun seeExternalServiceActions() : List<ApiExternalServiceAction> = seeActions(ActionFilter.ONLY_EXTERNAL_SERVICE) as List<ApiExternalServiceAction>

    override fun verifyInitializationActions(): Boolean {
        return SqlActionUtils.verifyActions(seeInitializingActions().filterIsInstance<SqlAction>())
    }

    override fun repairInitializationActions(randomness: Randomness) {

        /*
            TODO this need updating / refactoring
         */

        /**
         * First repair SQL Genes (i.e. SQL Timestamps)
         */
        if (log.isTraceEnabled)
            log.trace("invoke GeneUtils.repairGenes")

        GeneUtils.repairGenes(this.seeGenes(GeneFilter.ONLY_SQL).flatMap { it.flatView() })

        /**
         * Now repair database constraints (primary keys, foreign keys, unique fields, etc.).
         *
         * Note: this is only for DB Actions in the initialization phase, and NOT if there is any
         * afterwards (eg in resource-based handling).
         */
        if (!verifyInitializationActions()) {
            if (log.isTraceEnabled)
                log.trace("invoke GeneUtils.repairBrokenDbActionsList")
            val previous = sqlInitialization.toMutableList()
            SqlActionUtils.repairBrokenDbActionsList(previous, randomness)
            resetInitializingActions(previous)
            Lazy.assert{verifyInitializationActions()}
        }
    }

    override fun hasAnyAction(): Boolean {
        return super.hasAnyAction() || sqlInitialization.isNotEmpty()
    }

    override fun size() = seeMainExecutableActions().size

    private fun getLastIndexOfDbActionToAdd(): Int =
        groupsView()!!.endIndexForGroupInsertionInclusive(GroupsOfChildren.INITIALIZATION_SQL)

    private fun getLastIndexOfMongoDbActionToAdd(): Int =
        groupsView()!!.endIndexForGroupInsertionInclusive(GroupsOfChildren.INITIALIZATION_MONGO)

    private fun getLastIndexOfHostnameResolutionActionToAdd(): Int =
        groupsView()!!.endIndexForGroupInsertionInclusive(GroupsOfChildren.INITIALIZATION_DNS)

    private fun getFirstIndexOfDbActionToAdd(): Int =
        groupsView()!!.startIndexForGroupInsertionInclusive(GroupsOfChildren.INITIALIZATION_SQL)

    private fun getFirstIndexOfMongoDbActionToAdd(): Int =
        groupsView()!!.startIndexForGroupInsertionInclusive(GroupsOfChildren.INITIALIZATION_MONGO)

    private fun getFirstIndexOfHostnameResolutionActionToAdd(): Int =
        groupsView()!!.startIndexForGroupInsertionInclusive(GroupsOfChildren.INITIALIZATION_DNS)

    /**
     * add [actions] at [relativePosition]
     * if [relativePosition] = -1, append the [actions] at the end
     */
    fun addInitializingDbActions(relativePosition: Int=-1, actions: List<Action>){
        if (relativePosition < 0)  {
            addChildrenToGroup(getLastIndexOfDbActionToAdd(), actions, GroupsOfChildren.INITIALIZATION_SQL)
        } else{
            addChildrenToGroup(getFirstIndexOfDbActionToAdd()+relativePosition, actions, GroupsOfChildren.INITIALIZATION_SQL)
        }
    }

    fun addInitializingMongoDbActions(relativePosition: Int=-1, actions: List<Action>){
        if (relativePosition < 0)  {
            addChildrenToGroup(getLastIndexOfMongoDbActionToAdd(), actions, GroupsOfChildren.INITIALIZATION_MONGO)
        } else{
            addChildrenToGroup(getFirstIndexOfMongoDbActionToAdd()+relativePosition, actions, GroupsOfChildren.INITIALIZATION_MONGO)
        }
    }

    fun addInitializingHostnameResolutionActions(relativePosition: Int=-1, actions: List<Action>) {
        if (relativePosition < 0) {
            addChildrenToGroup(getLastIndexOfHostnameResolutionActionToAdd(), actions, GroupsOfChildren.INITIALIZATION_DNS)
        } else {
            addChildrenToGroup(getFirstIndexOfHostnameResolutionActionToAdd()+relativePosition, actions, GroupsOfChildren.INITIALIZATION_DNS)
        }
    }

    private fun resetInitializingActions(actions: List<SqlAction>){
        killChildren { it is SqlAction }
        // TODO: Can be merged with DbAction later
        addChildrenToGroup(getLastIndexOfDbActionToAdd(), actions, GroupsOfChildren.INITIALIZATION_SQL)
    }

    /**
     * remove specified dbactions i.e., [actions] from [sqlInitialization]
     */
    fun removeInitDbActions(actions: List<SqlAction>) {
        killChildren { it is SqlAction && actions.contains(it)}
    }

    /***
     * remove specified list of [HostnameResolutionAction] from the initializing actions.
     */
    fun removeHostnameResolutionAction(actions: List<HostnameResolutionAction>) {
        killChildren {  it is HostnameResolutionAction && actions.contains(it) }
    }

    /**
     * @return a list table names which are used to insert data directly
     */
    open fun getInsertTableNames(): List<String>{
        return sqlInitialization.filterNot { it.representExistingData }.map { it.table.name }
    }
}
