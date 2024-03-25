package org.evomaster.e2etests.spring.openapi.v3.wiremock.okhttp

import com.foo.rest.examples.spring.openapi.v3.wiremock.okhttp.WmOkHttpController
import org.evomaster.ci.utils.CIUtils
import org.evomaster.core.EMConfig
import org.evomaster.core.problem.rest.HttpVerb
import org.evomaster.e2etests.spring.openapi.v3.SpringTestBase
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test


class WmOkHttpEMTest : SpringTestBase() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun init() {

            val config = EMConfig()
            config.instrumentMR_NET = true
            initClass(WmOkHttpController(), config)

            /*
            The test fails on CI, but not local with WM 2.32.0

            if updating WM to 2.34.0, the test fails on local windows as well (TO CHECK)
            */
            //CIUtils.skipIfOnGA()
        }
    }


    @Test
    fun testRunEM() {

        runTestHandlingFlakyAndCompilation(
            "WmOkHttpEM",
            "org.foo.WmOkHttpEM",
            500,
            true,
            { args: MutableList<String> ->

                // TODO: When using actual domain name, Java tries to verify for the SSL certificate the
                //  respective name, so generated tests fails.
                //  PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target

                args.add("--externalServiceIPSelectionStrategy")
                args.add("USER")
                args.add("--externalServiceIP")
                args.add("127.0.0.12")
                args.add("--minimize")
                args.add("false")

                val solution = initAndRun(args)

                assertTrue(solution.individuals.size >= 1)
                assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wm/socketconnect/string", "OK")
                assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wm/socketconnect/string", "Hello There")
                assertHasAtLeastOne(solution, HttpVerb.GET, 400, "/api/wm/socketconnect/string", null)
                assertHasAtLeastOne(solution, HttpVerb.GET, 500, "/api/wm/socketconnect/string", null)

                assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wm/socketconnect/object", "OK")

                assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wm/socketconnect/sstring", "OK")
                assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wm/socketconnect/sstring", "Hello There")
                assertHasAtLeastOne(solution, HttpVerb.GET, 400, "/api/wm/socketconnect/sstring", null)
                assertHasAtLeastOne(solution, HttpVerb.GET, 500, "/api/wm/socketconnect/sstring", null)
            },
            3
        )
    }

}
