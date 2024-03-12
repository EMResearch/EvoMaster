package org.evomaster.core.problem.rest

import org.evomaster.core.search.action.Action
import org.evomaster.core.search.action.ActionComponent
import org.evomaster.core.search.action.ActionFilter
import org.evomaster.core.sql.SqlAction
import org.evomaster.core.sql.SqlActionUtils
import org.evomaster.core.mongo.MongoDbAction
import org.evomaster.core.problem.api.ApiWsIndividual
import org.evomaster.core.problem.enterprise.EnterpriseActionGroup
import org.evomaster.core.problem.enterprise.EnterpriseChildTypeVerifier
import org.evomaster.core.problem.enterprise.SampleType
import org.evomaster.core.problem.externalservice.ApiExternalServiceAction
import org.evomaster.core.problem.externalservice.HostnameResolutionAction
import org.evomaster.core.problem.rest.resource.RestResourceCalls
import org.evomaster.core.problem.rest.resource.SamplerSpecification
import org.evomaster.core.search.*
import org.evomaster.core.search.action.ActionFilter.*
import org.evomaster.core.search.gene.*
import org.evomaster.core.search.tracer.Traceable
import org.evomaster.core.search.tracer.TraceableElementCopyFilter
import org.evomaster.core.search.tracer.TrackOperator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.math.max

/**
 *
 * @property trackOperator indicates which operator create this individual.
 * @property index indicates when the individual is created, ie, using num of evaluated individual.
 */
