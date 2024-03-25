package org.evomaster.e2etests.spring.openapi.v3.wiremock.jsonmap

import com.foo.rest.examples.spring.openapi.v3.wiremock.jsonmap.WmJsonMapController
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

class WmJsonMapEMTest : SpringTestBase() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun init() {
            val config = EMConfig()
            config.instrumentMR_NET = true
            initClass(WmJsonMapController(), config)
        }
    }


    @Test
    fun testRunEM() {

        runTestHandlingFlakyAndCompilation(
            "WmJsonMapEM",
            "org.foo.WmJsonMapEM",
            1000,
            !CIUtils.isRunningGA(), //there exist bug in generated tests for map
            { args: MutableList<String> ->

                args.add("--externalServiceIPSelectionStrategy")
                args.add("USER")
                args.add("--externalServiceIP")
                args.add("127.0.0.80")

                val solution = initAndRun(args)

                assertTrue(solution.individuals.size >= 1)

                if (!CIUtils.isRunningGA()) {
                    assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wm/jsonmap/gson", "not empty map and include")
                    //assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wm/jsonmap/gson", "foo42") // not solved yet
                    //assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wm/jsonmap/gson", "bar54") // not solved yet
                    assertHasAtLeastOne(solution, HttpVerb.GET, 404, "/api/wm/jsonmap/gson", "empty map")
                    assertHasAtLeastOne(solution, HttpVerb.GET, 500, "/api/wm/jsonmap/gson", "")
                }
            },
            3
        )
    }

}
