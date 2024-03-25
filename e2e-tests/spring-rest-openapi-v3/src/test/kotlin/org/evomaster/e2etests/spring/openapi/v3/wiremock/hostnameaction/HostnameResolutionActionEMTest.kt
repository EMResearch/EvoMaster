package org.evomaster.e2etests.spring.openapi.v3.wiremock.hostnameaction

import com.foo.rest.examples.spring.openapi.v3.wiremock.hostnameaction.HostnameResolutionActionController
import org.evomaster.ci.utils.CIUtils
import org.evomaster.core.EMConfig
import org.evomaster.core.problem.rest.HttpVerb
import org.evomaster.e2etests.spring.openapi.v3.SpringTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class HostnameResolutionActionEMTest: SpringTestBase() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun init() {
            val config = EMConfig()
            config.instrumentMR_NET = true
            initClass(HostnameResolutionActionController(), config)
        }
    }

    @Test
    fun testRunEM() {
        runTestHandlingFlakyAndCompilation(
            "HostnameResolutionActionEMTest",
            "org.foo.HostnameResolutionActionEMTest",
            100,
            false,
            { args: MutableList<String> ->

                // Note: WireMock is initiated based on the served requests.
                // This SUT doesn't make any requests, so [TestSuiteWriter] will not add
                // any WM, eventually the generated tests will fail.
                // TODO: This will fail when [createTests] is true regardless of the
                //  environment.

                args.add("--externalServiceIPSelectionStrategy")
                args.add("USER")
                args.add("--externalServiceIP")
                args.add("127.0.0.4")
                // TODO: Need to remove, once the issue resolved
                args.add("--minimize")
                args.add("false")

                val solution = initAndRun(args)

                Assertions.assertTrue(solution.individuals.size >= 1)

                //if (!CIUtils.isRunningGA()) {
                    assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/resolve", "OK")
                //}
            },
            20
        )
    }

}
