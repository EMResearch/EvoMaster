package org.evomaster.core.search.impact.impactinfocollection.value

import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.gene.collection.TupleGene
import org.evomaster.core.search.impact.impactinfocollection.*

class TupleGeneImpact (
    sharedImpactInfo: SharedImpactInfo,
    specificImpactInfo: SpecificImpactInfo,
    val elements : MutableMap<String, Impact> = mutableMapOf()
) : GeneImpact(sharedImpactInfo, specificImpactInfo){

    constructor(id: String, tupleGene: TupleGene) : this (SharedImpactInfo(id), SpecificImpactInfo(), elements = tupleGene.elements.associate {
        Pair(
            it.name,
            ImpactUtils.createGeneImpact(it, it.name)
        )
    }.toMutableMap())

    override fun copy(): TupleGeneImpact {
        return TupleGeneImpact(
            shared.copy(),
            specific.copy(),
            elements = elements.map { Pair(it.key, it.value.copy()) }.toMap().toMutableMap())
    }

    override fun clone(): TupleGeneImpact {
        return TupleGeneImpact(
            shared.clone(),
            specific.clone(),
            elements.map { it.key to it.value.clone() }.toMap().toMutableMap()
        )
    }

    override fun countImpactWithMutatedGeneWithContext(gc: MutatedGeneWithContext, noImpactTargets: Set<Int>, impactTargets: Set<Int>, improvedTargets: Set<Int>, onlyManipulation: Boolean) {

        countImpactAndPerformance(noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation, num = gc.numOfMutatedGene)
        if (gc.previous == null && impactTargets.isNotEmpty()) return
        if (gc.current !is TupleGene)
            throw IllegalArgumentException("gc.current ${gc.current::class.java.simpleName} should be ObjectGene")
        if (gc.previous == null){
            gc.current.elements.forEach { gene ->
                val fImpact = elements.getValue(gene.name) as? GeneImpact ?:throw IllegalArgumentException("impact should be gene impact")
                val mutatedGeneWithContext = MutatedGeneWithContext(
                    current =  gene,
                    actionName = "none",
                    position = -1,
                    previous = null,
                    numOfMutatedGene = gc.current.elements.size,
                )
                fImpact.countImpactWithMutatedGeneWithContext(mutatedGeneWithContext, noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation)
            }
            return
        }
        if (gc.previous !is TupleGene)
            throw IllegalArgumentException("gc.previous ${gc.previous::class.java.simpleName} should be ObjectGene")

        val mutatedElements = gc.current.elements.zip(gc.previous.elements) { cf, pf ->
            Pair(Pair(cf, pf), cf.containsSameValueAs(pf))
        }.filter { !it.second }.map { it.first }

        val onlyManipulation = mutatedElements.size > 1 && impactTargets.isNotEmpty()

        mutatedElements.forEach { g->
            val fImpact = elements.getValue(g.first.name) as? GeneImpact ?:throw IllegalArgumentException("impact should be gene impact")
            val mutatedGeneWithContext = MutatedGeneWithContext(
                current =  g.first,
                actionName = "none",
                position = -1,
                previous = g.second,
                numOfMutatedGene = gc.numOfMutatedGene * mutatedElements.size,
            )
            fImpact.countImpactWithMutatedGeneWithContext(mutatedGeneWithContext, noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation)
        }
    }

    override fun validate(gene: Gene): Boolean = gene is TupleGene

    override fun flatViewInnerImpact(): Map<String, Impact> {
        val map = mutableMapOf<String, Impact>()
        elements.forEach { (t, u) ->
            map.putIfAbsent("${getId()}-$t", u)
            if (u is GeneImpact && u.flatViewInnerImpact().isNotEmpty())
                map.putAll(u.flatViewInnerImpact())
        }
        return map
    }

    override fun innerImpacts(): List<Impact> {
        return elements.values.toList()
    }
}