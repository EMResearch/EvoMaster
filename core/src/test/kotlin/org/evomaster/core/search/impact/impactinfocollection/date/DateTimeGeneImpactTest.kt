package org.evomaster.core.search.impact.impactinfocollection.date

import org.evomaster.core.search.gene.*
import org.evomaster.core.search.gene.datetime.DateGene
import org.evomaster.core.search.gene.datetime.DateTimeGene
import org.evomaster.core.search.gene.datetime.TimeGene
import org.evomaster.core.search.gene.numeric.IntegerGene
import org.evomaster.core.search.impact.impactinfocollection.GeneImpact
import org.evomaster.core.search.impact.impactinfocollection.GeneImpactTest
import org.evomaster.core.search.impact.impactinfocollection.ImpactOptions
import org.evomaster.core.search.impact.impactinfocollection.MutatedGeneWithContext
import org.evomaster.core.search.impact.impactinfocollection.value.date.DateTimeGeneImpact
import org.junit.jupiter.api.Test

/**
 * created by manzh on 2019-10-09
 */
class DateTimeGeneImpactTest : GeneImpactTest() {

    override fun getGene(): Gene {
        val date = DateGene("d", year = IntegerGene("y", 2019), month = IntegerGene("m", 10), day = IntegerGene("d", 9))
        val time = TimeGene("t", hour = IntegerGene("h", 16), minute = IntegerGene("m", 36), second = IntegerGene("s", 9))
        return DateTimeGene("dt", date, time)
    }

    override fun checkImpactType(impact: GeneImpact) {
        assert(impact is DateTimeGeneImpact)
    }

    override fun simulateMutation(original: Gene, geneToMutate: Gene, mutationTag: Int): MutatedGeneWithContext {
        geneToMutate as DateTimeGene
        geneToMutate.apply {
            when{
                mutationTag == 0 -> date.year.value = date.year.value + 1
                mutationTag == 1 -> date.month.value = date.month.value + 1
                mutationTag == 2 -> date.day.value = date.day.value + 1
                mutationTag == 3 -> time.hour.value = time.hour.value + 1
                mutationTag == 4 -> time.minute.value = time.minute.value + 1
                mutationTag == 5 -> time.second.value = time.second.value + 1
                else -> throw IllegalArgumentException("bug")
            }
        }

        return MutatedGeneWithContext(current = geneToMutate, previous = original,)
    }

    @Test
    fun testDate(){
        val gene = getGene()
        val impact = initImpact(gene)
        val pairY = template( gene, impact, listOf(ImpactOptions.IMPACT_IMPROVEMENT), 0)

        impact as DateTimeGeneImpact

        assertImpact(impact.dateGeneImpact, (pairY.second as DateTimeGeneImpact).dateGeneImpact, ImpactOptions.IMPACT_IMPROVEMENT)
        assertImpact(impact.timeGeneImpact, (pairY.second as DateTimeGeneImpact).timeGeneImpact, ImpactOptions.NONE)

        assertImpact(impact.dateGeneImpact.yearGeneImpact, (pairY.second as DateTimeGeneImpact).dateGeneImpact.yearGeneImpact, ImpactOptions.IMPACT_IMPROVEMENT)
        assertImpact(impact.dateGeneImpact.monthGeneImpact, (pairY.second as DateTimeGeneImpact).dateGeneImpact.monthGeneImpact, ImpactOptions.NONE)
        assertImpact(impact.dateGeneImpact.dayGeneImpact, (pairY.second as DateTimeGeneImpact).dateGeneImpact.dayGeneImpact, ImpactOptions.NONE)

        val pairM = template( pairY.first, pairY.second, listOf(ImpactOptions.NO_IMPACT), 1)

        assertImpact((pairY.second as DateTimeGeneImpact).dateGeneImpact, (pairM.second as DateTimeGeneImpact).dateGeneImpact, ImpactOptions.NO_IMPACT)
        assertImpact((pairY.second as DateTimeGeneImpact).timeGeneImpact, (pairM.second as DateTimeGeneImpact).timeGeneImpact, ImpactOptions.NONE)

        assertImpact((pairY.second as DateTimeGeneImpact).dateGeneImpact.yearGeneImpact, (pairM.second as DateTimeGeneImpact).dateGeneImpact.yearGeneImpact, ImpactOptions.NONE)
        assertImpact((pairY.second as DateTimeGeneImpact).dateGeneImpact.monthGeneImpact, (pairM.second as DateTimeGeneImpact).dateGeneImpact.monthGeneImpact, ImpactOptions.NO_IMPACT)
        assertImpact((pairY.second as DateTimeGeneImpact).dateGeneImpact.dayGeneImpact, (pairM.second as DateTimeGeneImpact).dateGeneImpact.dayGeneImpact, ImpactOptions.NONE)

        val pairD = template( pairM.first, pairM.second, listOf(ImpactOptions.ONLY_IMPACT), 2)

        assertImpact((pairM.second as DateTimeGeneImpact).dateGeneImpact, (pairD.second as DateTimeGeneImpact).dateGeneImpact, ImpactOptions.ONLY_IMPACT)
        assertImpact((pairM.second as DateTimeGeneImpact).timeGeneImpact, (pairD.second as DateTimeGeneImpact).timeGeneImpact, ImpactOptions.NONE)

        assertImpact((pairM.second as DateTimeGeneImpact).dateGeneImpact.yearGeneImpact, (pairD.second as DateTimeGeneImpact).dateGeneImpact.yearGeneImpact, ImpactOptions.NONE)
        assertImpact((pairM.second as DateTimeGeneImpact).dateGeneImpact.monthGeneImpact, (pairD.second as DateTimeGeneImpact).dateGeneImpact.monthGeneImpact, ImpactOptions.NONE)
        assertImpact((pairM.second as DateTimeGeneImpact).dateGeneImpact.dayGeneImpact, (pairD.second as DateTimeGeneImpact).dateGeneImpact.dayGeneImpact, ImpactOptions.ONLY_IMPACT)


    }

