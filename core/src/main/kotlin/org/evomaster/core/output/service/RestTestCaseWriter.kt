package org.evomaster.core.output.service

import com.google.inject.Inject
import org.evomaster.core.EMConfig
import org.evomaster.core.output.Lines
import org.evomaster.core.output.SqlWriter
import org.evomaster.core.problem.httpws.HttpWsAction
import org.evomaster.core.problem.httpws.HttpWsCallResult
import org.evomaster.core.problem.rest.RestCallAction
import org.evomaster.core.problem.rest.RestCallResult
import org.evomaster.core.problem.rest.RestIndividual
import org.evomaster.core.search.action.Action
import org.evomaster.core.search.action.ActionResult
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.Individual
import org.evomaster.core.search.gene.utils.GeneUtils
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.*

class RestTestCaseWriter : HttpWsTestCaseWriter {

    companion object {
        private val log = LoggerFactory.getLogger(RestTestCaseWriter::class.java)
    }

    @Inject
    private lateinit var partialOracles: PartialOracles

    constructor() : super()

    /**
     * ONLY for tests
     */
    constructor(config: EMConfig, partialOracles: PartialOracles) : super() {
        this.config = config
        this.partialOracles = partialOracles
    }


    /**
     * When we make a HTTP call, do we need to store the response in a variable for following HTTP calls
     * or to create assertions on it?
     */
    override fun needsResponseVariable(call: HttpWsAction, res: HttpWsCallResult): Boolean {

        return super.needsResponseVariable(call, res) ||
                (config.expectationsActive && partialOracles.generatesExpectation(call as RestCallAction, res))
                || ((call as RestCallAction).saveLocation && !res.stopping)
    }

    override fun handleFieldDeclarations(
        lines: Lines,
        baseUrlOfSut: String,
        ind: EvaluatedIndividual<*>,
        insertionVars: MutableList<Pair<String, String>>
    ) {
        super.handleFieldDeclarations(lines, baseUrlOfSut, ind, insertionVars)

        if (shouldCheckExpectations()) {
            addDeclarationsForExpectations(lines, ind as EvaluatedIndividual<RestIndividual>)
            //TODO: -> also check expectation generation before adding declarations
        }

        if (hasChainedLocations(ind.individual)) {
            assert(ind.individual is RestIndividual)
            /*
                If the "location" header of a HTTP response is used in a following
                call, we need to save it in a variable.
                We declare all such variables at the beginning of the test.

                TODO: rather declare variable first time we access it?
             */
            lines.addEmpty()

            ind.evaluatedMainActions().asSequence()
                .map { it.action }
                .filterIsInstance(RestCallAction::class.java)
                .filter { it.locationId != null }
                .map { it.locationId }
                .distinct()
                .forEach { id ->
                    val name = locationVar(id!!)
                    when {
                        format.isJava() -> lines.add("String $name = \"\";")
                        format.isKotlin() -> lines.add("var $name : String? = \"\"")
                        format.isJavaScript() -> lines.add("let $name = \"\";")
                        format.isCsharp() -> lines.add("var $name = \"\";")
                        // should never happen
                        else -> throw IllegalStateException("Unsupported format $format")
                    }
                }
        }
    }

    override fun handleActionCalls(
            lines: Lines,
            baseUrlOfSut: String,
            ind: EvaluatedIndividual<*>,
            insertionVars: MutableList<Pair<String, String>>,
            testCaseName: String,
            testSuitePath: Path?
    ) {
        //SQL actions are generated in between
        if (ind.individual is RestIndividual) {
            if((ind.individual as RestIndividual).getResourceCalls().isNotEmpty()){
                ind.evaluatedResourceActions().forEachIndexed { index, c ->
                    // db
                    if (c.first.isNotEmpty())
                        SqlWriter.handleDbInitialization(
                            format,
                            c.first,
                            lines,
                            ind.individual.seeSqlDbActions(),
                            groupIndex = index.toString(),
                            insertionVars = insertionVars,
                            skipFailure = config.skipFailureSQLInTestFile
                        )
                    //actions
                    c.second.forEach { a ->
                        val exeuctionIndex = ind.individual.seeMainExecutableActions().indexOf(a.action)
                        handleSingleCall(a, exeuctionIndex, ind.fitness, lines, testCaseName, testSuitePath, baseUrlOfSut)
                    }
                }
            }else{
                ind.evaluatedMainActions().forEachIndexed { index, evaluatedAction ->
                    handleSingleCall(evaluatedAction, index, ind.fitness, lines, testCaseName, testSuitePath, baseUrlOfSut)
                }
            }

        }
    }

