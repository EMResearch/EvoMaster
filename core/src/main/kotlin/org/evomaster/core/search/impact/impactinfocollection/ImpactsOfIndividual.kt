package org.evomaster.core.search.impact.impactinfocollection

import org.evomaster.core.sql.SqlAction
import org.evomaster.core.mongo.MongoDbAction
import org.evomaster.core.problem.externalservice.HostnameResolutionAction
import org.evomaster.core.search.EnvironmentAction
import org.evomaster.core.search.action.Action
import org.evomaster.core.search.FitnessValue
import org.evomaster.core.search.Individual
import org.evomaster.core.search.action.ActionFilter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * this class is to collect impacts for an individual including
 * based on a structure of individual, the impacts are classified into
 * - initialization represents impacts of actions in initialization, eg DbAction
 * - mainActions represents impacts of main actions, eg, RestAction (see [Individual.seeMainExecutableActions])
 * Note that the impact collection ignores the structure of action tree, only consider
 * their root actions
 *
 * @property initActionImpacts impacts for initialization actions
 * @property fixedMainActionImpacts impacts for a sequence of main actions
 * @property dynamicMainActionImpacts impacts for main
 * @property impactsOfStructure impacts for a structure of rest actions
 */
open class ImpactsOfIndividual(
    /**
     * map of impacts in initialization based on [Individual.seeInitializingActions]
     * - key is the type of the action, eg SqlAction, MongoDbAction
     * - value is impacts for the actions
     */
    val initActionImpacts: MutableMap<String, InitializationGroupedActionsImpacts>,

    /**
     * list of impacts for actions based on [Individual.seeFixedMainActions]
     */
    val fixedMainActionImpacts: MutableList<ImpactsOfAction>,

    /**
     * list of impacts for actions based on [Individual.seeDynamicMainActions]
     */
    val dynamicMainActionImpacts: MutableList<ImpactsOfAction>,

    /**
     * a history of structures of [this] with best fitness
     */
    val impactsOfStructure: ActionStructureImpact = ActionStructureImpact("StructureSize")
) {

    constructor(individual: Individual, initActionTypes: List<String>, abstractInitializationGeneToMutate: Boolean,  fitnessValue: FitnessValue?) : this(
            initActionImpacts = initActionTypes.associateWith {
                InitializationGroupedActionsImpacts(
                    abstractInitializationGeneToMutate
                )
            }.toMutableMap(),
            fixedMainActionImpacts = individual.seeFixedMainActions().map { a -> ImpactsOfAction(a) }.toMutableList(),
            dynamicMainActionImpacts = individual.seeDynamicMainActions().map { a-> ImpactsOfAction(a) }.toMutableList()
    ) {
        if (individual.seeActions(ActionFilter.NO_INIT).isEmpty())
            throw IllegalArgumentException("there is no main action")

        if (fitnessValue != null) {
            impactsOfStructure.updateStructure(individual, fitnessValue)
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ImpactsOfIndividual::class.java)

        val SQL_ACTION_KEY = SqlAction::class.java.name


        val MONGODB_ACTION_KEY = MongoDbAction::class.java.name

        val HOSTNAME_RESOLUTION_KEY = HostnameResolutionAction::class.java.name
    }

    /**
     * @return a copy of [this]
     */
    open fun copy(): ImpactsOfIndividual {
        return ImpactsOfIndividual(
                initActionImpacts.map { it.key to it.value.copy() }.toMap().toMutableMap(),
                fixedMainActionImpacts.map { it.copy() }.toMutableList(),
                dynamicMainActionImpacts.map { it.copy() }.toMutableList(),
                impactsOfStructure.copy()
        )
    }

    /**
     * @return a clone of this.
     *
     * Note the with the clone, for the
     */
    open fun clone(): ImpactsOfIndividual {
        return ImpactsOfIndividual(
                initActionImpacts.map { it.key to it.value.clone() }.toMap().toMutableMap(),
                fixedMainActionImpacts.map { it.clone() }.toMutableList(),
                dynamicMainActionImpacts.map { it.clone() }.toMutableList(),
                impactsOfStructure.clone()
        )
    }

    /**
     * @return pair, key is the position at impacts, and value indicates false is to remove and true is to add
     */
    fun findFirstMismatchedIndexForFixedMainActions(actions: List<Action>) : Pair<Int, Boolean?>{
        actions.forEachIndexed { index, action ->
            if (index == fixedMainActionImpacts.size)
                return index to false
            if (action.getName() != fixedMainActionImpacts[index].actionName){
                val remove = index + 1 < fixedMainActionImpacts.size && fixedMainActionImpacts.subList(index+1, fixedMainActionImpacts.size).any {
                    it.actionName == action.getName()
                }
                return index to remove
            }
        }
        if (fixedMainActionImpacts.size > actions.size){
            return actions.size to true
        }
        return (-1 to null)
    }


    /**
     * verify fixed action impacts based on the given [individual]
     */
    fun verifyFixedActionGeneImpacts(individual: Individual){
        // fixed
        val fixed = individual.seeFixedMainActions()
        if (fixed.size != fixedMainActionImpacts.size)
            throw IllegalStateException("mismatched size of impacts according to actions: ${fixed.size} (actions) vs. ${fixedMainActionImpacts.size} (impacts)")
        fixed.forEachIndexed { index, action ->
            if (action.getName() != fixedMainActionImpacts[index].actionName)
                throw IllegalStateException("mismatched impact info at $index index: actual action is ${action.getName()}, but the impact info is ${fixedMainActionImpacts[index].actionName}")
        }

        // dynamic
        individual.seeDynamicMainActions().forEach { d->
            dynamicMainActionImpacts.filter { it.localId == d.getLocalId() }.apply {
                if (size != 1)
                    throw IllegalStateException("there should be only one impact, but there exist $size impacts for an action with local id ${d.getLocalId()}")
            }
        }
    }

    /**
     * @return size of existingData in the initialization
     */
    fun getSQLExistingData() = initActionImpacts[SQL_ACTION_KEY]?.getExistingData()?:0


    /**
     * @return size of action impacts
     * @param fromInitialization specifies whether the actions are in the initialization or not
     * @param initActionClass specifies if the size for a specific type of init action (name of the class of the init action) or all types of init actions (i.e., null)
     */
    fun getSizeOfActionImpacts(fromInitialization: Boolean, initActionClass : String? = null) =
        if (fromInitialization) {
            if(initActionClass != null)
                initActionImpacts[initActionClass]?.getSize()?:0
            else
                initActionImpacts.map { it.value.getSize() }.sum()
        } else (fixedMainActionImpacts.size + dynamicMainActionImpacts.size)

    /**
     * @param actionIndex is null when there is no action in the individual, then return the first GeneImpact.
     * Note that the index refers the relative index at actions grouped by the type
     */
    fun getGene(
        actionName: String?,
        initActionClassName: String?,
        geneId: String,
        actionIndex: Int?,
        localId: String?,
        fixedIndexedAction: Boolean,
        fromInitialization: Boolean
    ): GeneImpact? {
        // all individual should have at leadt one action, then remove this condition
        //if (actionIndex == null || (actionIndex == -1 && noneActionIndividual())) return fixedMainActionImpacts.first().geneImpacts[geneId]

        if (actionIndex == null && (fromInitialization || fixedIndexedAction))
            throw IllegalArgumentException("an index of the action must be given in order to get the gene")

        if (localId == null && !fixedIndexedAction)
            throw IllegalArgumentException("local id must be specified in order to get the gene")

        val impactsOfAction =
                if (fromInitialization) {
                    if (initActionClassName != null) initActionImpacts[initActionClassName]?.getImpactOfAction(actionName, actionIndex!!)
                    else initActionImpacts.values.firstNotNullOfOrNull {
                        it.getImpactOfAction(
                            actionName,
                            actionIndex!!
                        )
                    }
                }
                else if (fixedIndexedAction) findImpactOfFixedAction(actionName, actionIndex!!)
                else findDynamicImpactActionByLocalId(localId!!)
        impactsOfAction ?: return null
        return impactsOfAction.get(geneId, actionName)
    }

    /**
     * @return all genes whose name is [geneId]
     */
    fun getGeneImpact(geneId: String): List<GeneImpact> {
        val list = mutableListOf<GeneImpact>()

        initActionImpacts.values.flatMap { it.getAll() }.plus(fixedMainActionImpacts).plus(dynamicMainActionImpacts).forEach {
            if (it.geneImpacts.containsKey(geneId))
                list.add(it.geneImpacts[geneId]!!)
        }
        return list
    }

    @Deprecated("now the indiviual should have at least one action")
    private fun noneActionIndividual() = fixedMainActionImpacts.size == 1 && fixedMainActionImpacts.first().actionName == null

    /**
     * except the structure mutator,
     * the actions might be updated due to,
     *      eg, repair Db actions, new genes for the rest action with additional info, new external service actions
     * thus, we need to synchronize the action impacts based on the [individual]
     */
    fun syncBasedOnIndividual(individual: Individual) {
        individual.seeInitializingActions().groupBy { it::class.java.name }.forEach {g->
            syncInitActionsBasedOnIndividual(individual, g.key, g.value)
        }

        //for fixed action
        val fixed = individual.seeFixedMainActions()
        if ((fixed.isNotEmpty() && fixed.size != fixedMainActionImpacts.size) ||
            (fixed.isEmpty() && !noneActionIndividual()))
            throw IllegalArgumentException("inconsistent size of actions and impacts")

        fixed.forEach { action ->
            val actionName = action.getName()
            val index = fixed.indexOf(action)
            //root genes might be changed e.g., additionalInfo, so sync impacts of all genes
            action.seeTopGenes().forEach { g ->
                val id = ImpactUtils.generateGeneId(action, g)
                if (getGene(actionName,null, id, index, localId = null, fixedIndexedAction = true, false) == null) {
                    val impact = ImpactUtils.createGeneImpact(g, id)
                    fixedMainActionImpacts[index].addGeneImpact(actionName, impact)
                }
            }
        }

        // for dynamic action
        individual.seeDynamicMainActions().forEach { c->
            val impact = findDynamicImpactActionByLocalId(c.getLocalId())
            if (impact == null){
                dynamicMainActionImpacts.add(ImpactsOfAction(c.getLocalId(), c.getName()))
            }else{
                /*
                    root gene for the external service might be updated during evaluation
                 */
                c.seeTopGenes().forEach { g ->
                    val id = ImpactUtils.generateGeneId(c, g)
                    if (getGene(c.getName(), null, id, null, localId = c.getLocalId(), fixedIndexedAction = false, false) == null) {
                        val gimpact = ImpactUtils.createGeneImpact(g, id)
                        impact.addGeneImpact(c.getName(), gimpact)
                    }
                }
            }
        }
    }

    private fun syncInitActionsBasedOnIndividual(individual: Individual, initActionClassName: String, initActions : List<EnvironmentAction>) {

        val impactsForInitActionType = initActionImpacts[initActionClassName]?: throw IllegalArgumentException("cannot find impacts for initialization action typed with $initActionClassName")
        //for initialization due to db action fixing
        val diff = initActions.size - impactsForInitActionType.getOriginalSize()
        if (diff < 0) { //truncation
            impactsForInitActionType.truncation(initActions)
        }else if (diff > 0){
            throw IllegalArgumentException("impact is out of sync")
        }
        if (impactsForInitActionType.getOriginalSize() != initActions.size){
            throw IllegalStateException("inconsistent impact for SQL genes")
        }

    }

    /**
     * remove impacts for actions based on their index [actionIndex]
     * @return whether the removal performs successfully
     */
    fun deleteFixedActionGeneImpacts(actionIndex: Set<Int>): Boolean {
        if (actionIndex.isEmpty()) return false
        if (actionIndex.maxOrNull()!! >= fixedMainActionImpacts.size)
            return false
        actionIndex.sortedDescending().forEach {
            fixedMainActionImpacts.removeAt(it)
        }
        return true
    }

    /**
     * remove impacts for actions based on their localid
     * @return whether the removal performs successfully
     */
    fun deleteDynamicActionGeneImpacts(localIds: Set<String>): Boolean {
        return dynamicMainActionImpacts.removeIf {
            localIds.contains(it.localId)
        }
    }


    /**
     * swap gene impacts based on their index
     */
    fun swapFixedActionGeneImpact(actionIndex: List<Int>, swapTo: List<Int>){
        var a = actionIndex
        var b = swapTo

        if (swapTo.first() < actionIndex.first()){
            a = swapTo
            b = actionIndex
        }

        val aImpacts = a.map { fixedMainActionImpacts[it] }
        val bImpacts = b.map { fixedMainActionImpacts[it] }

        (a.plus(b)).sorted().reversed().forEach {
            fixedMainActionImpacts.removeAt(it)
        }

//        actionGeneImpacts.removeAll(aImpacts)
//        actionGeneImpacts.removeAll(bImpacts)


        fixedMainActionImpacts.addAll(a.first(), bImpacts)
        val bIndex = b.first() + (b.size - a.size)
        fixedMainActionImpacts.addAll(bIndex, aImpacts)
    }

    /**
     * update impacts for initialization
     */
    fun updateInitializationImpactsAtEnd(groupedActions: List<List<Action>>, existingDataSize: Int, initActionClassName: String, initAbstrat: Boolean) {
        initActionImpacts.getOrPut(initActionClassName, {InitializationGroupedActionsImpacts(initAbstrat)}).updateInitializationImpactsAtEnd(groupedActions, existingDataSize)
    }

    /**
     * init impacts for initialization
     */
    fun initInitializationImpacts(groupedActions: List<List<Action>>, existingDataSize: Int, initActionClassName: String, initAbstrat: Boolean) {
        initActionImpacts.getOrPut(initActionClassName, {InitializationGroupedActionsImpacts(initAbstrat)}).initInitializationActions(groupedActions, existingDataSize)
    }

    /**
     * append impacts for initialization
     */
    fun appendInitializationImpacts(groupedActions: List<List<Action>>, initActionClassName: String, initAbstrat: Boolean) {
        initActionImpacts.getOrPut(initActionClassName, {InitializationGroupedActionsImpacts(initAbstrat)}).appendInitialization(groupedActions)
    }

    /**
     * remove impacts for initialization
     */
    fun removeInitializationImpacts(removed : List<Pair<SqlAction, Int>>, existingDataSize: Int, initActionClassName: String, initAbstrat: Boolean){
        initActionImpacts.getOrPut(initActionClassName, {InitializationGroupedActionsImpacts(initAbstrat)}).updateSizeOfExistingData(existingDataSize)
        initActionImpacts[initActionClassName]!!.removeInitialization(removed)
    }

    /**
     * update impacts for initialization based on the given impacts of individual [other]
     */
    fun updateInitializationGeneImpacts(other: ImpactsOfIndividual, initActionClassName: String, initAbstrat: Boolean) {
        other.initActionImpacts[initActionClassName]?: throw IllegalArgumentException("cannot find `other`'s impacts for action typed with $initActionClassName")
        initActionImpacts.getOrPut(initActionClassName, {InitializationGroupedActionsImpacts(initAbstrat)}).initInitializationActions(other.initActionImpacts[initActionClassName]!!)
    }

    /**
     * update the size of existing data
     */
    fun updateExistingSQLData(size: Int, initActionClassName: String, initAbstrat: Boolean) {
        initActionImpacts.getOrPut(initActionClassName, {InitializationGroupedActionsImpacts(initAbstrat)}).updateSizeOfExistingData(size)
    }

    /**
     * add/ update gene actions based on
     * @param localId is the local id of the action
     * @param fixedIndexedAction represents whether the index of the action is fixed
     * @param actionName is the name of the action
     * @param actionIndex specifies the index of the action at the individual
     * @param newAction specifies whether the action is newly added
     * @param impacts specifies the impacts of the actions to be added/updated
     */
    fun addOrUpdateMainActionGeneImpacts(localId: String,
                                         fixedIndexedAction: Boolean,
                                         actionName: String?,
                                         actionIndex: Int,
                                         newAction: Boolean,
                                         impacts: MutableMap<String, GeneImpact>): Boolean {

        if (newAction) {
            if (fixedIndexedAction && actionIndex > fixedMainActionImpacts.size) return false
            if (!fixedIndexedAction && findDynamicImpactActionByLocalId(localId) != null) return false
            if (fixedIndexedAction)
                fixedMainActionImpacts.add(actionIndex, ImpactsOfAction(localId, actionName, impacts))
            else
                dynamicMainActionImpacts.add(ImpactsOfAction(localId, actionName, impacts))
            return true
        }

        if (!fixedIndexedAction){
            return (findDynamicImpactActionByLocalId(localId)?:throw IllegalStateException("cannot find the dynamic action with the localId")).addGeneImpact(actionName, impacts)
        }

        if (actionIndex >= fixedMainActionImpacts.size) return false
        return fixedMainActionImpacts[actionIndex].addGeneImpact(actionName, impacts)
    }

    /**
     * @return whether there exist any collected impact,
     *     e.g., time of manipulation is more than one for any gene/action
     */
    fun anyImpactfulInfo(): Boolean {
        for (a in initActionImpacts.values.flatMap { it.getAll() }.plus(fixedMainActionImpacts).plus(dynamicMainActionImpacts)) {
            if (a.anyImpactfulInfo()) return true
        }
        return false
    }

    /**
     * @return all flatten gene impacts for the individual
     */
    fun flattenAllGeneImpact(): List<GeneImpact> {
        return initActionImpacts.values.flatMap { it.getAll() }.plus(fixedMainActionImpacts).plus(dynamicMainActionImpacts).flatMap { it.geneImpacts.values }
    }

    /**
     * @return all gene impacts for each of actions in initialization of the individual
     * Note that for each of the action, we remove a map for all of the genes in the actions:
     *      - the key of the map is the id of genes based on [ImpactUtils.generateGeneId]
     *      - the value of the map is the gene impacts
     */
    fun getInitializationGeneImpact(): List<MutableMap<String, GeneImpact>> {
        return initActionImpacts.values.flatMap { it.getAll() }.map { it.geneImpacts }
    }

    /**
     * export impacts info to [content] for the given targets [targets]
     * @param areInitializationGeneImpact specifies whether the impacts are in the initialization
     */
    fun exportImpactInfo(areInitializationGeneImpact: Boolean, isFixed: Boolean, content : MutableList<String>, targets : Set<Int>? = null){
        val impacts = if (areInitializationGeneImpact) getInitializationGeneImpact() else getMainActionGeneImpact(isFixed)
        val prefix = if (areInitializationGeneImpact) "Initialization" else "${if (isFixed) "Fixed" else "Dynamic"}_Action"
        impacts.forEachIndexed { aindex, mutableMap ->
            mutableMap.forEach { (t, geneImpact) ->
                content.add(mutableListOf("$prefix$aindex", t).plus(geneImpact.toCSVCell(targets)).joinToString(","))
                geneImpact.flatViewInnerImpact().forEach { (name, impact) ->
                    content.add(mutableListOf("$prefix$aindex", "$t-$name").plus(impact.toCSVCell(targets)).joinToString(","))
                }
            }
        }
    }

    /**
     * @return all genes of the actions in the individual
     */
    fun getMainActionGeneImpact(isFixed : Boolean = true): List<MutableMap<String, GeneImpact>> {
        return (if (isFixed) fixedMainActionImpacts else dynamicMainActionImpacts).map { it.geneImpacts }
    }


    /**
     * @return whether there exist any impact
     */
    fun anyImpactInfo(): Boolean = initActionImpacts.values.map{ it.getSize() }.sum() > 0 || fixedMainActionImpacts.isNotEmpty() || dynamicMainActionImpacts.isNotEmpty()

    /**
     * @return an impact of action which is from dynamic action group of the individual
     */
    fun findDynamicImpactActionByLocalId(localId: String) : ImpactsOfAction?{
        return dynamicMainActionImpacts.find { it.localId == localId }
    }

    /**
     * @return an impact of action which is from fixed action group of the individual
     */
    fun findImpactOfFixedAction(actionName: String?, actionIndex: Int): ImpactsOfAction {
        if (actionIndex >= fixedMainActionImpacts.size)
            throw IllegalArgumentException("exceed the boundary of impacts regarding actions, i.e., size of actions is ${fixedMainActionImpacts.size}, but asking index is $actionIndex")
        val actionImpacts = fixedMainActionImpacts[actionIndex]
        if (actionName != null && actionImpacts.actionName != actionName)
            throw IllegalArgumentException("mismatched action name, i.e., current is ${actionImpacts.actionName}, but $actionName")
        return actionImpacts
    }

    /**
     * @return impact of action based on
     * @param actionName specifies the name of the action
     * @param actionIndex specifies the index of the actions in the initialization or not from the individual
     * @param localId specifies the local id of the action
     * @param fixedIndexedAction specifies whether the action is from fixed action group of the individual
     * @param fromInitialization specifies whether the actions are in the initialization
     */
    fun findImpactsByAction(
        actionName: String,
        actionIndex: Int,
        localId: String?,
        fixedIndexedAction: Boolean,
        fromInitialization: Boolean,
        initActionClass: String?
    ): MutableMap<String, GeneImpact>? {
        val found = findImpactsAction(actionName, actionIndex, localId, fixedIndexedAction, fromInitialization, initActionClass) ?: return null
        return found.geneImpacts
    }

    private fun findImpactsAction(
        actionName: String,
        actionIndex: Int,
        localId: String?,
        fixedIndexedAction: Boolean,
        fromInitialization: Boolean,
        initActionClass: String?
    ): ImpactsOfAction? {
        return try {
            if (fromInitialization){
                if(initActionClass == null)
                    null
                else
                    initActionImpacts[initActionClass]?.getImpactOfAction(actionName, actionIndex)
            }
            else if (fixedIndexedAction)
                findImpactOfFixedAction(actionName, actionIndex)
            else {
                if (localId == null)
                    throw IllegalArgumentException("local id must be specified in order to find the gene")
                findDynamicImpactActionByLocalId(localId)
            }
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}