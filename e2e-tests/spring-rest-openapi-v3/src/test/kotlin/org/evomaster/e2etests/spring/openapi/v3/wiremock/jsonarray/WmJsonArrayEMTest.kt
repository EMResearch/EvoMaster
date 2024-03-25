package org.evomaster.e2etests.spring.openapi.v3.wiremock.jsonarray

import com.foo.rest.examples.spring.openapi.v3.wiremock.jsonarray.WmJsonArrayController
import org.evomaster.ci.utils.CIUtils

import org.evomaster.core.EMConfig
import org.evomaster.core.problem.rest.HttpVerb
import org.evomaster.e2etests.spring.openapi.v3.SpringTestBase
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 *
 */

class WmJsonArrayEMTest : SpringTestBase() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun init() {
            val config = EMConfig()
            config.instrumentMR_NET = true
            initClass(WmJsonArrayController(), config)
        }
    }


    @Test
    fun testRunEM() {

        runTestHandlingFlakyAndCompilation(
            "WmJsonArrayEM",
            "org.foo.WmJsonArrayEM",
            1000,
            true,
            { args: MutableList<String> ->

                // Note: Test works on local machine without any problems.

                args.add("--externalServiceIPSelectionStrategy")
                args.add("USER")
                args.add("--externalServiceIP")
                args.add("127.0.0.46")

                val solution = initAndRun(args)

                assertTrue(solution.individuals.size >= 1)

                //FIXME same issue as other WM tests... pass locally. Maybe should try again on CircleCI
                assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wm/jsonarray", "OK X")
                assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wm/jsonarray", "OK X and Y")
            },
            3
        )
    }

}
