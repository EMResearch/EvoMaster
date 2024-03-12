package org.evomaster.core.search.impact.impactinfocollection.date

import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.gene.numeric.IntegerGene
import org.evomaster.core.search.gene.datetime.TimeGene
import org.evomaster.core.search.impact.impactinfocollection.GeneImpact
import org.evomaster.core.search.impact.impactinfocollection.GeneImpactTest
import org.evomaster.core.search.impact.impactinfocollection.ImpactOptions
import org.evomaster.core.search.impact.impactinfocollection.MutatedGeneWithContext
import org.evomaster.core.search.impact.impactinfocollection.value.date.TimeGeneImpact
import org.junit.jupiter.api.Test

/**
 * created by manzh on 2019-10-09
 */
class TimeGeneImpactTest : GeneImpactTest() {

    override fun getGene(): Gene {
        return TimeGene("d", hour = IntegerGene("h", 16), minute = IntegerGene("m", 36), second = IntegerGene("s", 9))
    }

    override fun checkImpactType(impact: GeneImpact) {
        assert(impact is TimeGeneImpact)
    }

    override fun simulateMutation(original: Gene, geneToMutate: Gene, mutationTag: Int): MutatedGeneWithContext {
        geneToMutate as TimeGene
        geneToMutate.apply {
            when{
                mutationTag == 0 -> hour.value = (hour.value + 1)%24
                mutationTag == 1 -> minute.value = (minute.value + 1)%60
                mutationTag == 2 -> second.value = (second.value + 1)%60
                else -> throw IllegalArgumentException("bug")
            }
        }

        return MutatedGeneWithContext(current = geneToMutate, previous = original,)
    }

    @Test
    fun testHour(){
        val gene = getGene()
        val impact = initImpact(gene)
        val updatedImpact = template( gene, impact, listOf(ImpactOptions.IMPACT_IMPROVEMENT), 0).second

        impact as TimeGeneImpact
        updatedImpact as TimeGeneImpact
        assertImpact(impact.hourGeneImpact, updatedImpact.hourGeneImpact, ImpactOptions.IMPACT_IMPROVEMENT)
        assertImpact(impact.minuteGeneImpact, updatedImpact.minuteGeneImpact, ImpactOptions.NONE)
        assertImpact(impact.secondGeneImpact, updatedImpact.secondGeneImpact, ImpactOptions.NONE)
    }

    @Test
    fun testMin(){
        val gene = getGene()
        val impact = initImpact(gene)
        val updatedImpact = template( gene, impact, listOf(ImpactOptions.NO_IMPACT), 1).second

        impact as TimeGeneImpact
        updatedImpact as TimeGeneImpact
        assertImpact(impact.hourGeneImpact, updatedImpact.hourGeneImpact, ImpactOptions.NONE)
        assertImpact(impact.minuteGeneImpact, updatedImpact.minuteGeneImpact, ImpactOptions.NO_IMPACT)
        assertImpact(impact.secondGeneImpact, updatedImpact.secondGeneImpact, ImpactOptions.NONE)
    }

    @Test
    fun testSec(){
        val gene = getGene()
        val impact = initImpact(gene)
        val updatedImpact = template( gene, impact, listOf(ImpactOptions.ONLY_IMPACT), 2).second

        impact as TimeGeneImpact
        updatedImpact as TimeGeneImpact
        assertImpact(impact.hourGeneImpact, updatedImpact.hourGeneImpact, ImpactOptions.NONE)
        assertImpact(impact.minuteGeneImpact, updatedImpact.minuteGeneImpact, ImpactOptions.NONE)
        assertImpact(impact.secondGeneImpact, updatedImpact.secondGeneImpact, ImpactOptions.ONLY_IMPACT)
    }
}