package org.evomaster.core.problem.httpws.auth

import org.evomaster.core.problem.enterprise.auth.NoAuth


class HttpWsNoAuth : HttpWsAuthenticationInfo("NoAuth", listOf(), null, false), NoAuth