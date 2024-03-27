package org.evomaster.core.problem.rest.service

import com.google.inject.Inject
import org.evomaster.client.java.controller.api.EMTestUtils
import org.evomaster.client.java.controller.api.dto.ActionDto
import org.evomaster.client.java.controller.api.dto.AdditionalInfoDto
import org.evomaster.client.java.controller.api.dto.TestResultsDto
import org.evomaster.client.java.instrumentation.shared.ExternalServiceSharedUtils
import org.evomaster.client.java.instrumentation.shared.ExternalServiceSharedUtils.getWMDefaultSignature
import org.evomaster.core.Lazy
import org.evomaster.core.logging.LoggingUtil
import org.evomaster.core.problem.enterprise.auth.NoAuth
import org.evomaster.core.problem.externalservice.HostnameResolutionAction
import org.evomaster.core.problem.externalservice.HostnameResolutionInfo
import org.evomaster.core.problem.externalservice.httpws.service.HarvestActualHttpWsResponseHandler
import org.evomaster.core.problem.externalservice.httpws.service.HttpWsExternalServiceHandler
import org.evomaster.core.problem.externalservice.httpws.HttpExternalServiceInfo
import org.evomaster.core.problem.httpws.service.HttpWsFitness
import org.evomaster.core.problem.httpws.auth.HttpWsNoAuth
import org.evomaster.core.problem.rest.*
import org.evomaster.core.problem.rest.param.BodyParam
import org.evomaster.core.problem.rest.param.HeaderParam
import org.evomaster.core.problem.rest.param.QueryParam
import org.evomaster.core.problem.rest.param.UpdateForBodyParam
import org.evomaster.core.problem.util.ParserDtoUtil
import org.evomaster.core.remote.SutProblemException
import org.evomaster.core.remote.TcpUtils
import org.evomaster.core.search.action.Action
import org.evomaster.core.search.action.ActionResult
import org.evomaster.core.search.FitnessValue
import org.evomaster.core.search.GroupsOfChildren
import org.evomaster.core.search.Individual
import org.evomaster.core.search.action.ActionFilter
import org.evomaster.core.search.gene.*
import org.evomaster.core.search.gene.collection.EnumGene
import org.evomaster.core.search.gene.optional.OptionalGene
import org.evomaster.core.search.gene.string.StringGene
import org.evomaster.core.search.gene.utils.GeneUtils
import org.evomaster.core.taint.TaintAnalysis
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import javax.ws.rs.ProcessingException
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.client.Entity
import javax.ws.rs.client.Invocation
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.NewCookie
import javax.ws.rs.core.Response


abstract class AbstractRestFitness : HttpWsFitness<RestIndividual>() {

    // TODO: This will moved under ApiWsFitness once RPC and GraphQL support is completed
    @Inject
    protected lateinit var externalServiceHandler: HttpWsExternalServiceHandler

    @Inject
    protected lateinit var harvestResponseHandler: HarvestActualHttpWsResponseHandler

    companion object {
        private val log: Logger = LoggerFactory.getLogger(AbstractRestFitness::class.java)

        //see org.springframework.http.HttpHeaders

        val knownHttpHeaders = setOf(
            "Accept",
            "Accept-Charset",
            "Accept-Encoding",
            "Accept-Language",
            "Accept-Patch",
            "Accept-Ranges",
            "Access-Control-Allow-Credentials",
            "Access-Control-Allow-Headers",
            "Access-Control-Allow-Methods",
            "Access-Control-Allow-Origin",
            "Access-Control-Expose-Headers",
            "Access-Control-Max-Age",
            "Access-Control-Request-Headers",
            "Access-Control-Request-Method",
            "Age",
            "Allow",
            "Authorization",
            "Cache-Control",
            "Connection",
            "Content-Encoding",
            "Content-Disposition",
            "Content-Language",
            "Content-Length",
            "Content-Location",
            "Content-Range",
            "Content-Type",
            "Cookie",
            "Date",
            "ETag",
            "Expect",
            "Expires",
            "From",
            "Host",
            "If-Match",
            "If-Modified-Since",
            "If-None-Match",
            "If-Range",
            "If-Unmodified-Since",
            "Last-Modified",
            "Link",
            "Location",
            "Max-Forwards",
            "Origin",
            "Pragma",
            "Proxy-Authenticate",
            "Proxy-Authorization",
            "Range",
            "Referer",
            "Retry-After",
            "Server",
            "Set-Cookie",
            "Set-Cookie2",
            "TE",
            "Trailer",
            "Transfer-Encoding",
            "Upgrade",
            "User-Agent",
            "Vary",
            "Via",
            "Warning",
            "WWW-Authenticate"
        ).map { it.lowercase() }.toSet()
    }


