package org.evomaster.core.problem.rest

import org.evomaster.core.problem.httpws.HttpWsAction
import org.evomaster.core.problem.httpws.auth.HttpWsAuthenticationInfo
import org.evomaster.core.problem.httpws.auth.HttpWsNoAuth
import org.evomaster.core.problem.rest.param.BodyParam
import org.evomaster.core.problem.rest.param.FormParam
import org.evomaster.core.problem.api.param.Param
import org.evomaster.core.problem.rest.param.PathParam
import org.evomaster.core.problem.rest.resource.ActionRToken
import org.evomaster.core.problem.rest.service.AbstractRestSampler
import org.evomaster.core.problem.util.ParamUtil
import org.evomaster.core.problem.rest.util.ParserUtil
import org.evomaster.core.problem.util.BindingBuilder
import org.evomaster.core.search.action.Action
import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.gene.optional.OptionalGene
import org.evomaster.core.search.service.Randomness
import java.net.URLEncoder


class RestCallAction(
    /**
     * Identifier unique within the individual
     * **/
    val id:String,
    val verb: HttpVerb,
    val path: RestPath,
    parameters: MutableList<Param>,
    auth: HttpWsAuthenticationInfo = HttpWsNoAuth(),
    /**
     * If true, it means that it will
     * instruct to save the "location" header of the HTTP response for future
     * use by following calls. Typical case is to save the location of
     * a resource generated with a POST
     */
    saveLocation: Boolean = false,
    /**
     * Specify to use the "location" header of a
     * previous POST as path. As there might be different
     * POSTs creating different resources in the same test,
     * need to specify an id.
     *
     * Note: it might well be that we save the location returned
     * by a POST, where the POST itself might use a location for
     * path coming from a previous POST.
     *
     * It is possible that no location header is used, but id of newly created
     * resource is returned in body payload.
     * As such, we might use some heuristics to infer the "location"
     */
    var usePreviousLocationId: String? = null,
    val produces: List<String> = listOf(),
    val responseRefs : MutableMap<String, String> = mutableMapOf(),
    val skipOracleChecks : Boolean = false
) : HttpWsAction(auth, parameters) {

    var saveLocation : Boolean = saveLocation
        set(value) {
            if(value && verb != HttpVerb.POST){
                throw IllegalArgumentException("Save location can only be used for POST")
            }
            field = value
        }

    /**
     * collect info of description and summary from swagger
     */
    private var description : String? = null

    /**
     * tokens is used to present a set of tokens exist in the scheme, e.g., swagger specification
     * key is a parsed token
     * value is [ActionRToken]
     */
    val tokens : MutableMap<String, ActionRToken> = mutableMapOf()


    override fun shouldCountForFitnessEvaluations(): Boolean = true


    /**
     * @return a string representing an id to use when setting "saveLocation".
     *  following REST call can use such id to refer to the dynamically generated resource.
     */
    fun postLocationId() : String {
        if(verb != HttpVerb.POST){
            throw IllegalStateException("Location Ids are meaningful only for POST operations")
        }
        return  path.lastElement()
    }

    fun isLocationChained() = saveLocation || usePreviousLocationId?.isNotBlank() ?: false

    override fun copyContent(): Action {
        val p = parameters.asSequence().map(Param::copy).toMutableList()
        return RestCallAction(id, verb, path, p, auth, saveLocation, usePreviousLocationId, produces, responseRefs, skipOracleChecks)
    }

    override fun getName(): String {
        return "$verb:$path"
    }

    override fun seeTopGenes(): List<out Gene> {
        return parameters.flatMap { it.seeGenes() }
    }

    override fun toString(): String {
        return "$verb ${resolvedPath()} , auth=${auth.name}"
    }

    fun resolvedPath(): String {
        return path.resolve(parameters)
    }

    fun resolvedOnlyPath() : String{
        return path.resolveOnlyPath(parameters)
    }

    /**
     * Make sure that the path params are resolved to the same concrete values of "other".
     * Note: "this" can be just an ancestor of "other"
     *
     **/
    fun bindToSamePathResolution(other: RestCallAction) {
        if (!this.path.isSameOrAncestorOf(other.path)) {
            throw IllegalArgumentException("Cannot bind 2 different unrelated paths to the same path resolution: " +
                    "${this.path} vs ${other.path}")
        }
        for (i in 0 until parameters.size) {
            val target = parameters[i]
            if (target is PathParam) {
                val k = other.parameters.find { p -> p is PathParam && p.name == target.name }!!
                parameters[i].gene.copyValueFrom(k.gene)
            }
        }
    }

    /**
    Note: in swagger the "consume" type might be missing.
    So, if for any reason there is a form param, then consider
    the body as an application/x-www-form-urlencoded

    see https://www.w3.org/TR/html401/interact/forms.html#h-17.13.4.1
    Note: that is old HTML 4, still dealing with RFC 1738, from 1994...

    HTML 5.1 (November 2016) has these rules:
    https://www.w3.org/TR/html/sec-forms.html#urlencoded-form-data

    which unfortunately are unreadable...

    Regarding URLEncoder in Java 8, it refers to URIs from RFC 2396 from
    1998 (updating RFC 1738), which is obsoleted by RFC 3986 since 2005!!!

    Plus, x-www-form-urlencoded and encoding of URIs are not the same!!!

    REALLY: WTF?!?

    TODO: update/verify based on
    https://url.spec.whatwg.org/#concept-urlencoded-byte-serializer

     */
    @Deprecated("No needed anymore with OpenAPI v3")
    fun getBodyFormData(): String? {

        val forms = parameters.filterIsInstance<FormParam>()
        if(forms.isEmpty()){
            return null
        }

        return forms.filter { it.gene !is OptionalGene || it.gene.isActive }
                .map {
                    val name = URLEncoder.encode(it.gene.getVariableName(), "UTF-8")
                    val value = URLEncoder.encode(it.gene.getValueAsRawString(), "UTF-8")
                    "$name=$value"
                }
                .joinToString("&")
    }

    fun initTokens(description : String?){
        if(description!= null){
            this.description = description
            tokens.clear()
            ParserUtil.parseAction(this, description, tokens)
        }
    }

    /**
     * bind [parameters] based on [other]
     * @return whether any of param is bound with [other]
     */
    fun bindBasedOn(other: RestCallAction, randomness: Randomness?) : Boolean{
        var dependent = false
        parameters.forEach { p->
            dependent = BindingBuilder.bindRestAction(p, path, other.path, other.parameters, doBuildBindingGene = true, randomness = randomness) || dependent
        }
        return dependent
    }

    /**
     * it is used to bind [this] action regarding values of [params]
     * @param otherPath is the path of the [params]
     * @param params to bind
     * @param randomness is the randomness used in binding,
     *              if the randomness is null, all params are completely bound, ie, all fields of two ObjectGene will be completely bound
     */
    fun bindBasedOn(otherPath : RestPath, params : List<Param>, randomness: Randomness?) {

        if(params.isEmpty()){
            //no param is required to bind
            return
        }
        /*
           there may exist that a rest action has e.g., path parameter and body parameter.
           in this case (not all body param), we only bind non- BodyParam, e.g., PathParam
           the body parameter will be bound by "repair" process to ensure the same attribute of path and body parameter have same value.
         */
        if(!ParamUtil.isAllBodyParam(parameters)){
            parameters.filterNot { param -> param is BodyParam }.forEach { param->
                BindingBuilder.bindRestAction(param, this.path, otherPath, params, randomness = randomness)
            }
        }else{
            parameters.forEach {param->
                BindingBuilder.bindRestAction(param, this.path, otherPath, params, randomness = randomness)
            }
        }
    }

    /**
     * reset [saveLocation], [usePreviousLocationId] and [responseRefs] properties of [this] RestCallAction
     */
    fun resetProperties(){
        saveLocation = false
        usePreviousLocationId = null
        resetLocalId()
        seeTopGenes().flatMap { it.flatView() }.forEach { it.resetLocalId() }
        clearRefs()
    }

    fun getDescription() : String? = description

    fun clearRefs(){
        responseRefs.clear()
    }
    fun addRef(key: String, ref: String){
        responseRefs[key] = ref
    }


    override fun postRandomizedChecks(randomness: Randomness?) {
        // binding params in this action, e.g., path param with body param if there exists
        BindingBuilder.bindParamsInRestAction(this, randomness = randomness)
    }

    override fun shouldSkipAssertionsOnResponseBody(): Boolean {
        return id == AbstractRestSampler.CALL_TO_SWAGGER_ID
    }
}