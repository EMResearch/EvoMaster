package org.evomaster.core.output.service

import com.google.gson.Gson
import org.evomaster.core.logging.LoggingUtil
import org.evomaster.core.output.CookieWriter
import org.evomaster.core.output.Lines
import org.evomaster.core.output.OutputFormat
import org.evomaster.core.output.TokenWriter
import org.evomaster.core.output.formatter.OutputFormatter
import org.evomaster.core.problem.httpws.service.HttpWsAction
import org.evomaster.core.problem.httpws.service.HttpWsCallResult
import org.evomaster.core.problem.rest.RestCallResult
import org.evomaster.core.problem.rest.param.BodyParam
import org.evomaster.core.problem.rest.param.HeaderParam
import org.evomaster.core.search.Action
import org.evomaster.core.search.ActionResult
import org.evomaster.core.search.EvaluatedAction
import org.evomaster.core.search.gene.GeneUtils
import org.slf4j.LoggerFactory
import java.lang.NumberFormatException
import javax.ws.rs.core.MediaType

abstract class HttpWsTestCaseWriter : WebTestCaseWriter() {

    companion object {
        private val log = LoggerFactory.getLogger(HttpWsTestCaseWriter::class.java)
    }

    abstract fun getAcceptHeader(call: HttpWsAction, res: HttpWsCallResult): String


    override fun shouldFailIfException(result: ActionResult): Boolean {
        /*
            Fail test if exception is not thrown, but not if it was a timeout,
            otherwise the test would become flaky
        */
        return !(result as HttpWsCallResult).getTimedout()
    }




    protected fun handlePreCallSetup(call: HttpWsAction, lines: Lines, res: HttpWsCallResult){
        /*
            This is needed when we need to execute some code before each HTTP call.
            Cannot be in @Before/Fixture, as it must be done for each HTTP call , and not
            just once for test
         */

        if(format.isCsharp()){
            lines.add("Client.DefaultRequestHeaders.Clear();")
            lines.add(getAcceptHeader(call, res))
            lines.addEmpty()
        }
    }

    protected fun handleFirstLine(call: HttpWsAction, lines: Lines, res: HttpWsCallResult, resVarName: String) {

        lines.addEmpty()

        handlePreCallSetup(call, lines, res)

        if (needsResponseVariable(call, res) && !res.failedCall()) {
            when {
                format.isKotlin() -> lines.append("val $resVarName: ValidatableResponse = ")
                format.isJava() -> lines.append("ValidatableResponse $resVarName = ")
                format.isJavaScript() -> lines.append("const $resVarName = ")
                format.isCsharp() -> lines.append("var $resVarName = ")
            }
        }

        when {
            format.isJavaOrKotlin() -> lines.append("given()")
            format.isJavaScript() -> lines.append("await superagent")
            format.isCsharp() -> lines.append("await Client")
            format.isPython() -> {
                if (config.blackBox) {
                    lines.append("$resVarName = requests \\")
                } else {
                    lines.append("$resVarName = self.test_client \\")
                }
            }
        }

        if (!format.isJavaScript() && !format.isCsharp() && !format.isPython()) {
            // in JS, the Accept must be after the verb
            // in C#, must be before the call
            lines.append(getAcceptHeader(call, res))
        }
    }


    protected fun isVerbWithPossibleBodyPayload(verb: String): Boolean {

        val verbs = arrayOf("post", "put", "patch")

        if (verbs.contains(verb.toLowerCase()))
            return true;
        return false;
    }

    protected fun openAcceptHeader(): String {
        return when {
            format.isJavaOrKotlin() -> ".accept("
            format.isJavaScript() -> ".set('Accept', "
            format.isCsharp() -> "Client.DefaultRequestHeaders.Add(\"Accept\", "
            format.isPython() -> "headers['Accept'] = "
            else -> throw IllegalArgumentException("Invalid format: $format")
        }
    }

    protected fun closeAcceptHeader(openedHeader: String): String {
        var result = openedHeader
        val end = when {
            format.isPython() -> ""
            else -> ")"
        }
        result += end
        if (format.isCsharp()) result = "$result;"
        return result
    }



