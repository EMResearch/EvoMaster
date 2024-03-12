package org.evomaster.core.search.impact.impactinfocollection

import org.evomaster.core.search.gene.Gene

/**
 * this can be used to represent a mutated gene in detail, including
 * @property current gene after mutation
 * @property previous gene before mutation
 * @property actionName refers to an action which contains the gene
 * @property actionTypeClass refers to the class name of the action which contains the gene
 * @property position indicates where the gene located in a view of an individual, e.g., index of action
 * @property actionLocalId indicates the local id of the action
 * @property isDynamicAction indicates whether the action belongs to [Individual.seeDynamicMainActions]
 */
class MutatedGeneWithContext(
    val current: Gene,
    val actionName: String = NO_ACTION,
    val position: Int? = null,
    val actionLocalId: String = NO_ACTION,
    val isDynamicAction: Boolean = false,
    val previous: Gene?,
    val numOfMutatedGene: Int = 1,
    val actionTypeClass: String? = null
){
    companion object{
        const val NO_ACTION = "NONE"
    }

    fun mainPosition(current: Gene, previous: Gene?, numOfMutatedGene: Int) : MutatedGeneWithContext{
        return MutatedGeneWithContext(
            current = current,
            actionName = this.actionName,
            position = this.position,
            actionLocalId = actionLocalId,
            isDynamicAction = isDynamicAction,
            previous = previous,
            numOfMutatedGene = numOfMutatedGene,
            actionTypeClass = actionTypeClass
        )
    }
}