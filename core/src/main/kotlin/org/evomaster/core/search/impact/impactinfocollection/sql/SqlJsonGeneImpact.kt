package org.evomaster.core.search.impact.impactinfocollection.sql

import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.gene.sql.SqlJSONGene
import org.evomaster.core.search.impact.impactinfocollection.*
import org.evomaster.core.search.impact.impactinfocollection.value.ObjectGeneImpact

/**
 * created by manzh on 2019-09-29
 */
class SqlJsonGeneImpact(
        sharedImpactInfo: SharedImpactInfo,
        specificImpactInfo: SpecificImpactInfo,
        val geneImpact: ObjectGeneImpact) : GeneImpact(sharedImpactInfo, specificImpactInfo){


    constructor(id : String, sqlJSONGene: SqlJSONGene) : this(SharedImpactInfo(id), SpecificImpactInfo() ,geneImpact = ImpactUtils.createGeneImpact(sqlJSONGene.objectGene, id) as? ObjectGeneImpact?:throw IllegalStateException("geneImpact of SqlJSONImpact should be ObjectGeneImpact"))

    override fun copy(): SqlJsonGeneImpact {
        return SqlJsonGeneImpact(
                shared.copy(), specific.copy(), geneImpact.copy())
    }

    override fun clone(): SqlJsonGeneImpact {
        return SqlJsonGeneImpact(
                shared.clone(),specific.clone(), geneImpact.clone()
        )
    }
    override fun countImpactWithMutatedGeneWithContext(gc: MutatedGeneWithContext, noImpactTargets: Set<Int>, impactTargets: Set<Int>, improvedTargets: Set<Int>, onlyManipulation: Boolean){
        countImpactAndPerformance(noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation, num = gc.numOfMutatedGene)

        if (gc.current !is SqlJSONGene)
            throw IllegalStateException("gc.current (${gc.current::class.java.simpleName}) should be SqlJSONGene")
        if (gc.previous == null && impactTargets.isNotEmpty()) return
        if (gc.previous == null){
            val mutatedGeneWithContext = MutatedGeneWithContext(
                current = gc.current.objectGene,
                previous = null,
                numOfMutatedGene = gc.numOfMutatedGene,
            )
            geneImpact.countImpactWithMutatedGeneWithContext(mutatedGeneWithContext, noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation)
            return
        }
        if ( gc.previous !is SqlJSONGene){
            throw IllegalStateException("gc.previous (${gc.previous::class.java.simpleName}) should be SqlJSONGene")
        }

        val mutatedGeneWithContext = MutatedGeneWithContext(
            current = gc.current.objectGene,
            previous = gc.previous.objectGene,
            numOfMutatedGene = gc.numOfMutatedGene,
        )
        geneImpact.countImpactWithMutatedGeneWithContext(mutatedGeneWithContext, noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation)
    }
    override fun validate(gene: Gene): Boolean = gene is SqlJSONGene

    override fun flatViewInnerImpact(): Map<String, Impact> {
        return mutableMapOf("${getId()}-${geneImpact.getId()}" to geneImpact).plus(geneImpact.flatViewInnerImpact().map { "${getId()}-${it.key}" to it.value })
    }

    override fun innerImpacts(): List<Impact> {
        return listOf(geneImpact)
    }

    override fun syncImpact(previous: Gene?, current: Gene) {
        check(previous,current)
        geneImpact.syncImpact((previous as SqlJSONGene).objectGene, (current as SqlJSONGene).objectGene)
    }
}