package org.evomaster.core.search.impact.impactinfocollection.numeric

import org.evomaster.core.search.gene.BooleanGene
import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.impact.impactinfocollection.GeneImpact
import org.evomaster.core.search.impact.impactinfocollection.GeneImpactTest
import org.evomaster.core.search.impact.impactinfocollection.ImpactOptions
import org.evomaster.core.search.impact.impactinfocollection.MutatedGeneWithContext
import org.evomaster.core.search.impact.impactinfocollection.value.numeric.BinaryGeneImpact
import org.junit.jupiter.api.Test

/**
 * created by manzh on 2019-10-08
 */
class BoolenGeneTest : GeneImpactTest() {

    override fun simulateMutation(original: Gene, geneToMutate: Gene, mutationTag: Int): MutatedGeneWithContext {
        geneToMutate as BooleanGene

        geneToMutate.value = !geneToMutate.value

        return MutatedGeneWithContext(current = geneToMutate, previous = original,)
    }

    override fun getGene(): Gene = BooleanGene("i",  value= false)

    override fun checkImpactType(impact: GeneImpact) {
        assert(impact is BinaryGeneImpact)
    }

    @Test
    fun testT2F(){
        val gene = BooleanGene("b",  value= true)
        val impact = initImpact(gene)
        val updatedImpact = template( gene, impact, listOf(ImpactOptions.NO_IMPACT)).second
        assertImpact((impact as BinaryGeneImpact).falseValue, (updatedImpact as BinaryGeneImpact).falseValue, ImpactOptions.NO_IMPACT)
    }

    @Test
    fun testF2T(){
        val gene = BooleanGene("b",  value= false)
        val impact = initImpact(gene)
        val updatedImpact = template( gene, impact, listOf(ImpactOptions.IMPACT_IMPROVEMENT)).second
        assertImpact((impact as BinaryGeneImpact).trueValue, (updatedImpact as BinaryGeneImpact).trueValue, ImpactOptions.IMPACT_IMPROVEMENT)
    }

    @Test
    fun testF2T2F(){
        val gene = BooleanGene("b",  value= false)
        val impact = initImpact(gene)
        val pair = template( gene, impact, listOf(ImpactOptions.IMPACT_IMPROVEMENT))
        assertImpact((impact as BinaryGeneImpact).trueValue, (pair.second as BinaryGeneImpact).trueValue, ImpactOptions.IMPACT_IMPROVEMENT)

        val upair = template(pair.first, pair.second, listOf(ImpactOptions.NO_IMPACT))
        assertImpact((pair.second as BinaryGeneImpact).falseValue, (upair.second as BinaryGeneImpact).falseValue, ImpactOptions.NO_IMPACT)
    }
}