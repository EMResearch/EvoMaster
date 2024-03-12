package org.evomaster.core.search.impact.impactinfocollection.value

import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.gene.optional.OptionalGene
import org.evomaster.core.search.impact.impactinfocollection.*
import org.evomaster.core.search.impact.impactinfocollection.value.numeric.BinaryGeneImpact

/**
 * created by manzh on 2019-09-09
 */
class OptionalGeneImpact  (
        sharedImpactInfo: SharedImpactInfo,
        specificImpactInfo: SpecificImpactInfo,
        val activeImpact : BinaryGeneImpact,
        val geneImpact: GeneImpact
) : GeneImpact(sharedImpactInfo, specificImpactInfo){

    constructor(
            id : String,
            activeImpact : BinaryGeneImpact = BinaryGeneImpact("isActive"),
            geneImpact: GeneImpact

    ) : this(
            SharedImpactInfo(id),
            SpecificImpactInfo(),
            activeImpact,
            geneImpact
    )

    constructor(id : String, optionalGene: OptionalGene) : this(id, geneImpact = ImpactUtils.createGeneImpact(optionalGene.gene, id))

    override fun copy(): OptionalGeneImpact {
        return OptionalGeneImpact(
                shared.copy(),
                specific.copy(),
                activeImpact = activeImpact.copy(),
                geneImpact = geneImpact.copy() as GeneImpact)
    }

    override fun clone(): OptionalGeneImpact {
        return OptionalGeneImpact(
                shared.clone(),
                specific.clone(),
                activeImpact = activeImpact.clone(),
                geneImpact = geneImpact.clone())
    }

    override fun countImpactWithMutatedGeneWithContext(gc: MutatedGeneWithContext, noImpactTargets: Set<Int>, impactTargets: Set<Int>, improvedTargets: Set<Int>, onlyManipulation: Boolean) {
        countImpactAndPerformance(noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation, num = gc.numOfMutatedGene)

        if (gc.current !is OptionalGene)
            throw IllegalStateException("gc.current(${gc.current::class.java.simpleName}) should be OptionalGene")

        if (gc.previous != null && gc.previous !is OptionalGene)
            throw IllegalStateException("gc.pervious (${gc.previous::class.java.simpleName}) should be OptionalGene")

        if (gc.previous == null || (gc.previous as OptionalGene).isActive != gc.current.isActive){
            activeImpact.countImpactAndPerformance(noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation, num = gc.numOfMutatedGene)
            if (gc.current.isActive)
                activeImpact.trueValue.countImpactAndPerformance(noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation, num = 1)
            else
                activeImpact.falseValue.countImpactAndPerformance(noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation, num = 1)

            if (gc.previous != null){
                return
            }
        }

        if (gc.previous == null && impactTargets.isNotEmpty()) return

        if (gc.current.isActive){
            val mutatedGeneWithContext = MutatedGeneWithContext(
                current = gc.current.gene,
                previous = if (gc.previous==null) null else (gc.previous as OptionalGene).gene,
                numOfMutatedGene = gc.numOfMutatedGene,
            )
            geneImpact.countImpactWithMutatedGeneWithContext(mutatedGeneWithContext, noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation)
        }

    }


    override fun validate(gene: Gene): Boolean = gene is OptionalGene

    override fun syncImpact(previous: Gene?, current: Gene) {
        check(previous, current)
        geneImpact.syncImpact((previous as? OptionalGene)?.gene, (current as OptionalGene).gene)
    }

    override fun flatViewInnerImpact(): Map<String, Impact> {
        return mutableMapOf("${getId()}-${geneImpact.getId()}" to geneImpact)
                .plus("${getId()}-${activeImpact.getId()}" to activeImpact)
                .plus(activeImpact.flatViewInnerImpact().plus(geneImpact.flatViewInnerImpact()).map { "${getId()}-${it.key}" to it.value })
    }

    override fun innerImpacts(): List<Impact> {
        return listOf(activeImpact, geneImpact)
    }

}