package org.evomaster.core.search.service.mutator

import org.evomaster.core.search.EnvironmentAction
import org.evomaster.core.sql.SqlAction
import org.evomaster.core.search.action.Action
import org.evomaster.core.search.Individual
import org.evomaster.core.search.gene.Gene

/**
 * created by manzh on 2019-09-10
 *
 * Class used to collect what mutations have been applied to the genes in an individual.
 *
 * Based on their possible impact on fitness, based on MAIN actions or DB actions, we treat them separately.
 * Eg, could have different mutation rates based on type.
 *
 * TODO how to handle new types? and how to make sure to crash here if new type is introduced but this class
 * is not updated?
 * FIXME for example, now we have actions for MongoDB and ExternalService, and in future might have Kafka as well, plus
 * who knows in  some years...
 * likely issue a warning and have some default behavior
 *
 * @property mutatedGenes records what genes are mutated
 * @property mutatedDbGenes records what db genes are mutated
 * @property addedGenes (structure mutation) records what genes are added using structure mutator
 * @property removedGene (structure mutation) records what genes are removed using structure mutator
 * @property mutatedPosition records where mutated/added/removed genes are located. but regarding different individual,
 *          the position may be parsed in different way. For instance, the position may indicate the position of resource calls,
 *          not rest action.
 * @property mutatedDbActionPosition records where mutated/added/removed dbgenes are located.
 */
