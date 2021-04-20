package org.evomaster.e2etests.spring.graphql.db.base

import com.foo.graphql.db.base.DbBaseController
import org.evomaster.core.EMConfig
import org.evomaster.core.problem.graphql.GQMethodType
import org.evomaster.e2etests.spring.graphql.SpringTestBase
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class DbBaseEMTest : SpringTestBase() {


    companion object {
        @BeforeAll
        @JvmStatic
        fun init() {
            initClass(DbBaseController())
        }
    }

    @Test
    fun testRunEM() {
        runTestHandlingFlakyAndCompilation(
            "GQL_DbBaseEM",
            "org.foo.graphql.DbBaseEM",
            10000
        ) { args: MutableList<String> ->

            args.add("--problemType")
            args.add(EMConfig.ProblemType.GRAPHQL.toString())

            args.add("--heuristicsForSQL")
            args.add("true")
            args.add("--generateSqlDataWithSearch")
            args.add("false")
            args.add("--enableBasicAssertions")
            args.add("false")

            val solution = initAndRun(args)

            assertTrue(solution.individuals.size >= 1)
            assertHasAtLeastOne(solution, "dbBaseByName", GQMethodType.QUERY, 200, "{\"id\":\"42\",\"name\":\"foo\"}")
            assertNoneWithErrors(solution)
        }
    }
}