package org.evomaster.core.search.impact.impactinfocollection.collection

import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.gene.IntegerGene
import org.evomaster.core.search.gene.MapGene
import org.evomaster.core.search.impact.impactinfocollection.GeneImpact
import org.evomaster.core.search.impact.impactinfocollection.GeneImpactTest
import org.evomaster.core.search.impact.impactinfocollection.ImpactOptions
import org.evomaster.core.search.impact.impactinfocollection.MutatedGeneWithContext
import org.evomaster.core.search.impact.impactinfocollection.value.collection.MapGeneImpact
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * created by manzh on 2019-10-10
 */
class MapGeneImpactTest : GeneImpactTest(){
    val map = listOf(1,2,3).map { IntegerGene(it.toString(), value = it) }.toMutableList()
    var counter = 3

    fun generateKey() :Int{
        counter += 1
        return counter
    }
    override fun getGene(): Gene {
        return MapGene("map", template = map.first(), elements = map)
    }

    override fun checkImpactType(impact: GeneImpact) {
        assert(impact is MapGeneImpact)
    }

    override fun simulateMutation(original: Gene, geneToMutate: Gene, mutationTag: Int): MutatedGeneWithContext {
        geneToMutate as MapGene<IntegerGene>

        val p = Random.nextBoolean()

        when{
            mutationTag == 1 || (mutationTag == 0 && p)->{
                val index = Random.nextInt(0, geneToMutate.getAllElements().size)
                geneToMutate.getAllElements()[index].apply {
                    value += if (value + 1> getMax()) -1 else 1
                }
            }
            mutationTag == 2 || (mutationTag == 0 && !p)->{
                if (geneToMutate.getAllElements().size + 1 > geneToMutate.maxSize)
                    geneToMutate.getAllElements().removeAt(0)
                else{
                    val key = generateKey()
                    geneToMutate.getAllElements().add(IntegerGene(key.toString(), key))
                }
            }
        }
        return MutatedGeneWithContext(current = geneToMutate, previous = original)
    }

    @Test
    fun testSize(){
        val gene = getGene()
        val impact = initImpact(gene)
        val pair = template(gene, impact, listOf(ImpactOptions.NO_IMPACT), 1)

        assertImpact(impact, pair.second, ImpactOptions.NO_IMPACT)
        assertImpact((impact as MapGeneImpact).sizeImpact, (pair.second as MapGeneImpact).sizeImpact, ImpactOptions.NONE)

        val pairS = template(pair.first, pair.second, listOf(ImpactOptions.IMPACT_IMPROVEMENT), 2)
        assertImpact(pair.second, pairS.second, ImpactOptions.IMPACT_IMPROVEMENT)
        assertImpact((pair.second as MapGeneImpact).sizeImpact, (pairS.second as MapGeneImpact).sizeImpact, ImpactOptions.IMPACT_IMPROVEMENT)
    }
}