    /**
     * Based on what executed by the test, we might need to add new genes to the individual.
     * This for example can happen if we detected that the test is using headers or query
     * params that were not specified in the Swagger schema
     */
    open fun expandIndividual(
        individual: RestIndividual,
        additionalInfoList: List<AdditionalInfoDto>,
        actionResults: List<ActionResult>
    ) {

        if (individual.seeAllActions().size < additionalInfoList.size) {
            /*
                Note: as not all actions might had been executed, it might happen that
                there are less Info than declared actions.
                But the other way round should not really happen
             */
            log.warn("Length mismatch between ${individual.seeAllActions().size} actions and ${additionalInfoList.size} info data")
            return
        }

        for (i in additionalInfoList.indices) {

            val action = individual.seeAllActions()[i]
            val info = additionalInfoList[i]

            if (action !is RestCallAction) {
                continue
            }

            val result = actionResults[i] as RestCallResult

            /*
                Those are OptionalGenes, which MUST be off by default.
                We are changing the genotype, but MUST not change the phenotype.
                Otherwise, the fitness value we just computed would be wrong.

                TODO: should update default action templates in Sampler
             */

            info.headers
                .filter { name ->
                    !action.parameters.any { it is HeaderParam && it.name.equals(name, ignoreCase = true) }
                }
                .filter {
                    //ignore common HTTP headers which could mess up the requests
                    !knownHttpHeaders.contains(it.lowercase())
                }
                .forEach {
                    val gene = StringGene(it)
                    action.addParam(
                        HeaderParam(it,
                            OptionalGene(it, gene, false, requestSelection = true).apply { doInitialize() })
                    )
                }

            info.queryParameters
                .filter { name ->
                    //if parameter already exists, do not add it again
                    !action.parameters.any { it is QueryParam && it.name.equals(name, ignoreCase = true) }
                }
                .filter { name ->
                    /*
                        This one is very tricky. Some JEE-based frameworks could conflate URL query parameters and
                         parameters in body payload of form submissions in x-www-form-urlencoded format.
                         This happens for example in LanguageTool.
                     */
                    !action.parameters.any{
                            b -> b is BodyParam && b.isForm()
                            && b.seeGenes().flatMap { it.flatView() }.any { it.name.equals(name, ignoreCase = true)  }
                    }
                }
                .filter{ name ->
                    /*
                        Another tricky case. Some frameworks like Spring can have hidden params to override the method
                        type of the requests. This is needed for handling web browsers without JS.
                        See https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/filter/HiddenHttpMethodFilter.html
                        This lead to meaningless tests with 405 responses.
                        So, we skip them.
                     */
                    name != "_method"
                }
                .forEach {
                    val gene = StringGene(it).apply { doInitialize(randomness) }
                    action.addParam(
                        QueryParam(it,
                            OptionalGene(it, gene, false, requestSelection = true).apply { doInitialize() })
                    )
                }

            if (result.getStatusCode() == 415) {
                /*
                    In theory, this should not happen.
                    415 means the media type of the sent payload is wrong.
                    There is no point for EvoMaster to do that, ie sending an XML to
                    an endpoint that expects a JSON.
                    Having such kind of test would be pretty pointless.

                    However, a POST/PUT could expect a payload and, if that is not specified
                    in OpenAPI, we could get a 415 when sending no data.
                 */
                if (action.parameters.none { it is BodyParam }) {

                    val obj = ObjectGene("body", listOf()).apply { doInitialize(randomness) }

                    val body = BodyParam(obj,
                        // TODO could look at "Accept" header instead of defaulting to JSON
                        EnumGene("contentType", listOf("application/json")).apply { doInitialize(randomness) })

                    val update = UpdateForBodyParam(body)

                    action.addParam(update)
                }
            }


            val dtoNames = info.parsedDtoNames

            val noBody = action.parameters.none { it is BodyParam }
            val emptyObject = !noBody &&
                    // this is the case of 415 handling
                    action.parameters.find { it is BodyParam }!!.let {
                        it.gene is ObjectGene && it.gene.fields.isEmpty()
                    }

            if (info.rawAccessOfHttpBodyPayload == true
                && dtoNames.isNotEmpty()
                && (noBody || emptyObject)
            ) {
                /*
                    The SUT tried to read the HTTP body payload, but there is no info
                    about it in the schema. This can happen when payloads are dynamically
                    loaded directly in the business logic of the SUT, and automated tools
                    like SpringDoc/SpringFox failed to infer what is read

                    TODO could handle other types besides JSON
                    TODO what to do if more than 1 DTO are registered?
                         Likely need a new MultiOptionGene similar to DisjunctionListRxGene
                 */
                if (dtoNames.size > 1) {
                    LoggingUtil.uniqueWarn(log, "More than 1 DTO option: [${dtoNames.sorted().joinToString(", ")}]")
                }
                val name = dtoNames.first()
                val obj = getObjectGeneForDto(name).apply { doInitialize(randomness) }

                val body = BodyParam(obj,
                    EnumGene("contentType", listOf("application/json")).apply { doInitialize(randomness) })
                val update = UpdateForBodyParam(body)
                action.addParam(update)
            }
        }
    }

