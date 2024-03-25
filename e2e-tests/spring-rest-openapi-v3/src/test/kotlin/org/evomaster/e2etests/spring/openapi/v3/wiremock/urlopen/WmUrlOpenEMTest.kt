package org.evomaster.e2etests.spring.openapi.v3.wiremock.urlopen

import com.foo.rest.examples.spring.openapi.v3.wiremock.urlopen.WmUrlOpenController
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

class WmUrlOpenEMTest : SpringTestBase() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun init() {
            val config = EMConfig()
            config.instrumentMR_NET = true
            initClass(WmUrlOpenController(), config)
        }
    }


    @Test
    fun testRunEM() {

        defaultSeed = 123

        runTestHandlingFlakyAndCompilation(
            "WmUrlOpenEM",
            "org.foo.WmUrlOpenEM",
            500,
            true,
            { args: MutableList<String> ->

                args.add("--externalServiceIPSelectionStrategy")
                args.add("USER")
                args.add("--externalServiceIP")
                args.add("127.0.0.92")

                val solution = initAndRun(args)

                assertTrue(solution.individuals.size >= 1)
                assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wm/urlopen/string", "OK")
                assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wm/urlopen/sstring", "OK")

                //if(!CIUtils.isRunningGA()) {
                    //FIXME: this weird... fails on CI, even when incresing budget significantly... but passes local on all OS
                    assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wm/urlopen/object", "OK")
                //}
            },
            3,
        )
    }

}