    protected fun locationVar(id: String): String {
        //TODO make sure name is syntactically valid
        //TODO use counters to make them unique
        return "location_${id.trim().replace(" ", "_")}"
    }


    /**
     * Check if any action requires a chain based on location headers:
     * eg a POST followed by a GET on the created resource
     */
    private fun hasChainedLocations(individual: Individual): Boolean {
        return individual.seeAllActions().any { a ->
            a is RestCallAction && a.isLocationChained()
        }
    }


    override fun addActionLines(action: Action, index: Int, testCaseName: String, lines: Lines, result: ActionResult, testSuitePath: Path?, baseUrlOfSut: String) {
        addRestCallLines(action as RestCallAction, lines, result as RestCallResult, baseUrlOfSut)
    }

    private fun addRestCallLines(
        call: RestCallAction,
        lines: Lines,
        res: RestCallResult,
        baseUrlOfSut: String
    ) {

        val responseVariableName = makeHttpCall(call, lines, res, baseUrlOfSut)

        handleLocationHeader(call, res, responseVariableName, lines)
        handleResponseAfterTheCall(call, res, responseVariableName, lines)

        if (shouldCheckExpectations() && !res.failedCall()) {
            handleExpectationSpecificLines(call, lines, res, responseVariableName)
        }
    }


    private fun shouldCheckExpectations() =
    //for now Expectations are only supported on the JVM
        //TODO C# (and maybe JS as well???)
        config.expectationsActive && config.outputFormat.isJavaOrKotlin()


