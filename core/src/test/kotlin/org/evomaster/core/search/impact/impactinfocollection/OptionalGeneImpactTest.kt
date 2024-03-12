package org.evomaster.core.search.impact.impactinfocollection

import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.gene.numeric.IntegerGene
import org.evomaster.core.search.gene.optional.OptionalGene
import org.evomaster.core.search.impact.impactinfocollection.value.OptionalGeneImpact
import org.junit.jupiter.api.Test

/**
 * created by manzh on 2019-10-09
 */
class OptionalGeneImpactTest : GeneImpactTest() {
    override fun getGene(): Gene {
        val gene = IntegerGene("gene", 0)
        return OptionalGene("o", isActive = false, gene = gene)
    }

    override fun checkImpactType(impact: GeneImpact) {
        assert(impact is OptionalGeneImpact)
    }

    override fun simulateMutation(original: Gene, geneToMutate: Gene, mutationTag: Int): MutatedGeneWithContext {
        geneToMutate as OptionalGene
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

        impact as OptionalGeneImpact
        assertImpact(impact.activeImpact, (pair.second as OptionalGeneImpact).activeImpact, ImpactOptions.ONLY_IMPACT)
        assertImpact(impact.activeImpact.falseValue, (pair.second as OptionalGeneImpact).activeImpact.falseValue, ImpactOptions.NONE)
        assertImpact(impact.activeImpact.trueValue, (pair.second as OptionalGeneImpact).activeImpact.trueValue, ImpactOptions.ONLY_IMPACT)

        assertImpact(impact.geneImpact, (pair.second as OptionalGeneImpact).geneImpact, ImpactOptions.NONE)

        // mutate inside gene of optional gene.
        val pairG = template(pair.first, pair.second, listOf(ImpactOptions.IMPACT_IMPROVEMENT), 0)

        assertImpact((pair.second as OptionalGeneImpact).activeImpact, (pairG.second as OptionalGeneImpact).activeImpact, ImpactOptions.NONE)
        assertImpact((pair.second as OptionalGeneImpact).activeImpact.falseValue, (pairG.second as OptionalGeneImpact).activeImpact.falseValue, ImpactOptions.NONE)
        assertImpact((pair.second as OptionalGeneImpact).activeImpact.trueValue, (pairG.second as OptionalGeneImpact).activeImpact.trueValue, ImpactOptions.NONE)

        assertImpact((pair.second as OptionalGeneImpact).geneImpact, (pairG.second as OptionalGeneImpact).geneImpact, ImpactOptions.IMPACT_IMPROVEMENT)
    }
}