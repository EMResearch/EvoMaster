package org.evomaster.e2etests.spring.openapi.v3.wiremock.harvestoptimisation

import com.alibaba.dcm.DnsCacheManipulator
import com.foo.rest.examples.spring.openapi.v3.wiremock.harvestoptimisation.HarvestOptimisationController
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer
import org.evomaster.ci.utils.CIUtils
import org.evomaster.core.EMConfig
import org.evomaster.core.problem.rest.HttpVerb
import org.evomaster.e2etests.spring.openapi.v3.SpringTestBase
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class HarvestOptimisationEMTest: SpringTestBase() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun init() {
            val config = EMConfig()
            config.instrumentMR_NET = true
            initClass(HarvestOptimisationController(), config)
            //CIUtils.skipIfOnGA()
        }
    }

    @Test
    fun testRunEM() {
        val wmConfig = WireMockConfiguration()
            .bindAddress("127.0.0.1")
            .port(9999)
            .extensions(ResponseTemplateTransformer(false))

        val wm = WireMockServer(wmConfig)
        wm.start()
        wm.stubFor(
            WireMock.get(
                WireMock.urlEqualTo("/api/mock"))
                .atPriority(1)
                .willReturn(WireMock.aResponse().withStatus(200).withBody("{\"message\" : \"Working\"}"))
        )
        wm.stubFor(
            WireMock.get(
                WireMock.urlEqualTo("/api/mock/second"))
                .atPriority(1)
                .willReturn(WireMock.aResponse().withStatus(200).withBody("{\"message\" : \"Yes! Working\"}"))
        )

        DnsCacheManipulator.setDnsCache("mock.int", "127.0.0.1")

        runTestHandlingFlakyAndCompilation(
            "HarvestOptimisationEM",
            "org.foo.HarvestOptimisationEM",
            1000,
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
                args.add("--externalRequestResponseSelectionStrategy")
                args.add("EXACT")

                val solution = initAndRun(args)

                assertTrue(solution.individuals.size >= 1)
                assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wm/harvester/external", "Working")
            },
            3
        )

        wm.shutdown()
        DnsCacheManipulator.clearDnsCache()
    }
}