    private fun getObjectGeneForDto(name: String): Gene {

        if (!infoDto.unitsInfoDto.parsedDtos.containsKey(name)) {
            /*
                parsedDto info is update throughout the search.
                so, if info is missing, we re-fetch the whole data.
                Would be more efficient to just fetch new data, but,
                as this will happen seldom (at most N times for N dtos),
                no much point in optimizing it
             */
            infoDto = rc.getSutInfo()!!

            if (!infoDto.unitsInfoDto.parsedDtos.containsKey(name)) {
                throw RuntimeException("BUG: info for DTO $name is not available in the SUT driver")
            }
        }

        return ParserDtoUtil.getOrParseDtoWithSutInfo(infoDto, config.enableSchemaConstraintHandling)[name]!!
    }

    /**
     * Create local targets for each HTTP status code in each
     * API entry point
     */
    fun handleResponseTargets(
        fv: FitnessValue,
        actions: List<RestCallAction>,
        actionResults: List<ActionResult>,
        additionalInfoList: List<AdditionalInfoDto>
    ) {

        (0 until actionResults.size)
            .filter { actions[it] is RestCallAction }
            .filter { actionResults[it] is RestCallResult }
            .forEach {
                val result = actionResults[it] as RestCallResult
                val status = result.getStatusCode() ?: -1
                val name = actions[it].getName()

                //objective for HTTP specific status code
                val statusId = idMapper.handleLocalTarget("$status:$name")
                fv.updateTarget(statusId, 1.0, it)

                val location5xx: String? = getlocation5xx(status, additionalInfoList, it, result, name)

                handleAdditionalStatusTargetDescription(fv, status, name, it, location5xx)

                if (config.expectationsActive) {
                    handleAdditionalOracleTargetDescription(fv, actions, result, name, it)
                }
            }
    }


    fun handleAdditionalOracleTargetDescription(
        fv: FitnessValue,
        actions: List<RestCallAction>,
        result: RestCallResult,
        name: String,
        indexOfAction: Int
    ) {
        /*
           Objectives for the two partial oracles implemented thus far.
        */
        val call = actions[indexOfAction] as RestCallAction
        val oracles = writer.getPartialOracles().activeOracles(call, result)
        oracles.filter { it.value }.forEach { entry ->
            val oracleId = idMapper.getFaultDescriptiveIdForPartialOracle("${entry.key} $name")
            val bugId = idMapper.handleLocalTarget(oracleId)
            fv.updateTarget(bugId, 1.0, indexOfAction)
        }
    }