class RestIndividual(
    sampleType: SampleType,
    val sampleSpec: SamplerSpecification? = null,
    trackOperator: TrackOperator? = null,
    index : Int = -1,
    allActions : MutableList<out ActionComponent>,
    mainSize : Int = allActions.size,
    sqlSize: Int = 0,
    mongoSize: Int = 0,
    dnsSize: Int = 0,
    groups : GroupsOfChildren<StructuralElement> = getEnterpriseTopGroups(allActions,mainSize,sqlSize,mongoSize,dnsSize)
): ApiWsIndividual(sampleType, trackOperator, index, allActions,
    childTypeVerifier = EnterpriseChildTypeVerifier(RestCallAction::class.java,RestResourceCalls::class.java),
    groups) {

    companion object{
        private val log: Logger = LoggerFactory.getLogger(RestIndividual::class.java)
    }

    constructor(
        resourceCalls: MutableList<RestResourceCalls>,
        sampleType: SampleType,
        sampleSpec: SamplerSpecification? = null,
        dbInitialization: MutableList<SqlAction> = mutableListOf(),
        trackOperator: TrackOperator? = null,
        index : Int = -1
    ) : this(sampleType, sampleSpec, trackOperator, index, mutableListOf<ActionComponent>().apply {
        addAll(dbInitialization); addAll(resourceCalls)
    }, resourceCalls.size, dbInitialization.size)

    constructor(
        actions: MutableList<out Action>,
        sampleType: SampleType,
        dbInitialization: MutableList<SqlAction> = mutableListOf(),
        trackOperator: TrackOperator? = null,
        index : Int = Traceable.DEFAULT_INDEX
    ) : this(
                    actions.map {RestResourceCalls(actions= listOf(it as RestCallAction), sqlActions = listOf())}.toMutableList(),
                    sampleType,
                    null,
                    dbInitialization,
                    trackOperator,
                    index
            )



    override fun copyContent(): Individual {
        return RestIndividual(
                sampleType,
                sampleSpec?.copy(),
                trackOperator,
                index,
                children.map { it.copy() }.toMutableList() as MutableList<out ActionComponent>,
                mainSize = groupsView()!!.sizeOfGroup(GroupsOfChildren.MAIN),
                sqlSize = groupsView()!!.sizeOfGroup(GroupsOfChildren.INITIALIZATION_SQL),
                mongoSize = groupsView()!!.sizeOfGroup(GroupsOfChildren.INITIALIZATION_MONGO),
                dnsSize = groupsView()!!.sizeOfGroup(GroupsOfChildren.INITIALIZATION_DNS)
        )
    }


    override fun canMutateStructure(): Boolean {
        return sampleType == SampleType.RANDOM ||
                sampleType == SampleType.REST_SMART_GET_COLLECTION ||
                sampleType == SampleType.SMART_RESOURCE
    }

    /**
     * Note that if resource-mio is enabled, [dbInitialization] of a RestIndividual is always empty, since DbActions are created
     * for initializing an resource for a set of actions on the same resource.
     * TODO is this no longer the case?
     *
     * This effects on a configuration with respect to  [EMConfig.geneMutationStrategy] is ONE_OVER_N when resource-mio is enabled.
     *
     * In another word, if resource-mio is enabled, whatever [EMConfig.geneMutationStrategy] is, it always follows "GeneMutationStrategy.ONE_OVER_N_BIASED_SQL"
     * strategy.
     *
     */
    override fun seeGenes(filter: GeneFilter): List<out Gene> {

        return when (filter) {
            GeneFilter.ALL -> seeAllActions().flatMap(Action::seeTopGenes)
            GeneFilter.NO_SQL -> seeActions(ActionFilter.NO_SQL).flatMap(Action::seeTopGenes)
            GeneFilter.ONLY_SQL -> seeSqlDbActions().flatMap(SqlAction::seeTopGenes)
            GeneFilter.ONLY_MONGO -> seeMongoDbActions().flatMap(MongoDbAction::seeTopGenes)
            GeneFilter.ONLY_DB -> seeActions(ONLY_DB).flatMap { it.seeTopGenes() }
            GeneFilter.NO_DB -> seeActions(NO_DB).flatMap { it.seeTopGenes() }
            GeneFilter.ONLY_EXTERNAL_SERVICE -> seeExternalServiceActions().flatMap(ApiExternalServiceAction::seeTopGenes)
        }
    }

    /**
     * remove RestResourceCall structure and binding among genes
     */
    override fun doFlattenStructure() {

        // check the top structure
        val resources = groupsView()!!.getAllInGroup(GroupsOfChildren.MAIN).filterIsInstance<RestResourceCalls>()

        if (resources.isEmpty()) return

        // remove all bindings among genes
        removeAllBindingAmongGenes()

        val dnsActions = resources.flatMap { it.seeActions(ONLY_DNS)} as List<HostnameResolutionAction>
        val sqlActions = resources.flatMap { it.seeActions(ONLY_SQL) } as List<SqlAction>
        val mongoDbActions = resources.flatMap { it.seeActions(ONLY_MONGO) } as List<MongoDbAction>

        val groups = resources.flatMap { it.seeEnterpriseActionGroup() }

        removeResourceCall(resources)
        addChildrenToGroup(groups, GroupsOfChildren.MAIN)

        addChildrenToGroup(sqlActions, GroupsOfChildren.INITIALIZATION_SQL)
        addChildrenToGroup(mongoDbActions, GroupsOfChildren.INITIALIZATION_MONGO)
        addChildrenToGroup(dnsActions, GroupsOfChildren.INITIALIZATION_DNS)

        // re-generate local id
//        resetLocalIdRecursively()
//        doInitializeLocalId()
    }

    enum class ResourceFilter { ALL, NO_SQL, ONLY_SQL, ONLY_SQL_INSERTION, ONLY_SQL_EXISTING }

    /**
     * @return involved resources in [this] RestIndividual
     * for all [resourceCalls], we return their resource keys to represent the resource
     * for dbActions, we employ their table names to represent the resource
     *
     * NOTE that for [RestResourceCalls], there might exist DbAction as well.
     * But since the dbaction is to prepare resource for the endpoint whose goal is equivalent with POST here,
     * we do not consider such dbactions as separated resources from the endpoint.
     */
    fun seeResource(filter: ResourceFilter) : List<String>{
        return when(filter){
            ResourceFilter.ALL -> seeInitializingActions().filterIsInstance<SqlAction>().map { it.table.name }.plus(
                getResourceCalls().map { it.getResourceKey() }
            )
            ResourceFilter.NO_SQL -> getResourceCalls().map { it.getResourceKey() }
            ResourceFilter.ONLY_SQL -> seeInitializingActions().filterIsInstance<SqlAction>().map { it.table.name }
            ResourceFilter.ONLY_SQL_EXISTING -> seeInitializingActions().filterIsInstance<SqlAction>().filter { it.representExistingData }.map { it.table.name }
            ResourceFilter.ONLY_SQL_INSERTION -> seeInitializingActions().filterIsInstance<SqlAction>().filterNot { it.representExistingData }.map { it.table.name }
        }
    }

    /**
     * remove location id among actions used for minimization phase
     */
    fun removeLocationId(){
        seeMainExecutableActions().forEach { a->
            a.locationId = null
            a.saveLocation = false
        }
    }

    //FIXME refactor
    override fun verifyInitializationActions(): Boolean {
        return SqlActionUtils.verifyActions(seeInitializingActions().filterIsInstance<SqlAction>())
    }

    override fun copy(copyFilter: TraceableElementCopyFilter): RestIndividual {
        val copy = copy() as RestIndividual
        when(copyFilter){
            TraceableElementCopyFilter.NONE-> {}
            TraceableElementCopyFilter.WITH_TRACK, TraceableElementCopyFilter.DEEP_TRACK  ->{
                copy.wrapWithTracking(null, tracking!!.copy())
            }else -> throw IllegalStateException("${copyFilter.name} is not implemented!")
        }
        return copy
    }

    /**
     * for each call, there exist db actions for preparing resources.
     * however, the db action might refer to a db action which is not in the same call.
     * In this case, we need to repair the fk of db actions among calls.
     *
     * Note: this is ignoring the DB Actions in the initialization of the individual, as we
     * are building dependencies among resources here.
     *
     * TODO not sure whether build binding between fk and pk
     */
    fun repairDbActionsInCalls(){
        val previous = mutableListOf<SqlAction>()
        getResourceCalls().forEach { c->
            c.repairFK(previous)
            previous.addAll(c.seeActions(ONLY_SQL) as List<SqlAction>)
        }
    }

    /**
     * @return all groups of actions for resource handling
     */
    fun getResourceCalls() : List<RestResourceCalls> = children.filterIsInstance<RestResourceCalls>()


    /**
     * @return a list of EnterpriseActionGroups under GroupsOfChildren.MAIN
     * if the list is empty, it indicates that `doFlattenStructure` has been processed yet then return null
     */
    fun getFlattenMainEnterpriseActionGroup() : List<EnterpriseActionGroup<RestCallAction>>?{
        val groups = groupsView()!!.getAllInGroup(GroupsOfChildren.MAIN).filterIsInstance<EnterpriseActionGroup<*>>()
        if (groups.isEmpty()) return null
        return groups as List<EnterpriseActionGroup<RestCallAction>>
    }

    /**
     * return all the resource calls in this individual, with their index in the children list
     * @param isRelative indicates whether to return the relative index by only considering a list of resource calls
     */
    fun getIndexedResourceCalls(isRelative: Boolean = true) : Map<Int,RestResourceCalls> = getIndexedChildren(RestResourceCalls::class.java).run {
        if (isRelative)
            this.map { it.key - getFirstIndexOfRestResourceCalls() to it.value }.toMap()
        else
            this
    }

    /****************************** manipulate resource call in an individual *******************************************/
    /**
     * remove the resource at [position]
     */
    fun removeResourceCall(position : Int) {
        if(!getIndexedResourceCalls().keys.contains(position))
            throw IllegalArgumentException("position is out of range of list")
        val removed = killChildByIndex(getFirstIndexOfRestResourceCalls() + position) as RestResourceCalls
    }

    fun removeResourceCall(remove: List<RestResourceCalls>) {
        if(!getResourceCalls().containsAll(remove))
            throw IllegalArgumentException("specified rest calls are not part of this individual")
        killChildren(remove)
    }

    /**
     * add [restCalls] at [position], if [position] == -1, append the [restCalls] at the end
     */
    fun addResourceCall(position: Int = -1, restCalls : RestResourceCalls) {
        if (position == -1){
            addChildToGroup(restCalls, GroupsOfChildren.MAIN)
        }else{
            if(position > children.size)
                throw IllegalArgumentException("position is out of range of list")
            addChildToGroup(getFirstIndexOfRestResourceCalls() + position, restCalls, GroupsOfChildren.MAIN)
        }
    }

    private fun getFirstIndexOfRestResourceCalls() = max(0, max(children.indexOfLast { it is SqlAction }+1, children.indexOfFirst { it is RestResourceCalls }))

    /**
     * replace the resourceCall at [position] with [resourceCalls]
     */
    fun replaceResourceCall(position: Int, restCalls: RestResourceCalls){
        if(!getIndexedResourceCalls().keys.contains(position))
            throw IllegalArgumentException("position is out of range of list")

        removeResourceCall(position)
        addResourceCall(position, restCalls)
    }

    /**
     * switch the resourceCall at [position1] and the resourceCall at [position2]
     */
    fun swapResourceCall(position1: Int, position2: Int){
        val valid = getIndexedResourceCalls().keys
        if(!valid.contains(position1) || !valid.contains(position2))
            throw IllegalArgumentException("position is out of range of list")
        if(position1 == position2)
            throw IllegalArgumentException("It is not necessary to swap two same position on the resource call list")
        swapChildren(getFirstIndexOfRestResourceCalls() + position1, getFirstIndexOfRestResourceCalls() + position2)
    }


    fun getFixedActionIndexes(resourcePosition: Int)
    = getIndexedResourceCalls()[resourcePosition]!!.seeActions(ALL).filter { seeMainExecutableActions().contains(it) }.map {
        seeFixedMainActions().indexOf(it)
    }

    fun getDynamicActionLocalIds(resourcePosition: Int) =
        getIndexedResourceCalls()[resourcePosition]!!.seeActions(ALL).filter { seeDynamicMainActions().contains(it) }.map { it.getLocalId() }

    private fun validateSwap(first : Int, second : Int) : Boolean{
        //TODO need update, although currently not in use
        val position = getResourceCalls()[first].shouldBefore.map { r ->
            getResourceCalls().indexOfFirst { it.getResourceNodeKey() == r }
        }

        if(!position.none { it > second }) return false

        getResourceCalls().subList(0, second).find { it.shouldBefore.contains(getResourceCalls()[second].getResourceNodeKey()) }?.let {
            return getResourceCalls().indexOf(it) < first
        }
        return true

    }

    /**
     * @return movable position
     */
    fun getMovablePosition(position: Int) : List<Int>{
        return (getResourceCalls().indices)
                .filter {
                    if(it < position) validateSwap(it, position) else if(it > position) validateSwap(position, it) else false
                }
    }

    /**
     * @return whether the call at the position is movable
     */
    fun isMovable(position: Int) : Boolean{
        return (getResourceCalls().indices)
                .any {
                    if(it < position) validateSwap(it, position) else if(it > position) validateSwap(position, it) else false
                }
    }


    /**
     * @return possible swap positions of calls in this individual
     */
    fun extractSwapCandidates(): Map<Int, Set<Int>>{
        return getIndexedResourceCalls().map {
            val range = handleSwapCandidates(this, it.key)
            it.key to range
        }.filterNot { it.second.isEmpty() }.toMap()
    }

    private fun handleSwapCandidates(ind: RestIndividual, indexToSwap: Int): Set<Int>{
        val swapTo = handleSwapTo(ind, indexToSwap)
        return swapTo.filter { t -> handleSwapTo(ind, t).contains(indexToSwap) }.toSet()
    }

    private fun handleSwapTo(ind: RestIndividual, indexToSwap: Int): Set<Int>{

        val indexed = ind.getIndexedResourceCalls()
        val toSwap = indexed[indexToSwap]!!

        val before : Int = toSwap.shouldBefore.map { t ->
            indexed.filter { it.value.getResourceNodeKey() == t }
                .minByOrNull { it.key }?.key ?: (indexed.keys.maxOrNull()!! + 1)
        }.minOrNull() ?: (indexed.keys.maxOrNull()!! + 1)


        val after : Int = toSwap.depends.map { t->
            indexed.filter { it.value.getResourceNodeKey() == t }
                    .maxByOrNull { it.key }?.key ?: 0
        }.maxOrNull() ?: 0


        if (after >= before) return emptySet()
        return indexed.keys.filter { it >= after && it < before && it != indexToSwap }.toSet()
    }


    override fun getInsertTableNames(): List<String> {
        return seeSqlDbActions().filterNot { it.representExistingData }.map { it.table.name }
    }

    override fun seeMainExecutableActions(): List<RestCallAction> {
        return super.seeMainExecutableActions() as List<RestCallAction>
    }
}
