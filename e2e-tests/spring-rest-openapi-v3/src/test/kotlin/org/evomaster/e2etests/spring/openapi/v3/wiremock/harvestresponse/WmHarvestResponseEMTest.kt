package org.evomaster.e2etests.spring.openapi.v3.wiremock.harvestresponse

import com.foo.rest.examples.spring.openapi.v3.wiremock.harvestresponse.WmHarvestResponseController
import com.foo.rest.examples.spring.openapi.v3.wiremock.harvestresponse.WmHarvestResponseRest.Companion.HARVEST_FOUND
import com.foo.rest.examples.spring.openapi.v3.wiremock.harvestresponse.WmHarvestResponseRest.Companion.HARVEST_NOT_FOUND
import org.evomaster.ci.utils.CIUtils
import org.evomaster.core.EMConfig
import org.evomaster.core.problem.rest.HttpVerb
import org.evomaster.e2etests.spring.openapi.v3.SpringTestBase
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class WmHarvestResponseEMTest : SpringTestBase() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun init() {
            val config = EMConfig()
            config.instrumentMR_NET = true
            initClass(WmHarvestResponseController(), config)

            //CIUtils.skipIfOnGA()
        }
    }


    @Disabled("Disabled temporarily")
    fun testRunEM() {
        // External service uses port 80 and 443 so the test will fail in macOS.
        runTestHandlingFlakyAndCompilation(
            "WmHarvestResponseEM",
            "org.foo.WmHarvestResponseEM",
            1500,
                true,//!CIUtils.isRunningGA(),
            { args: MutableList<String> ->

                args.add("--externalServiceIPSelectionStrategy")
                args.add("USER")
                args.add("--externalServiceIP")
                args.add("127.0.0.3")
                args.add("--probOfHarvestingResponsesFromActualExternalServices")
                args.add("0.9")
                args.add("--probOfMutatingResponsesBasedOnActualResponse")
                args.add("0.1")
                args.add("--probOfPrioritizingSuccessfulHarvestedActualResponses")
                args.add("0.9")
                args.add("--externalRequestResponseSelectionStrategy")
                args.add("RANDOM")

                val solution = initAndRun(args)

                assertTrue(solution.individuals.size >= 1)
                assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wm/harvestresponse/images", "ANY FROM ONE TO NINE")
                assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wm/harvestresponse/images", "MORE THAN 10")
                assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wm/harvestresponse/images", "NONE")

                assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wm/harvestresponse/grch37Example", HARVEST_FOUND)
                assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wm/harvestresponse/grch37Example", HARVEST_NOT_FOUND)

                assertHasAtLeastOne(solution, HttpVerb.POST, 200, "/api/wm/harvestresponse/grch37Annotation", HARVEST_FOUND)
                assertHasAtLeastOne(solution, HttpVerb.POST, 200, "/api/wm/harvestresponse/grch37Annotation", HARVEST_FOUND)

                assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wm/harvestresponse/grch37Id", HARVEST_FOUND)
                assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wm/harvestresponse/grch37Id", HARVEST_NOT_FOUND)

//                assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wm/harvestresponse/users", ">10")
//                assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wm/harvestresponse/users", "<10")
//                assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wm/harvestresponse/users", "which has foo user")
            },
            5
        )
    }

}
