package org.evomaster.core.problem.httpws.auth

import org.evomaster.core.problem.enterprise.auth.AuthenticationInfo
import org.evomaster.core.problem.rest.RestCallAction
import org.evomaster.core.search.action.Action

/**
 * should be immutable
 *
 * AuthenticationInfo for http based SUT
 *
 */
open class HttpWsAuthenticationInfo(
    name: String,
    val headers: List<AuthenticationHeader>,
    val endpointCallLogin: EndpointCallLogin?
): AuthenticationInfo(name) {

    init {

        //FIXME "NoAuth" constant
        if(headers.isEmpty() && name != "NoAuth" && endpointCallLogin==null){
            throw IllegalArgumentException("Missing info")
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