    override fun handleVerbEndpoint(baseUrlOfSut: String, _call: HttpWsAction, lines: Lines) {

        val call = _call as RestCallAction
        val verb = call.verb.name.lowercase(Locale.getDefault())

        if (format.isCsharp()) {
            lines.append(".${capitalizeFirstChar(verb)}Async(")
        } else {
            if (verb == "trace" && format.isJavaOrKotlin()) {
                //currently, RestAssured does not have a trace() method
                lines.add(".request(io.restassured.http.Method.TRACE, ")
            } else {
                lines.add(".$verb(")
            }
        }

        if (call.locationId != null) {
            if (format.isJavaScript()) {
                lines.append("${TestSuiteWriter.jsImport}.")
            }

            if (format.isCsharp()) {
                //TODO: double check this
                lines.append("${locationVar(call.locationId!!)} + $baseUrlOfSut + \"${call.resolvedPath()}\"")
            } else {
                lines.append("resolveLocation(${locationVar(call.locationId!!)}, $baseUrlOfSut + \"${call.resolvedPath()}\")")
            }

        } else {

            if (format.isKotlin()) {
                lines.append("\"\${$baseUrlOfSut}")
            } else {
                lines.append("$baseUrlOfSut + \"")
            }

            if (call.path.numberOfUsableQueryParams(call.parameters) <= 1) {
                val uri = call.path.resolve(call.parameters)
                lines.append("${GeneUtils.applyEscapes(uri, mode = GeneUtils.EscapeMode.URI, format = format)}\"")
            } else {
                //several query parameters. lets have them one per line
                val path = call.path.resolveOnlyPath(call.parameters)
                val elements = call.path.resolveOnlyQuery(call.parameters)

                lines.append("$path?\" + ")

                lines.indented {
                    (0 until elements.lastIndex).forEach { i ->
                        lines.add(
                            "\"${
                                GeneUtils.applyEscapes(
                                    elements[i],
                                    mode = GeneUtils.EscapeMode.SQL,
                                    format = format
                                )
                            }&\" + "
                        )
                    }
                    lines.add(
                        "\"${
                            GeneUtils.applyEscapes(
                                elements.last(),
                                mode = GeneUtils.EscapeMode.SQL,
                                format = format
                            )
                        }\""
                    )
                }
            }
        }

        if (format.isCsharp()) {
            if (isVerbWithPossibleBodyPayload(verb)) {
                lines.append(", ")
                handleBody(call, lines)
            }
            lines.append(");")
        } else {
            lines.append(")")
        }
    }

    override fun getAcceptHeader(call: HttpWsAction, res: HttpWsCallResult): String {
        return getRestAcceptHeader(call as RestCallAction, res as RestCallResult)
    }

    private fun getRestAcceptHeader(call: RestCallAction, res: RestCallResult): String {
        /*
         *  Note: using the type in result body is wrong:
         *  if you request a JSON but make an error, you might
         *  get back a text/plain with an explanation
         *
         *  TODO: get the type from the REST call
         */

        val accept = openAcceptHeader()

        var result: String

        if (call.produces.isEmpty() || res.getBodyType() == null) {
            result = "$accept\"*/*\""
        } else {

            val accepted = call.produces.filter { res.getBodyType().toString().contains(it, true) }

            result = if (accepted.size == 1) {
                "$accept\"${accepted.first()}\""
            } else {
                //FIXME: there seems to have been something or a problem
                "$accept\"*/*\""
            }
        }

        result = closeAcceptHeader(result)

        return result
    }


    private fun handleLocationHeader(call: RestCallAction, res: RestCallResult, resVarName: String, lines: Lines) {
        if (call.saveLocation && !res.stopping) {

            if (!res.getHeuristicsForChainedLocation()) {

                val location = locationVar(call.path.lastElement())

                /*
                    If there is a "location" header, then it must be either empty or a valid URI.
                    If that is not the case, it would be a bug.
                    But we do not really handle it as "found fault" during the search.
                    Plus the test should not fail, although clearly a bug.
                    But in any case, if invalid URL, following HTTP calls would fail anyway

                    FIXME: should handle it as an extra oracle during the search
                 */

                when {
                    format.isJavaOrKotlin() -> {
                        val extract = "$resVarName.extract().header(\"location\")"
                        lines.add("$location = $extract")
                        lines.appendSemicolon(format)
                        lines.add("assertTrue(isValidURIorEmpty($location));")
                    }
                    format.isJavaScript() -> {
                        lines.add("$location = $resVarName.header['location'];")
                        val validCheck = "${TestSuiteWriter.jsImport}.isValidURIorEmpty($location)"
                        lines.add("expect($validCheck).toBe(true);")
                    }
                    format.isCsharp() -> {
                        lines.add("Assert.True(Uri.IsWellFormedUriString($location, UriKind.Absolute) || string.IsNullOrEmpty($location));")
                    }
                }
            } else {

                val extraTypeInfo = when {
                    format.isKotlin() -> "<Object>"
                    else -> ""
                }
                val baseUri: String = if (call.locationId != null) {
                    /* A variable should NOT be enclosed by quotes */
                    locationVar(call.locationId!!)
                } else {
                    /* Literals should be enclosed by quotes */
                    "\"${call.path.resolveOnlyPath(call.parameters)}\""
                }

                //TODO JS and C#
                val extract =
                    "$resVarName.extract().body().path$extraTypeInfo(\"${res.getResourceIdName()}\").toString()"

                lines.add("${locationVar(call.path.lastElement())} = $baseUri + \"/\" + $extract")
                lines.appendSemicolon(format)
            }
        }
    }

    private fun addDeclarationsForExpectations(lines: Lines, individual: EvaluatedIndividual<RestIndividual>) {
        if (!partialOracles.generatesExpectation(individual)) {
            return
        }

        if (!format.isJavaOrKotlin()) {
            //TODO will need to see if going to support JS and C# as well
            return
        }

        lines.addEmpty()
        when {
            format.isJava() -> lines.append("ExpectationHandler expectationHandler = expectationHandler()")
            format.isKotlin() -> lines.append("val expectationHandler: ExpectationHandler = expectationHandler()")
        }
        lines.appendSemicolon(format)
    }

    private fun handleExpectationSpecificLines(call: RestCallAction, lines: Lines, res: RestCallResult, name: String) {
        lines.addEmpty()
        if (partialOracles.generatesExpectation(call, res)) {
            partialOracles.addExpectations(call, lines, res, name, format)
        }
    }
}
