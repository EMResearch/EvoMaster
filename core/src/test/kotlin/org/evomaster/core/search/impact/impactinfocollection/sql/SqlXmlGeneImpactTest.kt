package org.evomaster.core.search.impact.impactinfocollection.sql

import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.gene.numeric.IntegerGene
import org.evomaster.core.search.gene.ObjectGene
import org.evomaster.core.search.gene.string.StringGene
import org.evomaster.core.search.gene.sql.SqlXMLGene
import org.evomaster.core.search.impact.impactinfocollection.GeneImpact
import org.evomaster.core.search.impact.impactinfocollection.GeneImpactTest
import org.evomaster.core.search.impact.impactinfocollection.ImpactOptions
import org.evomaster.core.search.impact.impactinfocollection.MutatedGeneWithContext
import org.junit.jupiter.api.Test

/**
 * created by manzh on 2019-10-10
 */
class SqlXmlGeneImpactTest : GeneImpactTest() {

    val f1 = "f1"
    val f2 = "f2"

    override fun getGene(): Gene {
        val f1 = StringGene(f1, "string1")
        val f2 = IntegerGene(f2, 2)

        val obj = ObjectGene("obj", listOf(f1, f2))

        return SqlXMLGene("xml", obj)
    }

    override fun checkImpactType(impact: GeneImpact) {
        assert(impact is SqlXmlGeneImpact)
    }

    override fun simulateMutation(original: Gene, geneToMutate: Gene, mutationTag: Int): MutatedGeneWithContext {
        geneToMutate as SqlXMLGene
        val gene = geneToMutate.objectGene

        assert(gene.fields.first() is StringGene)
        val f1 = gene.fields.first() as StringGene
        assert(gene.fields.last() is IntegerGene)
        val f2 = gene.fields.last() as IntegerGene

        val p = Math.random() < 0.5

        when{
            mutationTag == 1 || (mutationTag == 0 && p) -> {
                if (f1.value.length + 1 > f1.maxLength){
                    f1.value = f1.value.dropLast(1)
                }else
                    f1.value = f1.value + "a"
            }
            mutationTag == 2 || (mutationTag == 0 && !p)-> {
                if (f2.value + 1 > f2.getMaximum())
                    f2.value -= 1
                else
                    f2.value += 1
            }
            else -> TODO()
        }

        return MutatedGeneWithContext(current = geneToMutate, previous = original,)
    }

    @Test
    fun testfields(){
        val gene = getGene()
        var impact = (initImpact(gene) as SqlXmlGeneImpact)

        val pairf1 = template(gene, impact, listOf(ImpactOptions.IMPACT_IMPROVEMENT), 1)

        var updateImpact = (pairf1.second as SqlXmlGeneImpact)

        assertImpact(impact, updateImpact, ImpactOptions.IMPACT_IMPROVEMENT)
        assertImpact(impact.geneImpact, updateImpact.geneImpact, ImpactOptions.IMPACT_IMPROVEMENT)

        assertImpact(impact.geneImpact.fixedFields.getValue(f1), updateImpact.geneImpact.fixedFields.getValue(f1), ImpactOptions.IMPACT_IMPROVEMENT)
        assertImpact(impact.geneImpact.fixedFields.getValue(f2), updateImpact.geneImpact.fixedFields.getValue(f2), ImpactOptions.NONE)

        impact = updateImpact

        val pairf2 = template(gene, impact, listOf(ImpactOptions.NO_IMPACT), 2)
        updateImpact = (pairf2.second as SqlXmlGeneImpact)

        assertImpact(impact, updateImpact, ImpactOptions.NO_IMPACT)
        assertImpact(impact.geneImpact, updateImpact.geneImpact, ImpactOptions.NO_IMPACT)
        assertImpact(impact.geneImpact.fixedFields.getValue(f1), updateImpact.geneImpact.fixedFields.getValue(f1), ImpactOptions.NONE)
        assertImpact(impact.geneImpact.fixedFields.getValue(f2), updateImpact.geneImpact.fixedFields.getValue(f2), ImpactOptions.NO_IMPACT)

    }
}