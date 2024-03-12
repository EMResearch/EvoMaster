package org.evomaster.core.problem.rpc

import org.evomaster.core.Lazy
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

import org.evomaster.core.search.*
import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.tracer.TrackOperator
import java.util.*
import kotlin.math.max

/**
 * individual for RPC service
 */
class RPCIndividual(
    sampleType: SampleType,
    trackOperator: TrackOperator? = null,
    index: Int = -1,
    allActions: MutableList<ActionComponent>,
    mainSize: Int = allActions.size,
    sqlSize: Int = 0,
    mongoSize: Int = 0,
    dnsSize: Int = 0,
    groups: GroupsOfChildren<StructuralElement> = getEnterpriseTopGroups(allActions, mainSize, sqlSize,mongoSize,dnsSize)
) : ApiWsIndividual(
    sampleType,
    trackOperator, index, allActions,
    childTypeVerifier = EnterpriseChildTypeVerifier(RPCCallAction::class.java),
    groups
) {

    constructor(
        sampleType: SampleType,
        actions: MutableList<RPCCallAction>,
        externalServicesActions: MutableList<List<ApiExternalServiceAction>> = mutableListOf(),
        /*
            TODO might add sample type here as REST (check later)
         */
        dbInitialization: MutableList<SqlAction> = mutableListOf(),
        trackOperator: TrackOperator? = null,
        index: Int = -1
    ) : this(
        sampleType = sampleType,
        trackOperator = trackOperator,
        index = index,
        allActions = mutableListOf<ActionComponent>().apply {
            addAll(dbInitialization);
            addAll(actions.mapIndexed { index, rpcCallAction ->
                if (externalServicesActions.isNotEmpty())
                    Lazy.assert { actions.size == externalServicesActions.size }
                EnterpriseActionGroup(mutableListOf(rpcCallAction), RPCCallAction::class.java).apply {
                    addChildrenToGroup(
                        externalServicesActions[index],
                        GroupsOfChildren.EXTERNAL_SERVICES
                    )
                }})
        },
        mainSize = actions.size, sqlSize = dbInitialization.size)

    /**
     * TODO: Verify the implementation
     */
    override fun seeGenes(filter: GeneFilter): List<out Gene> {
        return when (filter) {
            GeneFilter.ALL -> seeAllActions().flatMap(Action::seeTopGenes)
            GeneFilter.NO_SQL -> seeActions(ActionFilter.NO_SQL).flatMap(Action::seeTopGenes)
            GeneFilter.ONLY_MONGO -> seeMongoDbActions().flatMap(MongoDbAction::seeTopGenes)
            GeneFilter.ONLY_SQL -> seeSqlDbActions().flatMap(SqlAction::seeTopGenes)
            GeneFilter.ONLY_DB -> seeActions(ActionFilter.ONLY_DB).flatMap { it.seeTopGenes() }
            GeneFilter.NO_DB -> seeActions(ActionFilter.NO_DB).flatMap { it.seeTopGenes() }
            GeneFilter.ONLY_EXTERNAL_SERVICE -> seeExternalServiceActions().flatMap(ApiExternalServiceAction::seeTopGenes)
        }
    }


    override fun canMutateStructure(): Boolean = true


    fun seeIndexedRPCCalls(): Map<Int, RPCCallAction> = getIndexedChildren(RPCCallAction::class.java)

    override fun verifyInitializationActions(): Boolean {
        return SqlActionUtils.verifyActions(seeInitializingActions().filterIsInstance<SqlAction>())
    }


    /**
     * add an action (ie, [action]) into [actions] at [position]
     */
    fun addAction(relativePosition: Int = -1, action: RPCCallAction) {
        val main = GroupsOfChildren.MAIN
        val g = EnterpriseActionGroup(mutableListOf(action), RPCCallAction::class.java)

        if (relativePosition == -1) {
            addChildToGroup(g, main)
        } else{
            val base = groupsView()!!.startIndexForGroupInsertionInclusive(main)
            val position = base + relativePosition
            addChildToGroup(position, action, main)
        }
    }

    /**
     * remove an action from [actions] at [position]
     */
    fun removeAction(position: Int) {
        killChildByIndex(getFirstIndexOfEnterpriseActionGroup() + position) as EnterpriseActionGroup<*>
    }

    private fun getFirstIndexOfEnterpriseActionGroup() = max(0, max(children.indexOfLast { it is SqlAction }+1, children.indexOfFirst { it is EnterpriseActionGroup<*> }))

    override fun copyContent(): Individual {
        return RPCIndividual(
            sampleType,
            trackOperator,
            index,
            children.map { it.copy() }.toMutableList() as MutableList<ActionComponent>,
            mainSize = groupsView()!!.sizeOfGroup(GroupsOfChildren.MAIN),
            sqlSize = groupsView()!!.sizeOfGroup(GroupsOfChildren.INITIALIZATION_SQL),
            mongoSize = groupsView()!!.sizeOfGroup(GroupsOfChildren.INITIALIZATION_MONGO),
            dnsSize = groupsView()!!.sizeOfGroup(GroupsOfChildren.INITIALIZATION_DNS)
        )
    }

    override fun seeMainExecutableActions(): List<RPCCallAction> {
        return super.seeMainExecutableActions() as List<RPCCallAction>
    }


    /**
     * @return a sorted list of involved interfaces in this test
     */
    fun getTestedInterfaces() : SortedSet<String> {
        return seeMainExecutableActions().map { it.interfaceId }.toSortedSet()
    }
}
