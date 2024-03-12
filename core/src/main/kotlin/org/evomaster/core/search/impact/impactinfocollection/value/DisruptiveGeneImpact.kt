package org.evomaster.core.search.impact.impactinfocollection.value

import org.evomaster.core.search.gene.optional.CustomMutationRateGene
import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.impact.impactinfocollection.*

/**
 * created by manzh on 2019-09-09
 */
class DisruptiveGeneImpact (
        sharedImpactInfo: SharedImpactInfo,
        specificImpactInfo: SpecificImpactInfo,
        val geneImpact: GeneImpact
) : GeneImpact(sharedImpactInfo, specificImpactInfo){

    constructor(
            id : String,
            geneImpact: GeneImpact

    ) : this(
            SharedImpactInfo(id),
            SpecificImpactInfo(),
            geneImpact
    )

    constructor(id : String, gene: CustomMutationRateGene<*>) : this(id, geneImpact = ImpactUtils.createGeneImpact(gene.gene, id))


    override fun copy(): DisruptiveGeneImpact {
        return DisruptiveGeneImpact(
                shared.copy(),
                specific.copy(),
                geneImpact = geneImpact.copy())
    }

    override fun clone(): DisruptiveGeneImpact {
        return DisruptiveGeneImpact(
                shared,
                specific.copy(),
                geneImpact = geneImpact.clone())
    }

    override fun validate(gene: Gene): Boolean = gene is CustomMutationRateGene<*>

    override fun countImpactWithMutatedGeneWithContext(gc: MutatedGeneWithContext, noImpactTargets : Set<Int>, impactTargets: Set<Int>, improvedTargets: Set<Int>, onlyManipulation: Boolean) {
        countImpactAndPerformance(noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation, num = gc.numOfMutatedGene)

        if (gc.previous == null && impactTargets.isNotEmpty()) return
        if (gc.current  !is CustomMutationRateGene<*>){
            throw IllegalStateException("gc.current (${gc.current::class.java.simpleName}) should be DisruptiveGene")
        }
        if (gc.previous != null && gc.previous !is CustomMutationRateGene<*>){
            throw IllegalStateException("gc.previous (${gc.previous::class.java.simpleName}) should be DisruptiveGene")
        }

        val mutatedGeneWithContext = MutatedGeneWithContext(
            current = gc.current.gene,
            previous = if (gc.previous==null) null else (gc.previous as CustomMutationRateGene<*>).gene,
            numOfMutatedGene = gc.numOfMutatedGene,
        )
        geneImpact.countImpactWithMutatedGeneWithContext(mutatedGeneWithContext, noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation)
    }

    override fun flatViewInnerImpact(): Map<String, Impact> {
        return mutableMapOf("${getId()}-${geneImpact.getId()}" to geneImpact)
                .plus(geneImpact.flatViewInnerImpact().map { "${getId()}-${it.key}" to it.value })
    }

    override fun innerImpacts(): List<Impact> {
        return listOf(geneImpact)
    }

    override fun syncImpact(previous: Gene?, current: Gene) {
        check(previous,current)
        geneImpact.syncImpact((previous as CustomMutationRateGene<*>).gene, (current as CustomMutationRateGene<*>).gene)
    }
}