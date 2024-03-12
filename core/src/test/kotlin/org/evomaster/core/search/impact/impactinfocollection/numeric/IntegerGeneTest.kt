package org.evomaster.core.search.impact.impactinfocollection.numeric

import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.gene.numeric.IntegerGene
import org.evomaster.core.search.impact.impactinfocollection.GeneImpact
import org.evomaster.core.search.impact.impactinfocollection.GeneImpactTest
import org.evomaster.core.search.impact.impactinfocollection.MutatedGeneWithContext
import org.evomaster.core.search.impact.impactinfocollection.value.numeric.IntegerGeneImpact

/**
 * created by manzh on 2019-10-08
 */
class IntegerGeneTest : GeneImpactTest() {

    override fun simulateMutation(original: Gene, geneToMutate: Gene, mutationTag: Int): MutatedGeneWithContext {
        geneToMutate as IntegerGene

        if (geneToMutate.value + 1 > Int.MAX_VALUE)
            geneToMutate.value -= 1
        else
            geneToMutate.value += 1

        return MutatedGeneWithContext(current = geneToMutate, previous = original,)
    }

    override fun getGene(): Gene = IntegerGene("i", value = 2)

    override fun checkImpactType(impact: GeneImpact) {
        assert(impact is IntegerGeneImpact)
    }

}