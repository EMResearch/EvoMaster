package org.evomaster.e2etests.spring.openapi.v3.jackson.auth0

import com.foo.rest.examples.spring.openapi.v3.jackson.auth0.AuthZeroJacksonController
import org.evomaster.ci.utils.CIUtils
import org.evomaster.core.problem.rest.HttpVerb
import org.evomaster.e2etests.spring.openapi.v3.SpringTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class AuthZeroJacksonEMTest: SpringTestBase() {


    companion object {
        @BeforeAll
        @JvmStatic
        fun init() {
            initClass(AuthZeroJacksonController())
            //CIUtils.skipIfOnGA()
        }
    }

    @Disabled("Disabled temporarily")
    fun testRunEM() {
        // Generated test has response which is accurate, but test fails because the
        // SUT throws error for the case which worked during the search.
        // When the created tests set to false, the test pass.
        // SUT uses HTTPS so the test won't work on macOS.
        runTestHandlingFlakyAndCompilation(
            "GeneratedAuthZeroJacksonEMTest",
            "org.foo.GeneratedAuthZeroJacksonEMTest",
            500,
            !CIUtils.isRunningGA(),
            { args: MutableList<String> ->

                args.add("--externalServiceIPSelectionStrategy")
                args.add("USER")
                args.add("--externalServiceIP")
                args.add("127.0.0.4")
                args.add("--instrumentMR_NET")
                args.add("true")

                val solution = initAndRun(args)

                Assertions.assertTrue(solution.individuals.size >= 1)
                assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/jackson/auth", "Working")
            }, 3
        )
    }
}