    @Test
    fun testTime(){
        val gene = getGene()
        val impact = initImpact(gene)
        val pairY = template( gene, impact, listOf(ImpactOptions.IMPACT_IMPROVEMENT), 3)

        impact as DateTimeGeneImpact

        assertImpact(impact.dateGeneImpact, (pairY.second as DateTimeGeneImpact).dateGeneImpact, ImpactOptions.NONE)
        assertImpact(impact.timeGeneImpact, (pairY.second as DateTimeGeneImpact).timeGeneImpact, ImpactOptions.IMPACT_IMPROVEMENT)

        assertImpact(impact.timeGeneImpact.hourGeneImpact, (pairY.second as DateTimeGeneImpact).timeGeneImpact.hourGeneImpact, ImpactOptions.IMPACT_IMPROVEMENT)
        assertImpact(impact.timeGeneImpact.minuteGeneImpact, (pairY.second as DateTimeGeneImpact).timeGeneImpact.minuteGeneImpact, ImpactOptions.NONE)
        assertImpact(impact.timeGeneImpact.secondGeneImpact, (pairY.second as DateTimeGeneImpact).timeGeneImpact.secondGeneImpact, ImpactOptions.NONE)

        val pairM = template( pairY.first, pairY.second, listOf(ImpactOptions.NO_IMPACT), 4)

        assertImpact((pairY.second as DateTimeGeneImpact).dateGeneImpact, (pairM.second as DateTimeGeneImpact).dateGeneImpact, ImpactOptions.NONE)
        assertImpact((pairY.second as DateTimeGeneImpact).timeGeneImpact, (pairM.second as DateTimeGeneImpact).timeGeneImpact, ImpactOptions.NO_IMPACT)

        assertImpact((pairY.second as DateTimeGeneImpact).timeGeneImpact.hourGeneImpact, (pairM.second as DateTimeGeneImpact).timeGeneImpact.hourGeneImpact, ImpactOptions.NONE)
        assertImpact((pairY.second as DateTimeGeneImpact).timeGeneImpact.minuteGeneImpact, (pairM.second as DateTimeGeneImpact).timeGeneImpact.minuteGeneImpact, ImpactOptions.NO_IMPACT)
        assertImpact((pairY.second as DateTimeGeneImpact).timeGeneImpact.secondGeneImpact, (pairM.second as DateTimeGeneImpact).timeGeneImpact.secondGeneImpact, ImpactOptions.NONE)

        val pairD = template( pairM.first, pairM.second, listOf(ImpactOptions.ONLY_IMPACT), 5)

        assertImpact((pairM.second as DateTimeGeneImpact).dateGeneImpact, (pairD.second as DateTimeGeneImpact).dateGeneImpact, ImpactOptions.NONE)
        assertImpact((pairM.second as DateTimeGeneImpact).timeGeneImpact, (pairD.second as DateTimeGeneImpact).timeGeneImpact, ImpactOptions.ONLY_IMPACT)

        assertImpact((pairM.second as DateTimeGeneImpact).timeGeneImpact.hourGeneImpact, (pairD.second as DateTimeGeneImpact).timeGeneImpact.hourGeneImpact, ImpactOptions.NONE)
        assertImpact((pairM.second as DateTimeGeneImpact).timeGeneImpact.minuteGeneImpact, (pairD.second as DateTimeGeneImpact).timeGeneImpact.minuteGeneImpact, ImpactOptions.NONE)
        assertImpact((pairM.second as DateTimeGeneImpact).timeGeneImpact.secondGeneImpact, (pairD.second as DateTimeGeneImpact).timeGeneImpact.secondGeneImpact, ImpactOptions.ONLY_IMPACT)
    }


}