    fun handleAdditionalStatusTargetDescription(
        fv: FitnessValue,
        status: Int,
        name: String,
        indexOfAction: Int,
        location5xx: String?
    ) {
        /*
           Objectives for results on endpoints.
           Problem: we might get a 4xx/5xx, but then no gradient to keep sampling for
           that endpoint. If we get 2xx, and full coverage, then no gradient to try
           to keep sampling that endpoint to get a 5xx
        */
        val okId = idMapper.handleLocalTarget("HTTP_SUCCESS:$name")
        val faultId = idMapper.handleLocalTarget("HTTP_FAULT:$name")

        //OK -> 5xx being better than 4xx, as code executed
        //FAULT -> 4xx worse than 2xx (can't find bugs if input is invalid)
        if (status in 200..299) {
            fv.updateTarget(okId, 1.0, indexOfAction)
            fv.updateTarget(faultId, 0.5, indexOfAction)
        } else if (status in 400..499) {
            fv.updateTarget(okId, 0.1, indexOfAction)
            fv.updateTarget(faultId, 0.1, indexOfAction)
        } else if (status in 500..599) {
            fv.updateTarget(okId, 0.5, indexOfAction)
            fv.updateTarget(faultId, 1.0, indexOfAction)
        }

        if (status == 500) {
            /*
                500 codes "might" be bugs. To distinguish between different bugs
                that crash the same endpoint, we need to know what was the last
                executed statement in the SUT.
                So, we create new targets for it.

                However, such info is missing in black-box testing
            */
            Lazy.assert {
                location5xx != null || config.blackBox
            }
            val postfix = if (location5xx == null) name else "${location5xx!!} $name"
            val descriptiveId = idMapper.getFaultDescriptiveIdFor500(postfix)
            val bugId = idMapper.handleLocalTarget(descriptiveId)
            fv.updateTarget(bugId, 1.0, indexOfAction)
        }
    }


    /**
     * @return whether the call was OK. Eg, in some cases, we might want to stop
     * the test at this action, and do not continue
     */
    protected fun handleRestCall(
        a: RestCallAction,
        actionResults: MutableList<ActionResult>,
        chainState: MutableMap<String, String>,
        cookies: Map<String, List<NewCookie>>,
        tokens: Map<String, String>
    ): Boolean {

        searchTimeController.waitForRateLimiter()

        val rcr = RestCallResult(a.getLocalId())
        actionResults.add(rcr)

        val response = try {
            createInvocation(a, chainState, cookies, tokens).invoke()
        } catch (e: ProcessingException) {

            log.debug("There has been an issue in the evaluation of a test: {}", e)

            /*
                this could have happened for example if call ends up in an infinite redirection loop.
                However, as we no longer follow 3xx automatically, it should not happen anymore
             */
            when {
                TcpUtils.isTooManyRedirections(e) -> {
                    rcr.setInfiniteLoop(true)
                    rcr.setErrorMessage(e.cause!!.message!!)
                    return false
                }

                TcpUtils.isTimeout(e) -> {
                    /*
                        This is very tricky. In theory it shouldn't happen that a REST call
                        does timeout (eg 10 seconds). But it might happen due to glitch,
                        or if very slow hardware. If it is a glitch, we do not want to
                        kill whole EM process, as might not happen again. If it is a
                        constant, we want to avoid using such test if possible, as it
                        would kill performance.
                        In any case, a generated test should never check assertions on time,
                        eg expect that a is SocketTimeoutException thrown. Not only because
                        maybe it was just a glitch, but also because the test might be run
                        on different machines (remote CI vs local development PC) with
                        different performance (and so the test would become flaky)
                     */
                    rcr.setTimedout(true)
                    statistics.reportTimeout()
                    return false
                }

                TcpUtils.isOutOfEphemeralPorts(e) -> {
                    /*
                        This could happen if for any reason we run out of ephemeral ports.
                        In such a case, we wait X seconds, and try again, as OS might have released ports
                        meanwhile.
                        And while we are at it, let's release any hanging network resource
                     */
                    client.close() //make sure to release any resource
                    client = ClientBuilder.newClient()

                    TcpUtils.handleEphemeralPortIssue()

                    createInvocation(a, chainState, cookies, tokens).invoke()
                }

                TcpUtils.isStreamClosed(e) || TcpUtils.isEndOfFile(e) -> {
                    /*
                        This should not really happen... but it does :( at least on Windows...
                     */
                    log.warn("TCP connection to SUT problem: ${e.cause!!.message}")
                    rcr.setTcpProblem(true)
                    return false
                }

                config.blackBox && TcpUtils.isRefusedConnection(e) -> {
                    /*
                        This might happen if we have wrong info of API location, eg host/servers in
                        the schema are wrong or static with hardcoded TCP ports
                     */
                    throw SutProblemException(
                        "Failed to connect API with TCP." +
                                " Is the API up and running at '${getBaseUrl()}' ?" +
                                " If not, the location can be overridden with --bbTargetUrl"
                    )
                }

                TcpUtils.isUnknownHost(e) -> {
                    throw SutProblemException("Unknown host: ${URL(getBaseUrl()).host}\n" +
                            " Are you sure you did not misspell it?")
                }

                TcpUtils.isInternalError(e) ->{
                    throw RuntimeException("Internal bug with EvoMaster when making a HTTP call toward ${a.resolvedPath()}", e)
                }

                else -> throw e
            }
        }

        rcr.setStatusCode(response.status)

        handlePossibleConnectionClose(response)

        try {
            if (response.hasEntity()) {
                if (response.mediaType != null) {
                    rcr.setBodyType(response.mediaType)
                }
                /*
                    FIXME should read as byte[]
                 */
                try {
                    val body = response.readEntity(String::class.java)

                    if (body.length < configuration.maxResponseByteSize) {
                        rcr.setBody(body)
                    } else {
                        LoggingUtil.uniqueWarn(
                            log,
                            "A very large response body was retrieved from the endpoint '${a.path}'." +
                                    " If that was expected, increase the 'maxResponseByteSize' threshold" +
                                    " in the configurations."
                        )
                        rcr.setTooLargeBody(true)
                    }
                } catch (e: OutOfMemoryError) {
                    /*
                        internal classes in JVM can throw this error directly, like
                        jdk.internal.util.ArraysSupport.hugeLength(...)
                        see:
                        https://github.com/EMResearch/EvoMaster/issues/449
                     */
                    LoggingUtil.uniqueWarn(
                        log,
                        "An extremely large response body was retrieved from the endpoint '${a.path}'." +
                                " So large that it cannot be handled inside the JVM in which EvoMaster is running."
                    )
                    rcr.setTooLargeBody(true)
                }
            }
        } catch (e: Exception) {

            if (e is ProcessingException && TcpUtils.isTimeout(e)) {
                rcr.setTimedout(true)
                statistics.reportTimeout()
                return false
            } else {
                log.warn("Failed to parse HTTP response: ${e.message}")
            }
        }

        if (response.status == 401 && a.auth !is NoAuth && !a.auth.requireMockHandling) {
            /*
                if the endpoint itself is to get auth info, we might exclude auth check for it
                eg,
                    the auth is Login with foo,
                    then the action is to Login with a generated account (eg bar)
                    thus, the response would likely be 401
             */
            if (!a.auth.excludeAuthCheck(a)) {
                //this would likely be a misconfiguration in the SUT controller
                log.warn("Got 401 although having auth for '${a.auth.name}'")
            }
        }


        if (!handleSaveLocation(a, response, rcr, chainState)) return false

        return true
    }


