package org.evomaster.core.search.impact.impactinfocollection

import org.evomaster.core.search.gene.datetime.DateGene
import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.gene.string.StringGene
import org.evomaster.core.search.impact.impactinfocollection.value.StringGeneImpact
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * created by manzh on 2020-07-10
 */
class StringSpecializationImpactTest : GeneImpactTest() {
    override fun getGene(): Gene {
        return StringGene("s","hello").also {
            it.selectedSpecialization = -1
        }
    }

    override fun checkImpactType(impact: GeneImpact) {
        assert(impact is StringGeneImpact)
    }

    /**
     * @param mutationTag indicates selected specialization
     */
    override fun simulateMutation(original: Gene, geneToMutate: Gene, mutationTag: Int): MutatedGeneWithContext {
        geneToMutate as StringGene
        if (mutationTag == 0){
            geneToMutate.addChild(DateGene("s").apply { doInitialize() })
            geneToMutate.selectedSpecialization = geneToMutate.specializationGenes.lastIndex
        }else{
            val selected = if (mutationTag > 0) mutationTag - 1 else mutationTag
            if (selected >= geneToMutate.specializationGenes.size){
                geneToMutate.addChild(DateGene("s"))
                geneToMutate.selectedSpecialization = geneToMutate.specializationGenes.lastIndex
            }else{
                if(geneToMutate.selectedSpecialization == selected){
                    throw IllegalStateException("nothing to mutate")
                }else{
                    geneToMutate.selectedSpecialization = selected
                }
            }
        }

        return MutatedGeneWithContext(current = geneToMutate, previous = original,)
    }

    @Test
    fun testSpecialization(){
        val g0 = getGene()
        val impactG0 = initImpact(g0) as StringGeneImpact
        assertNull(impactG0.hierarchySpecializationImpactInfo)

        // g0 -> g1
        val m1 = template(g0, impactG0, listOf(ImpactOptions.IMPACT_IMPROVEMENT), 0)
        (m1.second as StringGeneImpact).syncImpact(g0, m1.first)

        assertNotNull((m1.second as StringGeneImpact).hierarchySpecializationImpactInfo)
        assertImpact(impactG0, m1.second, ImpactOptions.IMPACT_IMPROVEMENT)
        assertImpact(impactG0.employSpecialization, (m1.second as StringGeneImpact).employSpecialization, ImpactOptions.IMPACT_IMPROVEMENT)

        // g1 -> g2
        val m2 = template(m1.first, m1.second, listOf(ImpactOptions.ONLY_IMPACT), 0)
        (m2.second as StringGeneImpact).syncImpact(m1.first, m2.first)
        assertEquals(2, (m2.second as StringGeneImpact).hierarchySpecializationImpactInfo!!.flattenImpacts().size)

        assertImpact(m1.second, m2.second, ImpactOptions.ONLY_IMPACT)
        assertImpact((m1.second as StringGeneImpact).employSpecialization, (m2.second as StringGeneImpact).employSpecialization, ImpactOptions.ONLY_IMPACT)

        // g1 -> g3
        val m3 = template(m1.first, m1.second, listOf(ImpactOptions.NO_IMPACT), 0)
        (m3.second as StringGeneImpact).syncImpact(m1.first, m3.first)
        assertEquals(2, (m3.second as StringGeneImpact).hierarchySpecializationImpactInfo!!.flattenImpacts().size)

        assertImpact(m1.second, m3.second, ImpactOptions.NO_IMPACT)
        assertImpact((m1.second as StringGeneImpact).employSpecialization, (m3.second as StringGeneImpact).employSpecialization, ImpactOptions.NO_IMPACT)

        assertNotNull((m2.second as StringGeneImpact).hierarchySpecializationImpactInfo)
        assertNotNull((m3.second as StringGeneImpact).hierarchySpecializationImpactInfo)
        assertEquals((m2.second as StringGeneImpact).hierarchySpecializationImpactInfo?.parent, (m3.second as StringGeneImpact).hierarchySpecializationImpactInfo?.parent)
    }
}