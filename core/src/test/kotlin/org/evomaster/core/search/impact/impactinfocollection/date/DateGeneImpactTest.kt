package org.evomaster.core.search.impact.impactinfocollection.date

import org.evomaster.core.search.gene.datetime.DateGene
import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.gene.numeric.IntegerGene
import org.evomaster.core.search.impact.impactinfocollection.GeneImpact
import org.evomaster.core.search.impact.impactinfocollection.GeneImpactTest
import org.evomaster.core.search.impact.impactinfocollection.ImpactOptions
import org.evomaster.core.search.impact.impactinfocollection.MutatedGeneWithContext
import org.evomaster.core.search.impact.impactinfocollection.value.date.DateGeneImpact
import org.junit.jupiter.api.Test

/**
 * created by manzh on 2019-10-09
 */
class DateGeneImpactTest : GeneImpactTest() {

    override fun getGene(): Gene {
        return DateGene("d", year = IntegerGene("y", 2019), month = IntegerGene("m", 10), day = IntegerGene("d", 9))
    }

    override fun checkImpactType(impact: GeneImpact) {
        assert(impact is DateGeneImpact)
    }

    override fun simulateMutation(original: Gene, geneToMutate: Gene, mutationTag: Int): MutatedGeneWithContext {
        geneToMutate as DateGene
        geneToMutate.apply {
            when {
                mutationTag == 0 -> year.value = year.value + 1
                mutationTag == 1 -> month.value = month.value + 1
                mutationTag == 2 -> day.value = day.value + 1
                else -> throw IllegalArgumentException("bug")
            }
        }

        return MutatedGeneWithContext(current = geneToMutate, previous = original,)
    }

    @Test
    fun testYear() {
        val gene = getGene()
        val impact = initImpact(gene)
        val updatedImpact = template(gene, impact, listOf(ImpactOptions.IMPACT_IMPROVEMENT), 0).second
        assertImpact(
            (impact as DateGeneImpact).yearGeneImpact,
            (updatedImpact as DateGeneImpact).yearGeneImpact,
            ImpactOptions.IMPACT_IMPROVEMENT
        )
        assertImpact(impact.monthGeneImpact, updatedImpact.monthGeneImpact, ImpactOptions.NONE)
        assertImpact(impact.dayGeneImpact, updatedImpact.dayGeneImpact, ImpactOptions.NONE)

    }

    @Test
    fun testMonth() {
        val gene = getGene()
        val impact = initImpact(gene)
        val updatedImpact = template(gene, impact, listOf(ImpactOptions.NO_IMPACT), 1).second
        assertImpact(
            (impact as DateGeneImpact).monthGeneImpact,
            (updatedImpact as DateGeneImpact).monthGeneImpact,
            ImpactOptions.NO_IMPACT
        )

        assertImpact(impact.yearGeneImpact, updatedImpact.yearGeneImpact, ImpactOptions.NONE)
        assertImpact(impact.dayGeneImpact, updatedImpact.dayGeneImpact, ImpactOptions.NONE)
    }

    @Test
    fun testDay() {
        val gene = getGene()
        val impact = initImpact(gene)
        val updatedImpact = template(gene, impact, listOf(ImpactOptions.ONLY_IMPACT), 2).second
        assertImpact(
            (impact as DateGeneImpact).dayGeneImpact,
            (updatedImpact as DateGeneImpact).dayGeneImpact,
            ImpactOptions.ONLY_IMPACT
        )

        assertImpact(impact.monthGeneImpact, updatedImpact.monthGeneImpact, ImpactOptions.NONE)
        assertImpact(impact.yearGeneImpact, updatedImpact.yearGeneImpact, ImpactOptions.NONE)
    }
}