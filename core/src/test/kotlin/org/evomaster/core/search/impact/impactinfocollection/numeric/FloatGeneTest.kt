package org.evomaster.core.search.impact.impactinfocollection.numeric

import org.evomaster.core.search.gene.numeric.FloatGene
import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.impact.impactinfocollection.GeneImpact
import org.evomaster.core.search.impact.impactinfocollection.GeneImpactTest
import org.evomaster.core.search.impact.impactinfocollection.MutatedGeneWithContext
import org.evomaster.core.search.impact.impactinfocollection.value.numeric.FloatGeneImpact

/**
 * created by manzh on 2019-10-08
 */
class FloatGeneTest : GeneImpactTest() {

    override fun simulateMutation(original: Gene, geneToMutate: Gene, mutationTag: Int): MutatedGeneWithContext {
        geneToMutate as FloatGene

        if (geneToMutate.value + 1f > Float.MAX_VALUE)
            geneToMutate.value -= 1f
        else
            geneToMutate.value += 1f

        return MutatedGeneWithContext(current = geneToMutate, previous = original,)
    }

    override fun getGene(): Gene = FloatGene("i",  value= 1f)

    override fun checkImpactType(impact: GeneImpact) {
        assert(impact is FloatGeneImpact)
    }

}