package org.evomaster.e2etests.spring.openapi.v3.wiremock.inet

import com.foo.rest.examples.spring.openapi.v3.wiremock.inet.InetReplacementController
import org.evomaster.ci.utils.CIUtils
import org.evomaster.core.EMConfig
import org.evomaster.core.problem.rest.HttpVerb
import org.evomaster.e2etests.spring.openapi.v3.SpringTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class InetReplacementEMTest : SpringTestBase() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun init() {
            val config = EMConfig()
            config.instrumentMR_NET = true
            initClass(InetReplacementController(), config)
        }
    }

    @Disabled("Disabled temporarily")
    fun testRunEM() {
        runTestHandlingFlakyAndCompilation(
            "InetReplacementEM",
            "org.foo.InetReplacementEM",
            1000,
            !CIUtils.isRunningGA(),
            { args: MutableList<String> ->

                args.add("--externalServiceIPSelectionStrategy")
                args.add("USER")
                args.add("--externalServiceIP")
                args.add("127.0.0.3")

                //FIXME should make sure it works with true.
                //looks like a bug in resetting the DNS cache to default state
                args.add("--minimize")
                args.add("false")

                val solution = initAndRun(args)

                Assertions.assertTrue(solution.individuals.size >= 1)

                if (!CIUtils.isRunningGA()) {
                    assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/inet/exp", "OK")
                    //FIXME should also have check on 500 and 400.
                    //actually should change code, as thrown exception leads to 500, need way to distinguish
                    assertHasAtLeastOne(solution, HttpVerb.GET, 400, "/api/inet/exp",null)
                    assertHasAtLeastOne(solution, HttpVerb.GET, 500, "/api/inet/exp",null)
                }
            },
            3
        )
    }
}