    private fun createInvocation(
        a: RestCallAction,
        chainState: MutableMap<String, String>,
        cookies: Map<String, List<NewCookie>>,
        tokens: Map<String, String>
    ): Invocation {

        val baseUrl = getBaseUrl()

        val path = a.resolvedPath()

        val locationHeader = if (a.locationId != null) {
            chainState[locationName(a.locationId!!)]
                ?: throw IllegalStateException("Call expected a missing chained 'location'")
        } else {
            null
        }

        val fullUri = EMTestUtils.resolveLocation(locationHeader, baseUrl + path)!!
            .let {
                /*
                    TODO this will be need to be done properly, and check if
                    it is or not a valid char.
                    Furthermore, likely needed to be done in resolveLocation,
                    or at least check how RestAssured would behave
                 */
                //it.replace("\"", "")
                GeneUtils.applyEscapes(it, GeneUtils.EscapeMode.URI, configuration.outputFormat)
            }


        val builder = if (a.produces.isEmpty()) {
            log.debug("No 'produces' type defined for {}", path)
            client.target(fullUri).request("*/*")

        } else {
            /*
                TODO: This only considers the first in the list of produced responses
                This is fine for endpoints that only produce one type of response.
                Could be a problem in future
            */
            client.target(fullUri).request(a.produces.first())
        }

        handleHeaders(a, builder, cookies, tokens)

        /*
            TODO: need to handle "accept" of returned resource
         */


        val body = a.parameters.find { p -> p is BodyParam }
        val forms = a.getBodyFormData()

        if (body != null && forms != null) {
            throw IllegalStateException("Issue in OpenAPI configuration: both Body and FormData definitions in the same endpoint")
        }

        val bodyEntity = if (body != null && body is BodyParam) {
            val mode = when {
                body.isJson() -> GeneUtils.EscapeMode.JSON
                body.isXml() -> GeneUtils.EscapeMode.XML
                body.isForm() -> GeneUtils.EscapeMode.X_WWW_FORM_URLENCODED
                body.isTextPlain() -> GeneUtils.EscapeMode.TEXT
                else -> throw IllegalStateException("Cannot handle body type: " + body.contentType())
            }
            Entity.entity(
                body.gene.getValueAsPrintableString(mode = mode, targetFormat = configuration.outputFormat),
                body.contentType()
            )
        } else if (forms != null) {
            Entity.entity(forms, MediaType.APPLICATION_FORM_URLENCODED_TYPE)
        } else if (a.verb == HttpVerb.PUT || a.verb == HttpVerb.PATCH) {
            /*
                PUT and PATCH must have a payload. But it might happen that it is missing in the Swagger schema
                when objects like WebRequest are used. So we default to urlencoded
             */
            Entity.entity("", MediaType.APPLICATION_FORM_URLENCODED_TYPE)
        } else if (a.verb == HttpVerb.POST && body == null) {
            /*
                POST does not enforce payload (isn't it?). However seen issues with Dotnet that gives
                411 if  Content-Length is missing...
             */
            //builder.header("Content-Length", 0)
            // null
            /*
                yet another critical bug in Jersey that it ignores that header (verified with WireShark)
             */
            Entity.entity("", MediaType.APPLICATION_FORM_URLENCODED_TYPE)
        } else {
            null
        }

        val invocation = when (a.verb) {
            HttpVerb.GET -> builder.buildGet()
            HttpVerb.POST -> builder.buildPost(bodyEntity)
            HttpVerb.PUT -> builder.buildPut(bodyEntity)
            HttpVerb.DELETE -> builder.buildDelete()
            HttpVerb.PATCH -> builder.build("PATCH", bodyEntity)
            HttpVerb.OPTIONS -> builder.build("OPTIONS")
            HttpVerb.HEAD -> builder.build("HEAD")
            HttpVerb.TRACE -> builder.build("TRACE")
        }
        return invocation
    }


