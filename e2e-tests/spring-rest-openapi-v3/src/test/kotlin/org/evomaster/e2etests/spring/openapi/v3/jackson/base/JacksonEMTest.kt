package org.evomaster.e2etests.spring.openapi.v3.jackson.base

import com.foo.rest.examples.spring.openapi.v3.jackson.base.JacksonController
import org.evomaster.ci.utils.CIUtils
import org.evomaster.core.problem.rest.HttpVerb
import org.evomaster.e2etests.spring.openapi.v3.SpringTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class JacksonEMTest : SpringTestBase() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun init() {
            initClass(JacksonController())
            /*
                TODO for some weird reason, this fails on CI, although it pass on different
                 Mac and Windows machines locally.
                Could be an issue with Linux or used JDK
             */
            //CIUtils.skipIfOnGA()
        }
    }

    @Test
    fun basicEMTest() {

        //CIUtils.skipIfOnGA()

        runTestHandlingFlakyAndCompilation(
            "JacksonGenericEM",
            "org.foo.JacksonGenericEM",
            1000
        ) { args: List<String> ->

            val solution = initAndRun(args)

            Assertions.assertTrue(solution.individuals.size >= 1)
            assertHasAtLeastOne(solution, HttpVerb.POST, 200, "/api/jackson/generic", "Working")
        }
    }
}