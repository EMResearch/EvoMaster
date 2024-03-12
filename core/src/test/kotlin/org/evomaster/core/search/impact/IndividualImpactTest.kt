package org.evomaster.core.search.impact

import org.evomaster.core.TestUtils
import org.evomaster.core.problem.enterprise.SampleType
import org.evomaster.core.problem.rest.RestIndividual
import org.evomaster.core.search.impact.impactinfocollection.ImpactsOfIndividual
import org.evomaster.core.sql.SqlAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * check init and update impact of individual
 */
class IndividualImpactTest {


    private fun generateFkIndividual() : RestIndividual{
        val twoDbActions = TestUtils.generateTwoFakeDbActions(1001L, 1002L, 12345L, 10L, "Foo", "Bar", 0, 42)


        val fooAction = TestUtils.generateFakeQueryRestAction("1", "/foo")
        val barAction = TestUtils.generateFakeQueryRestAction("2", "/bar", true)


        return RestIndividual(mutableListOf(fooAction, barAction), SampleType.RANDOM, twoDbActions.toMutableList())
    }

    @Test
    fun testRestIndividualImpactInit(){

        val ind = generateFkIndividual()

        val impactInfo = ImpactsOfIndividual(ind, listOf(SqlAction::class.java.name),false, null)

        impactInfo.fixedMainActionImpacts.apply {
            assertEquals(2, size)
            assertEquals(2, this[0].geneImpacts.size)
            assertEquals(1, this[1].geneImpacts.size)
        }
    }
}
