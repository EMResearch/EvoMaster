package org.evomaster.core.problem.httpws.auth

import org.evomaster.client.java.controller.api.dto.auth.AuthenticationDto
import org.evomaster.core.problem.enterprise.auth.AuthenticationInfo
import org.evomaster.core.problem.rest.RestCallAction
import org.evomaster.core.remote.SutProblemException
import org.evomaster.core.search.action.Action

/**
 * should be immutable
 *
 * AuthenticationInfo for http based SUT.
 * There can be different ways to do authentication.
 * Here, just one would be active (the others will be null or empty)
 *
 */
open class HttpWsAuthenticationInfo(
    name: String,
    /**
     * Static authentication-related headers. Might be empty.
     */
    val headers: List<AuthenticationHeader>,
    /**
     * Represent call done to a login endpoint, from which a token or cookie is extracted
     * for auth in following requests.
     */
    val endpointCallLogin: EndpointCallLogin?,
    val requireMockHandling: Boolean
): AuthenticationInfo(name) {

    init {

        //FIXME "NoAuth" constant
        if(headers.isEmpty() && name != "NoAuth" && endpointCallLogin==null){
            throw IllegalArgumentException("Missing info")
        }
    }

    companion object{

        fun fromDto(dto: AuthenticationDto) : HttpWsAuthenticationInfo{

            if (dto.name == null || dto.name.isBlank()) {
                throw IllegalArgumentException("Missing name in authentication info")
            }

            val headers: MutableList<AuthenticationHeader> = mutableListOf()

            dto.fixedHeaders.forEach loop@{ h ->
                val name = h.name?.trim()
                val value = h.value?.trim()
                if (name == null || value == null) {
                    throw IllegalArgumentException("Invalid header in ${dto.name}, $name:$value")
                }

                headers.add(AuthenticationHeader(name, value))
            }

            val endpointCallLogin = if(dto.loginEndpointAuth != null){
                try {
                    EndpointCallLogin.fromDto(dto.name, dto.loginEndpointAuth)
                } catch (e: Exception){
                    throw IllegalArgumentException("Issue when parsing auth info for '${dto.name}': ${e.message}")
                }
            } else {
                null
            }

            val requireMockHandling = dto.requireMockHandling != null && dto.requireMockHandling

           return HttpWsAuthenticationInfo(dto.name.trim(), headers, endpointCallLogin, requireMockHandling)
        }
    }


    /**
     * @return whether to exclude auth check (401 status code in the response) for the [action]
     */
    fun excludeAuthCheck(action: Action) : Boolean{
        if (action is RestCallAction && endpointCallLogin != null){
            return action.getName() == "POST:${endpointCallLogin.endpoint}"
        }
        return false
    }
}