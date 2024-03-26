package org.evomaster.core.problem.rest.service

import org.evomaster.core.problem.httpws.HttpWsAction
import org.evomaster.core.problem.httpws.service.HttpWsFitness
import org.evomaster.core.problem.rest.RestCallAction
import org.evomaster.core.problem.rest.RestIndividual
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.action.ActionResult
import org.glassfish.jersey.client.JerseyClientBuilder
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.client.Invocation
import javax.ws.rs.client.Invocation.Builder
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.NewCookie
import javax.ws.rs.core.Response

/**
 * This class is created for reusing rest action building process
 */
object RestActionUtils : HttpWsFitness<RestIndividual>() {

    fun handleSimpleRestCallForSwagger(a : RestCallAction, openApiUrl: String): Response {

        var currentReq = ClientBuilder.newClient()
            .target(openApiUrl)
            .request(MediaType.APPLICATION_JSON_TYPE)

        handleHeaders(a,currentReq, mapOf(), mapOf() )

        return currentReq.get()
    }

    override fun doCalculateCoverage(
        individual: RestIndividual,
        targets: Set<Int>,
        allCovered: Boolean
    ): EvaluatedIndividual<RestIndividual>? {
        TODO("Not yet implemented")
    }

}