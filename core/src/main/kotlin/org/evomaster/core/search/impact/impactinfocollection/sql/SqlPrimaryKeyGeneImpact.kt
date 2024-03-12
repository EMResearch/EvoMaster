package org.evomaster.core.search.impact.impactinfocollection.sql

import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.gene.sql.SqlPrimaryKeyGene
import org.evomaster.core.search.impact.impactinfocollection.*

/**
 * created by manzh on 2019-09-29
 */
class SqlPrimaryKeyGeneImpact (sharedImpactInfo: SharedImpactInfo, specificImpactInfo: SpecificImpactInfo,
                               val geneImpact: GeneImpact) : GeneImpact(sharedImpactInfo, specificImpactInfo){

    constructor(id : String, sqlPrimaryKeyGene: SqlPrimaryKeyGene) :
            this(SharedImpactInfo(id), SpecificImpactInfo(), geneImpact = ImpactUtils.createGeneImpact(sqlPrimaryKeyGene.gene, id))

    override fun copy(): SqlPrimaryKeyGeneImpact {
        return SqlPrimaryKeyGeneImpact(
                shared.copy(),
                specific.copy(),
                geneImpact = geneImpact.copy())
    }

    override fun clone(): SqlPrimaryKeyGeneImpact {
        return SqlPrimaryKeyGeneImpact(
                shared.clone(),
                specific.clone(),
                geneImpact.clone()
        )
    }

    override fun validate(gene: Gene): Boolean = gene is SqlPrimaryKeyGene

    override fun flatViewInnerImpact(): Map<String, Impact> {
        return mutableMapOf(
                "${getId()}-${geneImpact.getId()}" to geneImpact
        ).plus(geneImpact.flatViewInnerImpact().map { "${getId()}-${it.key}" to it.value })
    }

    override fun countImpactWithMutatedGeneWithContext(gc: MutatedGeneWithContext, noImpactTargets : Set<Int>, impactTargets: Set<Int>, improvedTargets: Set<Int>, onlyManipulation: Boolean) {
        countImpactAndPerformance(noImpactTargets = noImpactTargets, impactTargets = impactTargets, improvedTargets = improvedTargets, onlyManipulation = onlyManipulation, num = gc.numOfMutatedGene)

        if (gc.previous == null && impactTargets.isNotEmpty()) return
        if (gc.current  !is SqlPrimaryKeyGene)
            throw IllegalStateException("gc.current (${gc.current::class.java.simpleName}) should be SqlPrimaryKeyGene")

        if (gc.previous != null && gc.previous !is SqlPrimaryKeyGene)
            throw IllegalStateException("gc.previous (${gc.previous::class.java.simpleName}) should be SqlPrimaryKeyGene")

        val mutatedGeneWithContext = MutatedGeneWithContext(
            current = gc.current.gene,
            previous = if (gc.previous==null) null else (gc.previous as SqlPrimaryKeyGene).gene,
            numOfMutatedGene = gc.numOfMutatedGene,
        )
        geneImpact.countImpactWithMutatedGeneWithContext(
                mutatedGeneWithContext,
                noImpactTargets =noImpactTargets,
                impactTargets = impactTargets,
                improvedTargets = improvedTargets,
                onlyManipulation = onlyManipulation)
    }

    override fun innerImpacts(): List<Impact> {
        return listOf(geneImpact)
    }

    override fun syncImpact(previous: Gene?, current: Gene) {
        check(previous,current)
        geneImpact.syncImpact((previous as SqlPrimaryKeyGene).gene, (current as SqlPrimaryKeyGene).gene)
    }
}