    open fun needsResponseVariable(call: HttpWsAction, res: HttpWsCallResult): Boolean {
        /*
          Bit tricky... when using RestAssured on JVM, we can assert directly on the call...
          but that is not the case for the other libraries used for example in JS and C#
         */
        return config.outputFormat == OutputFormat.JS_JEST
                || config.outputFormat == OutputFormat.CSHARP_XUNIT
                || config.outputFormat == OutputFormat.PYTHON_UNITTEST
    }

    protected fun handleHeaders(call: HttpWsAction, lines: Lines) {

        if(format.isCsharp()){
            //FIXME
            log.warn("Currently not handling headers in C#")
            return
        }

        val prechosenAuthHeaders = call.auth.headers.map { it.name }

        val set = when {
            format.isJavaOrKotlin() -> "header"
            format.isJavaScript() -> "set"
            format.isPython() -> ""
            //TODO C#
            else -> throw IllegalArgumentException("Not supported format: $format")
        }

        if (format.isPython()) {
            lines.add("headers = {}")
        }

        call.auth.headers.forEach {
            if (format.isPython()) {
                lines.add("headers[\"${it.name}\"] = \"${it.value}\"")
            } else {
                lines.add(".$set(\"${it.name}\", \"${it.value}\") // ${call.auth.name}")
            }
        }

        call.parameters.filterIsInstance<HeaderParam>()
                .filter { !prechosenAuthHeaders.contains(it.name) }
                .filter { !(call.auth.jsonTokenPostLogin != null && it.name.equals("Authorization", true)) }
                .forEach {
                    if (format.isPython()) {
                        lines.add("headers[\"${it.name}\"] = ${it.gene.getValueAsPrintableString(targetFormat = format)}")
                    } else {
                        val x = it.gene.getValueAsRawString()
                        lines.add(".$set(\"${it.name}\", \"${GeneUtils.applyEscapes(x, GeneUtils.EscapeMode.BODY, format)}\")")
                    }
                }

        val cookieLogin = call.auth.cookieLogin
        if (cookieLogin != null) {
            when {
                format.isJavaOrKotlin() -> lines.add(".cookies(${CookieWriter.cookiesName(cookieLogin)})")
                format.isJavaScript() -> lines.add(".set('Cookies', ${CookieWriter.cookiesName(cookieLogin)})")
                //TODO C#
                //TODO PYTHON
            }
        }

        //TODO make sure header was not already set
        val tokenLogin = call.auth.jsonTokenPostLogin
        if (tokenLogin != null) {
            lines.add(".$set(\"Authorization\", ${TokenWriter.tokenName(tokenLogin)}) // ${call.auth.name}")
        }
    }



    protected fun handleResponseAfterTheCall(call: HttpWsAction, res: HttpWsCallResult, responseVariableName: String, lines: Lines) {

        if (format.isJavaOrKotlin() //assertions handled in the call
                || !needsResponseVariable(call, res)
                || res.failedCall()
        ) {
            return
        }

        lines.addEmpty()

        val code = res.getStatusCode()

        when {
            format.isJavaScript() -> {
                lines.add("expect($responseVariableName.status).toBe($code);")
            }
            format.isCsharp() -> {
                lines.add("Assert.Equal($code, (int) $responseVariableName.StatusCode);")
            }
            format.isPython() -> {
                lines.add("assert $responseVariableName.status_code == $code")
            }
            else -> {
                LoggingUtil.uniqueWarn(log, "No status assertion supported for format $format")
            }
        }

        handleLastStatementComment(res, lines)

        if (config.enableBasicAssertions) {
            if (format.isPython()) {
                handlePythonResponseContents(lines, res, responseVariableName)
            } else {
                handleResponseAssertions(lines, res, responseVariableName)
            }
        }
    }

    protected open fun handleLastStatementComment(res: HttpWsCallResult, lines: Lines){
        val code = res.getStatusCode()
        if (code == 500 && ! config.blackBox) {
            val comment = if (format.isPython()) "#" else "//"
            lines.append(" $comment " + res.getLastStatementWhen500())
        }
    }

    protected fun handleSingleCall(
            evaluatedAction: EvaluatedAction,
            lines: Lines,
            baseUrlOfSut: String
    ) {
        lines.addEmpty()

        val call = evaluatedAction.action as HttpWsAction
        val res = evaluatedAction.result as HttpWsCallResult

        if (res.failedCall()) {
            addActionInTryCatch(call, lines, res, baseUrlOfSut)
        } else {
            addActionLines(call, lines, res, baseUrlOfSut)
        }
    }