    private fun handleSaveLocation(
        a: RestCallAction,
        response: Response,
        rcr: RestCallResult,
        chainState: MutableMap<String, String>
    ): Boolean {
        if (a.saveLocation) {

            if (response.statusInfo.family != Response.Status.Family.SUCCESSFUL) {
                /*
                    If this failed, and following actions require the "location" header
                    of this call, there is no point whatsoever to continue evaluating
                    the remaining calls
                 */
                rcr.stopping = true
                return false
            }

            val name = locationName(a.path.lastElement())
            var location = response.getHeaderString("location")

            if (location == null) {
                /*
                    Excluding bugs, this might happen if API was
                    designed to return the created resource, from
                    which an "id" can be extracted.
                    This is usually not a good practice, but it can
                    happen. So, here we "heuristically" (cannot be 100% sure)
                    check if this is indeed the case
                 */
                val id = rcr.getResourceId()

                if (id != null && hasParameterChild(a)) {
                    location = a.resolvedPath() + "/" + id
                    rcr.setHeuristicsForChainedLocation(true)
                }
            }

            //save location for the following REST calls
            chainState[name] = location ?: ""
        }
        return true
    }


    fun hasParameterChild(a: RestCallAction): Boolean {
        return sampler.seeAvailableActions()
            .filterIsInstance<RestCallAction>()
            .map { it.path }
            .any { it.isDirectChildOf(a.path) && it.isLastElementAParameter() }
    }

    private fun locationName(id: String): String {
        return "location_$id"
    }

    protected fun restActionResultHandling(
        individual: RestIndividual, targets: Set<Int>, allCovered: Boolean, actionResults: List<ActionResult>, fv: FitnessValue
    ): TestResultsDto? {

        if (actionResults.any { it is RestCallResult && it.getTcpProblem() }) {
            /*
                If there are socket issues, we avoid trying to compute any coverage.
                The caller might restart the SUT and try again.
                Hopefully, this should be just a glitch...
                TODO if we see this happening often, we need to find a proper solution.
                For example, we could re-run the test, and see if this one always fails,
                while others in the archive do pass.
                It could be handled specially in the archive.
             */
            return null
        }

        val dto = updateFitnessAfterEvaluation(targets, allCovered, individual, fv)
            ?: return null

        handleExtra(dto, fv)

        handleResponseTargets(
            fv,
            individual.seeAllActions().filterIsInstance<RestCallAction>(),
            actionResults,
            dto.additionalInfoList
        )

        handleExternalServiceInfo(individual, fv, dto.additionalInfoList)

        if(! allCovered) {
            if (config.expandRestIndividuals) {
                expandIndividual(individual, dto.additionalInfoList, actionResults)
            }

            if (config.isEnabledTaintAnalysis()) {
                Lazy.assert { actionResults.size == dto.additionalInfoList.size }
                //TODO add taint analysis for resource-based solution
                TaintAnalysis.doTaintAnalysis(
                    individual,
                    dto.additionalInfoList,
                    randomness,
                    config.enableSchemaConstraintHandling
                )
            }
        }

        return dto
    }

