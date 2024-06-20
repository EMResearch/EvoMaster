package org.evomaster.core.problem.enterprise.auth

/**
 * representing AuthenticationInfo
 */
abstract class AuthenticationInfo(
    /**
     * name of the Authentication.
     * this must be unique
     */
    val name: String
){


    init {
        if (name.isBlank()) {
            throw IllegalArgumentException("Blank name")
        }
    }

    /**
     * For auth info, names are unique.
     * so, 2 auths are technically different as long as have different names.
     * this forced in [AuthSettings]
     */
    fun isDifferentFrom(other: AuthenticationInfo) = this.name != other.name
}