    protected fun makeHttpCall(call: HttpWsAction, lines: Lines, res: HttpWsCallResult, baseUrlOfSut: String): String {
        //first handle the first line
        val responseVariableName = createUniqueResponseVariableName()

        if (format.isPython()) {
            handleHeaders(call, lines)
            handleBody(call, lines)
            lines.add(getAcceptHeader(call, res))
        }

        handleFirstLine(call, lines, res, responseVariableName)

        when {
            format.isJavaOrKotlin() -> {
                lines.indent(2)
                handleHeaders(call, lines)
                handleBody(call, lines)
                handleVerbEndpoint(baseUrlOfSut, call, lines)
            }
            format.isJavaScript() -> {
                lines.indent(2)
                //in SuperAgent, verb must be first
                handleVerbEndpoint(baseUrlOfSut, call, lines)
                lines.append(getAcceptHeader(call, res))
                handleHeaders(call, lines)
                handleBody(call, lines)
            }
            format.isCsharp() -> {
                handleVerbEndpoint(baseUrlOfSut, call, lines)
                //TODO headers
            }
            format.isPython() -> {
                lines.indent(2)
                handleVerbEndpoint(baseUrlOfSut, call, lines)
                lines.deindent(2)
            }
        }

        if (format.isJavaOrKotlin()) {
            handleResponseDirectlyInTheCall(call, res, lines)
        }
        handleLastLine(call, res, lines, responseVariableName)
        return responseVariableName
    }



    abstract fun handleVerbEndpoint(baseUrlOfSut: String, _call: HttpWsAction, lines: Lines)

    protected fun sendBodyCommand() : String{
        return when {
            format.isJavaOrKotlin() -> "body"
            format.isJavaScript() -> "send"
            format.isCsharp() -> ""
            format.isPython() -> ""
            else -> throw IllegalArgumentException("Format not supported $format")
        }
    }

