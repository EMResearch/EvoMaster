package org.evomaster.core.search.gene.optional

import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.gene.ObjectGene
import org.evomaster.core.search.gene.placeholder.CycleObjectGene

/**
 * there might be a case where the 2 depth cycle is not enough to handle seeded value or responses
 * this class allows to replace a CycleObject with its ObjectGene based on the refType
 */
class FlexibleCycleObjectGene(
    name: String,
    gene: Gene,
    replaceable: Boolean = true
) : FlexibleGene(name, gene, replaceable) {

    init {
        if (gene !is ObjectGene && gene !is CycleObjectGene)
            throw IllegalArgumentException("For a FlexibleCycleObjectGene, its gene is either ObjectGene or CycleObjectGene")
    }

    override fun copyContent(): FlexibleCycleObjectGene {
        return FlexibleCycleObjectGene(name, gene.copy(), replaceable)
    }

    override fun replaceGeneTo(geneToUpdate: Gene) {
        if (geneToUpdate !is ObjectGene && geneToUpdate !is CycleObjectGene)
            throw IllegalArgumentException("For a FlexibleCycleObjectGene, its gene is either ObjectGene or CycleObjectGene")

        val currentRefType = if (gene is ObjectGene) (gene as ObjectGene).refType else (gene as CycleObjectGene).refType
        val replacedRefType = if (geneToUpdate is ObjectGene) geneToUpdate.refType else (geneToUpdate as CycleObjectGene).refType

        if (currentRefType != replacedRefType)
            throw IllegalArgumentException("Cannot replace a FlexibleCycleObjectGene with a gene which has different refType with current gene (current is $currentRefType and replaced is $replacedRefType)")

        super.replaceGeneTo(geneToUpdate)
    }

}