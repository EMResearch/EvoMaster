package org.evomaster.core.problem.external.service.httpws

import java.util.UUID

/**
 * Represent an external service call made to a WireMock
 * instance from a SUT.
 *
 * TODO: Properties have to extended further based on the need
 */
class HttpExternalServiceRequest(
    val id: UUID,
    val method: String,
    val url: String,
    val absoluteURL: String,
    val wasMatched: Boolean,
) {

    fun getId() : String {
        return id.toString()
    }
}