    //TODO: check again for C#, especially when not json
    protected open fun handleBody(call: HttpWsAction, lines: Lines) {

        val bodyParam = call.parameters.find { p -> p is BodyParam } as BodyParam?


        if(format.isCsharp() && bodyParam == null){
            lines.append("null")
            return
        }

        if (bodyParam != null) {

            val send = sendBodyCommand()

            if (format.isPython()) {
                lines.add("body = {}")
            }

            when {
                format.isJavaOrKotlin() -> lines.add(".contentType(\"${bodyParam.contentType()}\")")
                format.isJavaScript() -> lines.add(".set('Content-Type','${bodyParam.contentType()}')")
                format.isPython() -> lines.add("headers['Content-Type'] = \"${bodyParam.contentType()}\"")
                //FIXME
            //format.isCsharp() -> lines.add("Client.DefaultRequestHeaders.Accept.Add(new MediaTypeWithQualityHeaderValue(\"${bodyParam.contentType()}\"));")
            }

            if (bodyParam.isJson()) {

                val json = bodyParam.gene.getValueAsPrintableString(mode = GeneUtils.EscapeMode.JSON, targetFormat = format)

                 printSendJsonBody(json, lines)

            } else if (bodyParam.isTextPlain()) {
                val body =
                        bodyParam.gene.getValueAsPrintableString(mode = GeneUtils.EscapeMode.TEXT, targetFormat = format)
                if (body != "\"\"") {
                    when {
                        format.isCsharp() -> {
                            lines.append("new StringContent(\"$body\", Encoding.UTF8, \"${bodyParam.contentType()}\")")
                        }
                        format.isPython() -> {
                            lines.add("body = $body")
                        }
                        else -> lines.add(".$send($body)")
                    }
                } else {
                    when {
                        format.isCsharp() -> {
                            lines.append("new StringContent(\"${"""\"\""""}\", Encoding.UTF8, \"${bodyParam.contentType()}\")")
                        }
                        format.isPython() -> {
                            lines.add("body = $body")
                        }
                        else -> lines.add(".$send(\"${"""\"\""""}\")")
                    }
                }

                //BMR: this is needed because, if the string is empty, it causes a 400 (bad request) code on the test end.
                // inserting \"\" should prevent that problem
                // TODO: get some tests done of this

            } else if (bodyParam.isForm()) {
                val body = bodyParam.gene.getValueAsPrintableString(
                        mode = GeneUtils.EscapeMode.X_WWW_FORM_URLENCODED,
                        targetFormat = format
                )
                when {
                    format.isCsharp() -> {
                        lines.append("new StringContent(\"$body\", Encoding.UTF8, \"${bodyParam.contentType()}\")")
                    }
                    format.isPython() -> {
                        lines.add("body = $body")
                    }
                    else -> lines.add(".$send(\"$body\")")
                }

            } else {
                //TODO XML
                LoggingUtil.uniqueWarn(log, "Unhandled type for body payload: " + bodyParam.contentType())
            }
        }
    }

    fun printSendJsonBody(json: String, lines: Lines) {

        val send = sendBodyCommand()

        val body = if (OutputFormatter.JSON_FORMATTER.isValid(json)) {
            OutputFormatter.JSON_FORMATTER.getFormatted(json)
        } else {
            json
        }

        //needed as JSON uses ""
        val bodyLines = body.split("\n").map { s ->
            "\" " + GeneUtils.applyEscapes(s.trim(), mode = GeneUtils.EscapeMode.BODY, format = format) + " \""
        }

        if (bodyLines.size == 1) {
            when {
                format.isCsharp() -> {
                    lines.add("new StringContent(${bodyLines.first()}, Encoding.UTF8, \"application/json\")")
                }
                format.isPython() -> {
                    lines.add("body = ${bodyLines.first()}")
                }
                else -> {
                    lines.add(".$send(${bodyLines.first()})")
                }
            }
        } else {
            when {
                format.isCsharp() -> {
                    lines.add("new StringContent(")
                    lines.add("${bodyLines.first()} +")
                    lines.indented {
                        (1 until bodyLines.lastIndex).forEach { i ->
                            lines.add("${bodyLines[i]} + ")
                        }
                        lines.add("${bodyLines.last()}")
                    }
                    lines.add(", Encoding.UTF8, \"application/json\")")
                }
                format.isPython() -> {
                    lines.add("body = ${bodyLines.first()} + \\")
                    lines.indented {
                        (1 until bodyLines.lastIndex).forEach { i ->
                            lines.add("${bodyLines[i]} + \\")
                        }
                        lines.add("${bodyLines.last()}")
                    }
                }
                else -> {
                    lines.add(".$send(${bodyLines.first()} + ")
                    lines.indented {
                        (1 until bodyLines.lastIndex).forEach { i ->
                            lines.add("${bodyLines[i]} + ")
                        }
                        lines.add("${bodyLines.last()})")
                    }
                }
            }
        }
    }

    /**
     * This is done mainly for RestAssured
     */
    protected fun handleResponseDirectlyInTheCall(call: HttpWsAction, res: HttpWsCallResult, lines: Lines) {
        if (!res.failedCall()) {

            val code = res.getStatusCode()

            when {
                format.isJavaOrKotlin() -> {
                    lines.add(".then()")
                    lines.add(".statusCode($code)")
                }
                else -> throw IllegalStateException("No assertion in calls for format: $format")
            }

            handleLastStatementComment(res, lines)

            if (config.enableBasicAssertions) {
                handleResponseAssertions(lines, res, null)
            }

        }

//        else if (partialOracles.generatesExpectation(call, res)
//                && format.isJavaOrKotlin()){
//            //FIXME what is this for???
//            lines.add(".then()")
//        }
    }

    //----------------------------------------------------------------------------------------
    // assertion lines

    protected fun handleResponseAssertions(lines: Lines, res: HttpWsCallResult, responseVariableName: String?) {

        assert(responseVariableName != null || format.isJavaOrKotlin())

        /*
            there are 2 cases:
            a) assertions directly as part of the HTTP call, eg, as done in RestAssured
            b) assertions on response object, stored in a variable after the HTTP call

            based on this, the code to add is quite different.
            Note, in case of (b), we must have the name of the variable
         */
        val isInCall = responseVariableName == null

        if (isInCall) {
            lines.add(".assertThat()")
        }

        if (res.getBodyType() == null) {
            lines.add(emptyBodyCheck(responseVariableName))
        } else {

            //TODO is there a better solution? where was this a problem?
            val bodyTypeSimplified = res.getBodyType()
                    .toString()
                    .split(";") // remove all associated variables
                    .first()

            val instruction = when {
                format.isJavaOrKotlin() -> ".contentType(\"$bodyTypeSimplified\")"
                format.isJavaScript() ->
                    "expect($responseVariableName.header[\"content-type\"].startsWith(\"$bodyTypeSimplified\")).toBe(true);"
                format.isCsharp() -> "Assert.Contains(\"$bodyTypeSimplified\", $responseVariableName.Content.Headers.GetValues(\"Content-Type\").First());"
                else -> throw IllegalStateException("Unsupported format $format")
            }
            lines.add(instruction)
        }

        val bodyString = res.getBody()

        if (res.getBodyType() != null) {
            val type = res.getBodyType()!!

            var bodyVarName = responseVariableName

            if(format.isCsharp()){
                //cannot use response object directly, as need to unmarshall the body payload manually
                bodyVarName = createUniqueBodyVariableName()
                lines.add("dynamic $bodyVarName = ")
            }

            if (type.isCompatible(MediaType.APPLICATION_JSON_TYPE) || type.toString().toLowerCase().contains("+json")) {

                if(format.isCsharp()){
                    lines.append("JsonConvert.DeserializeObject(await $responseVariableName.Content.ReadAsStringAsync());")
                }

                handleJsonStringAssertion(bodyString, lines, bodyVarName, res.getTooLargeBody())

            } else if (type.isCompatible(MediaType.TEXT_PLAIN_TYPE)) {

                if(format.isCsharp()){
                    lines.append("await $responseVariableName.Content.ReadAsStringAsync();")
                }

                handleTextPlainTextAssertion(bodyString, lines, bodyVarName)
            } else {
                if(format.isCsharp()){
                    lines.append("await $responseVariableName.Content.ReadAsStringAsync();")
                }
                LoggingUtil.uniqueWarn(log, "Currently no assertions are generated for response type: $type")
            }
        }
    }

    private fun handlePythonResponseContents(lines: Lines, res: HttpWsCallResult, resVarName: String) {
        val bodyString = res.getBody()
        if (bodyString.isNullOrBlank()) {
            lines.add("assert not($resVarName.get_data(as_text=True))")
        } else {
            val type = res.getBodyType()!!
            val escapedText = GeneUtils.applyEscapes(bodyString, mode = GeneUtils.EscapeMode.TEXT, format = format)
            if (type.isCompatible(MediaType.APPLICATION_JSON_TYPE) || type.toString().toLowerCase().contains("json")) {
                if (config.blackBox) {
                    lines.add("assert $resVarName.json() == json.loads(\"$escapedText\")")
                } else {
                    lines.add("assert $resVarName.json == json.loads(\"$escapedText\")")
                }
            } else if (type.isCompatible(MediaType.TEXT_PLAIN_TYPE)) {
                lines.add("assert \"$escapedText\" in $resVarName.text")
            }
        }
    }

    protected fun handleLastLine(call: HttpWsAction, res: HttpWsCallResult, lines: Lines, resVarName: String) {

        if(format.isJavaScript()){
            /*
                This is to deal with very weird behavior in SuperAgent that crashes the tests
                for status codes different from 2xx...
                so, here we make it passes as long as a status was present
             */
            lines.add(".ok(res => res.status)")
        }

        //Why this check? seems wrong...
        //if (config.enableBasicAssertions) {
            //lines.appendSemicolon(format)
        //}

        if(lines.shouldUseSemicolon(format)) {
            if (lines.currentContains("//")) {
                //a ; after a comment // would be ignored otherwise
                if(lines.isCurrentACommentLine()){
                    //let's not lose identation
                    lines.replaceInCurrent(Regex("//"), "; //")
                } else {
                    //there can be any number of spaces between the statement and the //
                    lines.replaceInCurrent(Regex("\\s*//"), "; //")
                }

            } else {
                lines.appendSemicolon(format)
            }
        }

        //TODO what was the reason for this?
        if(!format.isCsharp() && !format.isPython()) {
            lines.deindent(2)
        }
    }
}