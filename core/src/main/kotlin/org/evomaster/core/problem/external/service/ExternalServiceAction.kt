package org.evomaster.core.problem.external.service

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import org.evomaster.core.search.Action
import org.evomaster.core.search.StructuralElement
import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.service.Randomness

/**
 * Action to execute the external service related need
 * to handle the external service calls.
 *
 * Typically, handle WireMock responses
 */
class ExternalServiceAction (
    /**
     * Received request to the respective WireMock instance
     *
     * TODO: Need to expand the properties further in future
     * depending on the need
     */
    val request: ExternalServiceRequest,

    /**
     * WireMock server which received the request
     */
    val wireMockServer: WireMockServer,
    private val id: Long,
    computedGenes: List<Gene>? = null,
        ) : Action(listOf()) {

    private val genes: List<Gene> = (computedGenes ?:
        ExternalServiceActionGeneBuilder().buildGene(request)
    )

    override fun getName(): String {
        // TODO: Need to change in future
        return request.getID()
    }

    override fun seeGenes(): List<out Gene> {
        return genes
    }

    override fun shouldCountForFitnessEvaluations(): Boolean {
        return false
    }

    override fun copyContent(): StructuralElement {
        return ExternalServiceAction(request, wireMockServer, id, genes.map(Gene::copy))
    }

    /**
     * Experimental implementation of WireMock stub generation
     *
     * Method should randomize the response code
     */
    fun buildResponse(randomness: Randomness) {
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlMatching(request.getURL()))
                .atPriority(1)
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withBody("")))
    }

}