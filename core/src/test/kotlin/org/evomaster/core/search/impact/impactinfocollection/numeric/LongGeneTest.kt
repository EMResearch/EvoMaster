package org.evomaster.core.search.impact.impactinfocollection.numeric

import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.gene.numeric.LongGene
import org.evomaster.core.search.impact.impactinfocollection.GeneImpact
import org.evomaster.core.search.impact.impactinfocollection.GeneImpactTest
import org.evomaster.core.search.impact.impactinfocollection.MutatedGeneWithContext
import org.evomaster.core.search.impact.impactinfocollection.value.numeric.LongGeneImpact

/**
 * created by manzh on 2019-10-08
 */
class LongGeneTest : GeneImpactTest() {

    override fun simulateMutation(original: Gene, geneToMutate: Gene, mutationTag: Int): MutatedGeneWithContext {
        geneToMutate as LongGene

        if (geneToMutate.value + 1L > Long.MAX_VALUE)
            geneToMutate.value -= 1L
        else
            geneToMutate.value += 1L

        return MutatedGeneWithContext(current = geneToMutate, previous = original,)
    }

    override fun getGene(): Gene = LongGene("i",  value= 1L)

    override fun checkImpactType(impact: GeneImpact) {
        assert(impact is LongGeneImpact)
    }

}