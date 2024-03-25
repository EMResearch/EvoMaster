package org.evomaster.e2etests.spring.openapi.v3.wiremock.harveststrategy

import com.alibaba.dcm.DnsCacheManipulator
import com.foo.rest.examples.spring.openapi.v3.wiremock.harveststrategy.HarvestStrategyController
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

class HarvestingStrategyTest : SpringTestBase() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun init() {
            val config = EMConfig()
            config.instrumentMR_NET = true
            initClass(HarvestStrategyController(), config)
        }
    }

    @Test
    fun testExactResponse() {
        val wmConfig = WireMockConfiguration()
            .bindAddress("127.0.0.10")
            .port(13579)
            .extensions(ResponseTemplateTransformer(false))

        val wm = WireMockServer(wmConfig)
        wm.start()
        wm.stubFor(
            WireMock.get(
                WireMock.urlEqualTo("/api/mock")
            )
                .atPriority(1)
                .willReturn(WireMock.aResponse().withStatus(200).withBody("{\"message\" : \"Working\"}"))
        )

        DnsCacheManipulator.setDnsCache("mock.int", "127.0.0.10")

        runTestHandlingFlakyAndCompilation(
            "HarvestStrategyExactEMTest",
            "org.foo.HarvestStrategyExactEMTest",
            1500,
            !CIUtils.isRunningGA(), // this fails in local and CI
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
                if (!CIUtils.isRunningGA()) {
                    assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/harvest/strategy/exact", "Working")
                }
            },
            3
        )

        wm.shutdown()
        DnsCacheManipulator.clearDnsCache()
    }

    @Test
    fun testClosestResponse() {
        // For /api/harvest/strategy/closest/second WireMock will response with 500
        // so the core will select the nearest with the response status code 200.
        val wmConfig = WireMockConfiguration()
            .bindAddress("127.0.0.13")
            .port(13578)
            .extensions(ResponseTemplateTransformer(false))

        val wm = WireMockServer(wmConfig)
        wm.start()
        wm.stubFor(
            WireMock.get(
                WireMock.urlEqualTo("/api/mock")
            )
                .atPriority(1)
                .willReturn(WireMock.aResponse().withStatus(200).withBody("{\"message\" : \"Working\"}"))
        )
        wm.stubFor(
            WireMock.any(WireMock.anyUrl())
                .atPriority(2)
                .willReturn(WireMock.aResponse().withStatus(500).withBody("Internal Server Error"))
        )

        DnsCacheManipulator.setDnsCache("mock.int", "127.0.0.13")

        runTestHandlingFlakyAndCompilation(
            "HarvestStrategyClosestSameDomainEMTest",
            "org.foo.HarvestStrategyClosestEMTest",
            1000,
            true,
            { args: MutableList<String> ->

                args.add("--externalServiceIPSelectionStrategy")
                args.add("USER")
                args.add("--externalServiceIP")
                args.add("127.0.0.4")
                args.add("--probOfHarvestingResponsesFromActualExternalServices")
                args.add("0.9")
                args.add("--probOfMutatingResponsesBasedOnActualResponse")
                args.add("0.1")
                args.add("--externalRequestResponseSelectionStrategy")
                args.add("CLOSEST_SAME_DOMAIN")

                val solution = initAndRun(args)

                assertTrue(solution.individuals.size >= 1)
                assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/harvest/strategy/closest", "Working")
            },
            3
        )

        wm.shutdown()
        DnsCacheManipulator.clearDnsCache()
    }
}
