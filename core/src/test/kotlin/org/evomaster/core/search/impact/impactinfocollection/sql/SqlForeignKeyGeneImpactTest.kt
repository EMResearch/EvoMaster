package org.evomaster.core.search.impact.impactinfocollection.sql

import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.gene.sql.SqlForeignKeyGene
import org.evomaster.core.search.impact.impactinfocollection.GeneImpact
import org.evomaster.core.search.impact.impactinfocollection.GeneImpactTest
import org.evomaster.core.search.impact.impactinfocollection.MutatedGeneWithContext

/**
 * created by manzh on 2019-10-08
 */
class SqlForeignKeyGeneImpactTest : GeneImpactTest() {

    override fun simulateMutation(original: Gene, geneToMutate: Gene, mutationTag: Int): MutatedGeneWithContext {
        geneToMutate as SqlForeignKeyGene

        if (geneToMutate.uniqueIdOfPrimaryKey + 1L > Long.MAX_VALUE)
            geneToMutate.uniqueIdOfPrimaryKey -= 1L
        else
            geneToMutate.uniqueIdOfPrimaryKey += 1L

        return MutatedGeneWithContext(current = geneToMutate, previous = original,)
    }

    override fun getGene(): Gene = SqlForeignKeyGene(
            sourceColumn = "source",
            uniqueId = 1L,
            targetTable = "fake",
            nullable = false,
            uniqueIdOfPrimaryKey = 1L
    )

    override fun checkImpactType(impact: GeneImpact) {
        assert(impact is SqlForeignKeyGeneImpact)
    }

}