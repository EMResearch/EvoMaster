package org.evomaster.core.problem.enterprise.auth

import org.evomaster.core.search.service.Randomness


/**
 * Class used to store and handle all Auth Info for the current SUT
 */
class AuthSettings(authInfos: List<AuthenticationInfo> = listOf()) {

    private val auths = mutableListOf<AuthenticationInfo>()
    init {
        val n  = authInfos.size
        val unique = authInfos.map { it.name }.toSet()
        val k = unique.size
        if(k != n){
            throw IllegalArgumentException("There are repeated names in the auth definitions:" +
                    " [${unique.joinToString(",")}]")
        }

        auths.addAll(authInfos)
    }

    fun addInfo(info: AuthenticationInfo){
        if(auths.any { it.name == info.name }){
            throw IllegalArgumentException("Auth configuration with name '${info.name} already exists")
        }
        auths.add(info)
    }

    fun <T : AuthenticationInfo> getOfType(klass: Class<T>) = auths.filterIsInstance(klass)


    fun <T : AuthenticationInfo> isNotEmpty(klass: Class<T>) = auths.any { klass.isAssignableFrom(it.javaClass) }

    fun isNotEmpty() = auths.isNotEmpty()

    fun size() = auths.size

    fun getFirstAuthentication() = auths.first()

    fun <T : AuthenticationInfo> size(klass: Class<T>) = auths.count{ klass.isAssignableFrom(it.javaClass) }

    fun <T : AuthenticationInfo> getDifferentOne(name: String, klass: Class<T>, randomness: Randomness) : T {

        if(auths.none { it.name == name }){
            throw IllegalArgumentException("No auth info with name: $name")
        }

        val others = getOfType(klass).filter { it.name != name }
        if(others.isEmpty()){
            throw IllegalArgumentException("There is no other auth config besides '$name' for type $klass")
        }

        return randomness.choose(others)
    }
}