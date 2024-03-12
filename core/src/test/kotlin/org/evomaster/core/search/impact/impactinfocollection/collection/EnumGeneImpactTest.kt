package org.evomaster.core.search.impact.impactinfocollection.collection

import org.evomaster.core.search.gene.collection.EnumGene
import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.impact.impactinfocollection.GeneImpact
import org.evomaster.core.search.impact.impactinfocollection.GeneImpactTest
import org.evomaster.core.search.impact.impactinfocollection.ImpactOptions
import org.evomaster.core.search.impact.impactinfocollection.MutatedGeneWithContext
import org.evomaster.core.search.impact.impactinfocollection.value.collection.EnumGeneImpact
import org.junit.jupiter.api.Test

/**
 * created by manzh on 2019-10-10
 */
class EnumGeneImpactTest : GeneImpactTest(){
    val items = listOf(1,2,3)

    override fun getGene(): Gene {
        return EnumGene("e", items, 0)
    }

    override fun checkImpactType(impact: GeneImpact) {
        assert(impact is EnumGeneImpact)
    }

    override fun simulateMutation(original: Gene, geneToMutate: Gene, mutationTag: Int): MutatedGeneWithContext {
        geneToMutate as EnumGene<Int>

        geneToMutate.index = (geneToMutate.index + 1)%items.size
        return MutatedGeneWithContext(current = geneToMutate, previous = original,)
    }


    @Test
    fun testValues(){
        val gene = getGene()
        val impact = initImpact(gene)

        //0->1
        val pair1 = template(gene, impact, listOf(ImpactOptions.ONLY_IMPACT))

        assertImpact(impact, pair1.second, ImpactOptions.ONLY_IMPACT)
        assertImpact((impact as EnumGeneImpact).values[0], (pair1.second as EnumGeneImpact).values[0], ImpactOptions.NONE)
        assertImpact((impact as EnumGeneImpact).values[1], (pair1.second as EnumGeneImpact).values[1], ImpactOptions.ONLY_IMPACT)
        assertImpact((impact as EnumGeneImpact).values[2], (pair1.second as EnumGeneImpact).values[2], ImpactOptions.NONE)


        //1->2
        val pair2 = template(pair1.first, pair1.second, listOf(ImpactOptions.IMPACT_IMPROVEMENT))

        assertImpact(pair1.second, pair2.second, ImpactOptions.IMPACT_IMPROVEMENT)
        assertImpact((pair1.second as EnumGeneImpact).values[0], (pair2.second as EnumGeneImpact).values[0], ImpactOptions.NONE)
        assertImpact((pair1.second as EnumGeneImpact).values[1], (pair2.second as EnumGeneImpact).values[1], ImpactOptions.NONE)
        assertImpact((pair1.second as EnumGeneImpact).values[2], (pair2.second as EnumGeneImpact).values[2], ImpactOptions.IMPACT_IMPROVEMENT)

        //2-> 0

        val pair0 = template(pair2.first, pair2.second, listOf(ImpactOptions.NO_IMPACT))

        assertImpact(pair2.second, pair0.second, ImpactOptions.NO_IMPACT)
        assertImpact((pair2.second as EnumGeneImpact).values[0], (pair0.second as EnumGeneImpact).values[0], ImpactOptions.NO_IMPACT)
        assertImpact((pair2.second as EnumGeneImpact).values[1], (pair0.second as EnumGeneImpact).values[1], ImpactOptions.NONE)
        assertImpact((pair2.second as EnumGeneImpact).values[2], (pair0.second as EnumGeneImpact).values[2], ImpactOptions.NONE)

    }
}