    /**
     * Based on info coming from SUT execution, register and start new WireMock instances.
     *
     * TODO push this thing up to hierarchy to EntepriseFitness
     */
    private fun handleExternalServiceInfo(individual: RestIndividual, fv: FitnessValue, infoDto: List<AdditionalInfoDto>) {

        /*
            Note: this info here is based from what connections / hostname resolving done in the SUT,
            via instrumentation.

            However, what is actually called on an already up and running WM instance from a previous call is
            done on WM directly, and it must be done at SUT call (as WM get reset there)
         */

        infoDto.forEachIndexed { index, info ->
            info.hostnameResolutionInfoDtos.forEach { hn ->

                val dns = HostnameResolutionInfo(
                    hn.remoteHostname,
                    hn.resolvedAddress
                )
                externalServiceHandler.addHostname(dns)

                if(dns.isResolved()){
                    /*
                        We need to ask, are we in that special case in which a hostname was resolved but there is
                        no action for it?
                        that would represent a real website resolution, which we cannot allow in the generated tests.
                        for this reason, in instrumentation, we redirect toward a RESERVED IP address.
                        to guarantee such behavior in generated tests, where there is instrumentation, we need to modify
                        the genotype of this evaluated individual (without modifying its phenotype)
                     */
                    val actions = individual.seeActions(ActionFilter.ONLY_DNS) as List<HostnameResolutionAction>
                    if ((actions.isEmpty() || actions.none{ it.hostname == hn.remoteHostname}) &&
                        // To avoid adding action for the local WM address in case of InetAddress used
                        // in the case study.
                        !externalServiceHandler.isWireMockAddress(hn.remoteHostname)){
                        // OK, we are in that special case
                        val hra = HostnameResolutionAction(hn.remoteHostname, ExternalServiceSharedUtils.RESERVED_RESOLVED_LOCAL_IP)
                        individual.addChildToGroup(hra, GroupsOfChildren.INITIALIZATION_DNS)
                        // TODO: Above line adds unnecessary tests at the end, which is
                        //  causing the created tests to fail.
                        //  Now handling in Mutator, removing existing actions with default IP address for the same hostname.

                    }
                }
            }

            info.externalServices.forEach { es ->

                /*
                    The info here is coming from SUT instrumentation
                 */

                /*
                    TODO: check, do we really want to start WireMock instances right now after a fitness evaluation?
                    We need to make sure then, if we do this, that a call in instrumented SUT with (now) and
                    without (previous fitness evaluation) WM instances would result in same behavior.

                    TODO: ie make sure that, if test is executed again now, the behavior is the same
                 */
                externalServiceHandler.addExternalService(
                    HttpExternalServiceInfo(
                        es.protocol,
                        es.remoteHostname,
                        es.remotePort
                    )
                )
            }


            // register the external service info which re-direct to the default WM
            fv.registerExternalRequestToDefaultWM(
                index,
                info.employedDefaultWM.associate { it ->
                    val signature = getWMDefaultSignature(it.protocol, it.remotePort)
                    it.remoteHostname to externalServiceHandler.getExternalService(signature)
                }
            )
        }
    }

    override fun getActionDto(action: Action, index: Int): ActionDto {
        val actionDto = super.getActionDto(action, index)
        // TODO: Need to move under ApiWsFitness after the GraphQL and RPC support is completed
        if (index == 0) {
            actionDto.externalServiceMapping = externalServiceHandler.getExternalServiceMappings()
            actionDto.localAddressMapping = externalServiceHandler.getLocalDomainNameMapping()
            actionDto.skippedExternalServices = externalServiceHandler.getSkippedExternalServices()
        }
        return actionDto
    }
}
