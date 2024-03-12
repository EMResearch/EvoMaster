package org.evomaster.core.search.impact.impactinfocollection.value.numeric

import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.gene.string.NumericStringGene
import org.evomaster.core.search.impact.impactinfocollection.*


class NumericStringGeneImpact (
    sharedImpactInfo: SharedImpactInfo,
    specificImpactInfo: SpecificImpactInfo,
    val numberGeneImpact: BigDecimalGeneImpact
    ) : GeneImpact(sharedImpactInfo, specificImpactInfo){

    constructor(
            id : String,
            gene: NumericStringGene
    ) : this(SharedImpactInfo(id), SpecificImpactInfo(), numberGeneImpact = ImpactUtils.createGeneImpact(gene.number, id) as BigDecimalGeneImpact)

    override fun copy(): NumericStringGeneImpact {
        return NumericStringGeneImpact(
                shared.copy(), specific.copy(), numberGeneImpact.copy())
    }

    override fun clone(): NumericStringGeneImpact {
        return NumericStringGeneImpact(
                shared.clone(), specific.clone(), numberGeneImpact.clone())
    }

    override fun validate(gene: Gene): Boolean = gene is NumericStringGene


    override fun countImpactWithMutatedGeneWithContext(gc: MutatedGeneWithContext, noImpactTargets: Set<Int>, impactTargets: Set<Int>, improvedTargets: Set<Int>, onlyManipulation: Boolean) {

        countImpactAndPerformance(noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation, num = gc.numOfMutatedGene)

        if (gc.previous == null && impactTargets.isNotEmpty()) return
        if (gc.current !is NumericStringGene)
            throw IllegalStateException("gc.current(${gc.current::class.java.simpleName}) should be NumericStringGene")

        if (gc.previous != null && gc.previous !is NumericStringGene)
            throw IllegalStateException("gc.pervious (${gc.previous::class.java.simpleName}) should be NumericStringGene")


        val mutatedGeneWithContext = MutatedGeneWithContext(
            current = gc.current.number,
            previous = if (gc.previous==null) null else (gc.previous as NumericStringGene).number,
            numOfMutatedGene = gc.numOfMutatedGene,
        )
        numberGeneImpact.countImpactWithMutatedGeneWithContext(mutatedGeneWithContext, noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation)


    }

    override fun syncImpact(previous: Gene?, current: Gene) {
        check(previous, current)
        numberGeneImpact.syncImpact((previous as? NumericStringGene)?.number, (current as NumericStringGene).number)
    }

    override fun flatViewInnerImpact(): Map<String, Impact> {
        return mutableMapOf("${getId()}-${numberGeneImpact.getId()}" to numberGeneImpact)
            .plus(numberGeneImpact.flatViewInnerImpact().map { "${getId()}-${it.key}" to it.value })
    }

    override fun innerImpacts(): List<Impact> {
        return listOf(numberGeneImpact)
    }
}