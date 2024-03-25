package org.evomaster.e2etests.spring.openapi.v3.wiremock.auth0

import com.foo.rest.examples.spring.openapi.v3.wiremock.auth0.WmAuth0Controller
import com.foo.rest.examples.spring.openapi.v3.wiremock.okhttp.WmOkHttpController
import org.evomaster.ci.utils.CIUtils
import org.evomaster.core.EMConfig
import org.evomaster.core.problem.rest.HttpVerb
import org.evomaster.e2etests.spring.openapi.v3.SpringTestBase
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test


class WmAuth0EMTest : SpringTestBase() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun init() {

            val config = EMConfig()
            config.instrumentMR_NET = true
            initClass(WmAuth0Controller(), config)

            /*
            The test fails on CI, but not local with WM 2.32.0

            if updating WM to 2.34.0, the test fails on local windows as well (TO CHECK)
            */
           // CIUtils.skipIfOnGA()
        }
    }


    @Disabled("Disabled temporarily")
    fun testRunEM() {

        runTestHandlingFlakyAndCompilation(
            "WmAuth0EM",
            "org.foo.WmAuth0EM",
            500,
            true,
            { args: MutableList<String> ->

                args.add("--externalServiceIPSelectionStrategy")
                args.add("USER")
                args.add("--externalServiceIP")
                args.add("127.3.0.12")
                args.add("--instrumentMR_NET")
                args.add("true")

                val solution = initAndRun(args)

                assertTrue(solution.individuals.size >= 1)
                assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wm/auth0", "OK")
            },
            3
        )
    }

}