data class MutatedGeneSpecification (
    val mutatedGenes : MutableList<MutatedGene> = mutableListOf(),
    val mutatedDbGenes : MutableList<MutatedGene> = mutableListOf(),
    val mutatedInitGenes : MutableList<MutatedGene> = mutableListOf(),

    //init action handling
    val addedActionsInInitializationGenes : MutableMap<String, MutableList<Gene>> = mutableMapOf(),
    val addedExistingDataInInitialization: MutableMap<String, MutableList<EnvironmentAction>> = mutableMapOf(),
    val addedGroupedActionsInInitialization: MutableMap<String, MutableList<List<EnvironmentAction>>> = mutableMapOf(),

    //SQL resource handling
    val addedSqlActions : MutableList<List<SqlAction>> = mutableListOf(),
    val removedSqlActions : MutableList<Pair<SqlAction, Int>> = mutableListOf(),

    // external service actions
    val addedExternalServiceActions : MutableList<Action> = mutableListOf()
){

    var mutatedIndividual: Individual? = null
        private set

    fun setMutatedIndividual(individual: Individual){
        if (mutatedIndividual!= null)
            throw IllegalArgumentException("it does not allow setting mutated individual more than one time")
        mutatedIndividual = individual
    }

    fun addMutatedGene(isDb : Boolean, isInit : Boolean, valueBeforeMutation : String, gene : Gene, position : Int?, localId: String?, resourcePosition: Int? = null){
        (if (isInit)
            mutatedInitGenes
        else if (isDb){
            mutatedDbGenes
        }else{
            mutatedGenes
        }).add(MutatedGene(valueBeforeMutation, gene, actionPosition=position, localId=localId, resourcePosition = resourcePosition))
    }


    //FIXME: need documentation for these parameters
    /**
     * @param action represents the action to be added/removed
     * @param position represents the location of the fixed indexed action in the individual, note that the location is in terms of the fixedMainAction group
     * @param localId represents the local id of the action, and it is used for dynamic main action
     * @param removed represents the mutation type, either add or remove
     * @param resourcePosition represents the index of resource if the action belongs to such structure
     */
    fun addRemovedOrAddedByAction(action: Action, position: Int?, localId: String?, removed : Boolean, resourcePosition: Int?){
        mutatedGenes.addAll(
            action.seeTopGenes().map { MutatedGene(null, it, position, localId = localId,
                    if (removed) MutatedType.REMOVE else MutatedType.ADD, resourcePosition = resourcePosition) }
        )
        if (action.seeTopGenes().isEmpty()){
            mutatedGenes.add(MutatedGene(null, null, position,localId = localId,
                    if (removed) MutatedType.REMOVE else MutatedType.ADD, resourcePosition = resourcePosition))
        }
    }

    fun swapAction(resourcePosition: Int, from: List<Int>, to: List<Int>){
        mutatedGenes.add(
            MutatedGene(previousValue = null, gene = null, actionPosition =null, type = MutatedType.SWAP, localId = null,
                resourcePosition = resourcePosition, from = from, to = to)
        )
    }

    fun isActionMutated(actionIndex : Int?, actionLocalId: String?, isInit: Boolean) : Boolean{
        if (isInit)
            return mutatedInitGenes.any { it.type == MutatedType.MODIFY && it.actionPosition == actionIndex }

        return (mutatedGenes.plus(mutatedDbGenes)).any { it.type == MutatedType.MODIFY && (
                it.actionPosition == actionIndex || it.localId == actionLocalId) }
    }

    fun getRemoved(isRest : Boolean) =
        (if (isRest) mutatedGenes else (mutatedInitGenes.plus(mutatedDbGenes))).filter { it.type == MutatedType.REMOVE}

    fun getAdded(isRest : Boolean) = (if (isRest) mutatedGenes else (mutatedInitGenes.plus(mutatedDbGenes))).filter { it.type == MutatedType.ADD}
    fun getMutated(isRest : Boolean) = (if (isRest) mutatedGenes else (mutatedInitGenes.plus(mutatedDbGenes))).filter { it.type == MutatedType.MODIFY}
    fun getSwap() = mutatedGenes.filter { it.type == MutatedType.SWAP }


    fun mutatedGeneInfo() = mutatedGenes.map { it.gene }
    fun mutatedInitGeneInfo() = mutatedInitGenes.map { it.gene }
    fun mutatedDbGeneInfo() = mutatedDbGenes.map { it.gene }

    fun numOfMutatedGeneInfo() = mutatedGenes.size + mutatedDbGenes.size+ mutatedInitGenes.size

    fun didAddInitializationGenes() = addedActionsInInitializationGenes.isNotEmpty() || addedExistingDataInInitialization.isNotEmpty()

    data class MutatedGene(
        /**
         * previous value of the mutated gene if it has
         */
        val previousValue : String? = null,
        /**
         * the mutated gene
         */
        val gene:  Gene?,
        /**
         * where the gene is located at
         * we employ an index of action for initialization and fixedMainAction
         */
        val actionPosition: Int?,
        /**
         * for dynimiac main action, we employ local id of the action to target the action
         * which contains the mutated gene
         */
        val localId : String?,
        /**
         * the type of mutation representing what the mutation was performed
         */
        val type : MutatedType = MutatedType.MODIFY,
        /**
         * the index of resource if the gene belongs to such structure
         */
        val resourcePosition: Int? = actionPosition,
        /**
         * when swap mutation is applied, it is used to record the `from` positions
         *
         * Note that this mutator is only applicable to fixed main action
         */
        val from : List<Int>? = null,
        /**
         * when swap mutation is applied, it is used to record the `to` positions
         *
         * Note that this mutator is only applicable to fixed main action
         */
        val to : List<Int>? = null
    )

    enum class MutatedType{
        ADD,
        REMOVE,
        MODIFY,
        SWAP
    }

    // add, remove, swap, add_sql, remove_sql
    fun didStructureMutation() =  mutatedGenes.any { it.type != MutatedType.MODIFY }
            || (mutatedInitGenes.plus(mutatedDbGenes)).any { it.type != MutatedType.MODIFY }
            || addedSqlActions.isNotEmpty() || removedSqlActions.isNotEmpty()

    fun isMutated(gene: Gene) = (mutatedInitGenes.plus(mutatedDbGenes)).any { it.gene == gene }
            || mutatedGenes.any { it.gene == gene }
            || addedSqlActions.flatten().any { it.seeTopGenes().contains(gene) }
            || removedSqlActions.map { it.first }.any { it.seeTopGenes().contains(gene) }

    fun mutatedActionOrInit() = setOf((mutatedGenes.plus(mutatedDbGenes)).isEmpty(), mutatedInitGenes.isNotEmpty())

    /**
     * repair mutated db genes based on [individual] after db repair
     */
    fun repairInitAndDbSpecification(individual: Individual) : Boolean{
        val init = individual.seeInitializingActions()
        var anyRemove = mutatedInitGenes.removeIf {
            it.type == MutatedType.MODIFY && it.actionPosition != null && it.actionPosition >= init.size
        }

        val noInit = individual.seeFixedMainActions()
        anyRemove = mutatedDbGenes.removeIf {
            it.type == MutatedType.MODIFY && (if (it.actionPosition != null)
                it.actionPosition >= noInit.size
            else if (it.localId != null)
                noInit.none { a-> a.getLocalId() == it.localId }
            else
                throw IllegalArgumentException("to represent mutated gene info, position or local id of an action which contains the gene must be specified"))
        } || anyRemove


        return anyRemove
    }
}