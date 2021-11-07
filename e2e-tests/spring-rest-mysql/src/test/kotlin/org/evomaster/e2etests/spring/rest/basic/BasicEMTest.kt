package org.evomaster.e2etests.spring.rest.basic

import com.foo.spring.rest.mysql.basic.BasicController
import org.evomaster.core.problem.rest.HttpVerb
import org.evomaster.core.problem.rest.service.RestSampler
import org.evomaster.core.search.gene.IntegerGene
import org.evomaster.core.search.gene.LongGene
import org.evomaster.e2etests.utils.RestTestBase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class BasicEMTest : RestTestBase() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun initClass() {
            initClass(BasicController())
        }
    }

    @Test
    fun testRunEM() {

        val budget = 100;
        runTestHandlingFlakyAndCompilation(
            "BasicEM",
            "org.bar.mysql.BasicEM",
            budget
        ) { args ->

            val saveExecutedSQLToFile = "target/executionInfo/org/bar/mysql/BasicEM/sql.txt"

            args.add("--outputExecutedSQL")
            args.add("ALL_AT_END")
            args.add("--saveExecutedSQLToFile")
            args.add(saveExecutedSQLToFile)

            val solution = initAndRun(args)

            assertTrue(solution.individuals.size >= 1)

            assertHasAtLeastOne(solution, HttpVerb.GET, 400)
            assertHasAtLeastOne(solution, HttpVerb.GET, 200)

            assertTrue(Files.exists(Paths.get(saveExecutedSQLToFile)))

            /*
                200 status code indicates that the resource is created by evomaster,
                then further check if all INSERT are ignored
             */
            val allSql = Files.readAllLines(Paths.get(saveExecutedSQLToFile))
            // 100 actions + 1 header
            assertEquals(budget + 1, allSql.size)
            val ignoreInitSql = allSql.none { s->
                s.contains("INSERT INTO X")
            }
            assertTrue(ignoreInitSql)
        }
    }

    @Test
    fun testUnsignedNumeric(){

        runTestHandlingFlaky(
            "BasicUnsignedEM",
            "org.bar.mysql.BasicUnsignedEM",
            1,
            false
        ) { args ->

            val injector = init(args)

            val sampler = injector.getInstance(RestSampler::class.java)
            val dbactions = sampler.sampleSqlInsertion("X", setOf("*"))

            assertEquals(1, dbactions.size)

            val genes = dbactions[0].seeGenes()

            val a = genes.find { it.name.equals("a", ignoreCase = true) }
            assertTrue(a is IntegerGene)
            assertEquals(0, (a as IntegerGene).getMin())
            assertEquals(255, a.getMax())
            val b = genes.find { it.name.equals("b", ignoreCase = true) }
            assertTrue(b is LongGene)

            assertEquals(0L, (b as LongGene).getMin())
            assertEquals(4294967295L, b.getMax())
        }

    }
}