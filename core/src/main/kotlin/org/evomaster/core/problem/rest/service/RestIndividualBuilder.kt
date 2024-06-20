package org.evomaster.core.problem.rest.service

import com.google.inject.Inject
import org.evomaster.core.problem.rest.*
import org.evomaster.core.search.service.Randomness


/**
 * Set of operations to help creating new individuals, taking into account what is available
 * in the schema (but NOT the archive).
 */
class RestIndividualBuilder {

    @Inject
    private lateinit var sampler: AbstractRestSampler

    @Inject
    private lateinit var randomness: Randomness


    /**
     * Based on a given [template], create a new action for it.
     * Such new action will have the same path resolution of [target], using same auth.
     * Note that [template] must have same path of be an ancestor of [target].
     * For example:
     * template: GET /users/{id}
     * target:   PUT /users/42/orders/77
     * would lead to return something like:
     *           GET /users/42
     *
     */
    fun createBoundActionFor(template: RestCallAction, target: RestCallAction): RestCallAction {

        if (!template.path.isSameOrAncestorOf(target.path)) {
            throw IllegalArgumentException("Cannot create an action for unrelated paths: " +
                    "${template.path} vs ${target.path}")
        }

        val res = template.copy() as RestCallAction
        if(res.isInitialized()){
            res.seeTopGenes().forEach { it.randomize(randomness, false) }
        } else {
            res.doInitialize(randomness)
        }
        res.auth = target.auth
        res.bindToSamePathResolution(target)

        return res
    }

    /**
     * Make sure that what returned is different from the target.
     * This can be a strict ancestor (shorter path), or same
     * endpoint but with different HTTP verb.
     * Among the different ancestors, return one of the longest
     *
     * @return a potentially null [RestCallAction] using any of the given verbs,
     *        and that is different from [target], but having same or ancestor path.
     */
    fun chooseClosestAncestor(target: RestCallAction, verbs: List<HttpVerb>): RestCallAction? {

        var others = sameOrAncestorEndpoints(target.path)
        others = filterBasedOnVerbs(others, verbs)
            .filter {
                //recall name is using verb and path
                t -> t.getName() != target.getName()
            }

        if (others.isEmpty()) {
            return null
        }

        return chooseLongestPath(others)
    }

    /**
     * return all actions using any of the specified verbs
     */
    private fun filterBasedOnVerbs(actions: List<RestCallAction>, verbs: List<HttpVerb>): List<RestCallAction> {
        return actions.filter { a ->
            verbs.contains(a.verb)
        }
    }

    /**
     * Get all ancestor (or same path) endpoints.
     * This is based on their path structure
     */
    fun sameOrAncestorEndpoints(path: RestPath): List<RestCallAction> {
        return sampler.seeAvailableActions().asSequence()
            .filterIsInstance<RestCallAction>()
            .filter { it.path.isSameOrAncestorOf(path) }
            .toList()
    }

    private fun findTemplate(path: RestPath, verb: HttpVerb) : RestCallAction? {
        return sampler.seeAvailableActions()
            .filterIsInstance<RestCallAction>()
            .find { it.path.isEquivalent(path) && it.verb == verb }
    }

    private fun chooseLongestPath(actions: List<RestCallAction>): RestCallAction {

        if (actions.isEmpty()) {
            throw IllegalArgumentException("Cannot choose from an empty collection")
        }

        val max = actions.asSequence().map { a -> a.path.levels() }.maxOrNull()!!
        val candidates = actions.filter { a -> a.path.levels() == max }

        return randomness.choose(candidates)
    }

    /**
     * Create a new action, to be added to the list representing the [test] we are building.
     * Given a [target] (eg, a GET on a specific resource path X), create a new action that should lead
     * to the creation of such resource.
     * Target must be inside [test].
     * This new action will be added at the beginning of [test].
     * As such action might need its own ancestor resources, this process is then applied recursively.
     */
     fun createResourcesFor(target: RestCallAction, test: MutableList<RestCallAction>)
            : Boolean {

         if(!test.contains(target)){
             throw IllegalArgumentException("Target ${target.getName()} is not inside test:" +
                     " ${test.joinToString(" , ") { it.getName() }}")
         }

        val template = chooseClosestAncestor(target, listOf(HttpVerb.POST))
            ?: (if(target.verb != HttpVerb.PUT) findTemplate(target.path, HttpVerb.PUT) else null)
                ?: return false

        val create = createBoundActionFor(template, target)

        if(template.verb == HttpVerb.PUT){
            /*
                TODO: should check if body payload has any id matching the path element...
                if so, should bind it
             */
        }

        test.add(0, create)

        /*
            Check if create action depends itself on the creation of
            some intermediate resources
         */
        if (
            (create.path.hasVariablePathParameters() && !create.path.isLastElementAParameter())
            || create.path.getVariableNames().size >= 2
            ) {

            val dependencyCreated = createResourcesFor(create, test)
            if (!dependencyCreated) {
                return false
            }
        }


        /*
            Once the create is fully initialized, need to fix
            links with target
         */
        linkDynamicCreateResource(create, target)

        return true
    }

    /**
     * Given two actions in sequence, [before] and [after], setup a creation link.
     * This means that [before] is supposed to create a resource dynamically, which is then used
     * by [after].
     * eg:
     * before: POST   /products
     * after:  DELETE /products/{id}
     */
    fun linkDynamicCreateResource(
        before: RestCallAction,
        after: RestCallAction
    ) {
        if (!before.path.isEquivalent(after.path)) {
            /*
                eg
                POST /x
                GET  /x/{id}
             */
            before.saveLocation = true
            after.usePreviousLocationId = before.postLocationId()
        } else {
            /*
                eg
                POST /x
                POST /x/{id}/y
                GET  /x/{id}/y
                not going to save the position of last POST, as same as target

                however, might also be in the case of:
                PUT /x/{id}
                GET /x/{id}
             */
            before.saveLocation = false

            // the target (eg GET) needs to use the location of first POST, or more correctly
            // the same location used for the last POST (in case there is a deeper chain)
            after.usePreviousLocationId = before.usePreviousLocationId
        }
    }


    /**
     * Create a copy of [restIndividual], where all main actions after index are removed
     */
    fun sliceAllCallsInIndividualAfterAction(restIndividual: RestIndividual, actionIndex: Int) : RestIndividual {

        //TODO move code here
        return RestIndividualSelectorUtils.sliceAllCallsInIndividualAfterAction(restIndividual, actionIndex)
    }

    /**
     * Check in the schema if there is any action which is a direct child of [a] and last path element is a parameter
     */
    fun hasParameterChild(a: RestCallAction): Boolean {
        return sampler.seeAvailableActions()
            .filterIsInstance<RestCallAction>()
            .map { it.path }
            .any { it.isDirectChildOf(a.path) && it.isLastElementAParameter() }
    }


}