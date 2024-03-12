package org.evomaster.core.search.impact.impactinfocollection.sql

import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.gene.optional.NullableGene
import org.evomaster.core.search.impact.impactinfocollection.*
import org.evomaster.core.search.impact.impactinfocollection.value.numeric.BinaryGeneImpact

/**
 * created by manzh on 2019-09-29
 */
class NullableImpact(sharedImpactInfo: SharedImpactInfo, specificImpactInfo: SpecificImpactInfo,
                     val presentImpact : BinaryGeneImpact = BinaryGeneImpact("isPresent"),
                     val geneImpact: GeneImpact) : GeneImpact(sharedImpactInfo, specificImpactInfo){


    constructor(id : String, sqlnullGene: NullableGene) : this(SharedImpactInfo(id), SpecificImpactInfo(), geneImpact = ImpactUtils.createGeneImpact(sqlnullGene.gene, id))

    override fun copy(): NullableImpact {
        return NullableImpact(
                shared.copy(),
                specific.copy(),
                presentImpact = presentImpact.copy(),
                geneImpact = geneImpact.copy())
    }

    override fun clone(): NullableImpact {
        return NullableImpact(
                shared.clone(),specific.clone(), presentImpact.clone(), geneImpact.clone()
        )
    }

    override fun countImpactWithMutatedGeneWithContext(gc: MutatedGeneWithContext,noImpactTargets : Set<Int>, impactTargets: Set<Int>, improvedTargets: Set<Int>, onlyManipulation: Boolean) {
        countImpactAndPerformance(noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation, num = gc.numOfMutatedGene)

        if (gc.current  !is NullableGene){
            throw IllegalStateException("gc.current (${gc.current::class.java.simpleName}) should be SqlNullable")
        }
        if (gc.previous != null && gc.previous !is NullableGene){
            throw IllegalStateException("gc.previous (${gc.previous::class.java.simpleName}) should be SqlNullable")
        }

        if (gc.previous == null || (gc.previous as NullableGene).isActive != gc.current.isActive){
            presentImpact.countImpactAndPerformance(noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation, num = gc.numOfMutatedGene)

            if (gc.current.isActive)
                presentImpact.trueValue.countImpactAndPerformance(noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation, num = 1)
            else
                presentImpact.falseValue.countImpactAndPerformance(noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation, num = 1)

            if (gc.previous != null) {
                return
            }
        }

        if (gc.previous == null && impactTargets.isNotEmpty()) return

        if (gc.current.isActive){
            val mutatedGeneWithContext = MutatedGeneWithContext(
                current = gc.current.gene,
                previous = if (gc.previous == null) null else (gc.previous as NullableGene).gene,
                numOfMutatedGene = gc.numOfMutatedGene,
            )
            geneImpact.countImpactWithMutatedGeneWithContext(mutatedGeneWithContext, noImpactTargets= noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation)
        }
    }

    override fun validate(gene: Gene): Boolean = gene is NullableGene

    override fun flatViewInnerImpact(): Map<String, Impact> {
        return mutableMapOf("${getId()}-${geneImpact.getId()}" to geneImpact)
                .plus("${getId()}-${presentImpact.getId()}" to presentImpact)
                .plus(presentImpact.flatViewInnerImpact().plus(geneImpact.flatViewInnerImpact()).map { "${getId()}-${it.key}" to it.value })
    }

    override fun innerImpacts(): List<Impact> {
        return listOf(presentImpact, geneImpact)
    }

    override fun syncImpact(previous: Gene?, current: Gene) {
        check(previous,current)
        geneImpact.syncImpact((previous as NullableGene).gene, (current as NullableGene).gene)
    }
}