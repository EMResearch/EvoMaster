package org.evomaster.core.search.impact.impactinfocollection.sql

import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.gene.numeric.IntegerGene
import org.evomaster.core.search.gene.optional.NullableGene
import org.evomaster.core.search.impact.impactinfocollection.GeneImpact
import org.evomaster.core.search.impact.impactinfocollection.GeneImpactTest
import org.evomaster.core.search.impact.impactinfocollection.ImpactOptions
import org.evomaster.core.search.impact.impactinfocollection.MutatedGeneWithContext
import org.junit.jupiter.api.Test

/**
 * created by manzh on 2019-10-09
 */
class SqlNullImpactTest : GeneImpactTest() {
    override fun getGene(): Gene {
        val gene = IntegerGene("gene", 0)
        return NullableGene("o", isActive = false, gene = gene, nullLabel = "NULL")
    }

    override fun checkImpactType(impact: GeneImpact) {
        assert(impact is NullableImpact)
    }

    override fun simulateMutation(original: Gene, geneToMutate: Gene, mutationTag: Int): MutatedGeneWithContext {
        geneToMutate as NullableGene
        geneToMutate.apply {
            when{
                mutationTag == 0 -> (geneToMutate.gene as IntegerGene).value += 1
                mutationTag == 1 -> geneToMutate.isActive = !geneToMutate.isActive
                else -> throw IllegalArgumentException("bug")
            }
        }

        return MutatedGeneWithContext(current = geneToMutate, previous = original,)
    }

    @Test
    fun testActiveAndGene(){

        val gene = getGene()
        val impact = initImpact(gene)

        // false -> true
        val pair = template(gene, impact, listOf(ImpactOptions.ONLY_IMPACT), 1)

        impact as NullableImpact
        assertImpact(impact.presentImpact, (pair.second as NullableImpact).presentImpact, ImpactOptions.ONLY_IMPACT)
        assertImpact(impact.presentImpact.falseValue, (pair.second as NullableImpact).presentImpact.falseValue, ImpactOptions.NONE)
        assertImpact(impact.presentImpact.trueValue, (pair.second as NullableImpact).presentImpact.trueValue, ImpactOptions.ONLY_IMPACT)

        assertImpact(impact.geneImpact, (pair.second as NullableImpact).geneImpact, ImpactOptions.NONE)

        // mutate inside gene of optional gene.
        val pairG = template(pair.first, pair.second, listOf(ImpactOptions.IMPACT_IMPROVEMENT), 0)

        assertImpact((pair.second as NullableImpact).presentImpact, (pairG.second as NullableImpact).presentImpact, ImpactOptions.NONE)
        assertImpact((pair.second as NullableImpact).presentImpact.falseValue, (pairG.second as NullableImpact).presentImpact.falseValue, ImpactOptions.NONE)
        assertImpact((pair.second as NullableImpact).presentImpact.trueValue, (pairG.second as NullableImpact).presentImpact.trueValue, ImpactOptions.NONE)

        assertImpact((pair.second as NullableImpact).geneImpact, (pairG.second as NullableImpact).geneImpact, ImpactOptions.IMPACT_IMPROVEMENT)
    }
}