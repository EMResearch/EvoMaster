package org.evomaster.core.problem.rest

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.SwaggerParseResult
import org.evomaster.core.problem.api.param.Param
import org.evomaster.core.problem.enterprise.auth.AuthenticationInfo
import org.evomaster.core.problem.httpws.auth.HttpWsAuthenticationInfo
import org.evomaster.core.problem.httpws.auth.HttpWsNoAuth
import org.evomaster.core.problem.rest.service.RestActionUtils
import org.evomaster.core.remote.SutProblemException
import java.net.ConnectException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import javax.ws.rs.core.Response

/**
 * Created by arcuri82 on 22-Jan-20.
 */
object OpenApiAccess {

    const val UNAUTHORIZED_SCHEMA_ACCESS : String = "UNAUTHORIZED_SCHEMA_ACCESS"

    fun getOpenApi(schemaText: String): OpenAPI {

        var parseResults: SwaggerParseResult? = null

        for (extension in OpenAPIV3Parser.getExtensions()) {
            parseResults = try {
                extension.readContents(schemaText, null, null)
            } catch (e: Exception) {
                throw SutProblemException("Failed to parse OpenApi schema: $e")
            }
            if (parseResults != null && parseResults.openAPI != null) {
                break
            }
        }

        return parseResults!!.openAPI
                ?: throw SutProblemException("Failed to parse OpenApi schema: " + parseResults.messages.joinToString("\n"))
    }

    fun getOpenAPIFromURL(openApiUrl: String, authentication : AuthenticationInfo = HttpWsNoAuth() ): OpenAPI {

        //could be either JSON or YAML
       val data = if(openApiUrl.startsWith("http", true)){
           readFromRemoteServer(openApiUrl, authentication)
       } else {
           readFromDisk(openApiUrl)
       }

        // if the swagger could not be retrieved, throw SutException
        if (data == UNAUTHORIZED_SCHEMA_ACCESS) {
            throw SutProblemException("Swagger could not be accessed because access to the swagger with " +
                    "the authentication information: " + authentication.toString() + " was denied.")

        }

        return getOpenApi(data)
    }

    private fun readFromRemoteServer(openApiUrl: String, authentication : AuthenticationInfo) : String{
        val response = connectToServer(openApiUrl, authentication, 10)

        val body = response.readEntity(String::class.java)

        // check for status code 401 or 403
        if (response.status == 401 || response.status == 403) {
            throw SutProblemException("Swagger could not be accessed because access to the swagger with " +
                    "the authentication information: " + authentication.toString() + " was denied.")
        }
        // if the problem is not due to not being able to access to an authenticated swagger,
        // just throw an exception and show the status and body
        else if (response.statusInfo.family != Response.Status.Family.SUCCESSFUL) {
            throw SutProblemException("Cannot retrieve OpenAPI schema from $openApiUrl ," +
                    " status=${response.status} , body: $body")
        }

        return body
    }

    private fun readFromDisk(openApiUrl: String) : String {

        // file schema
        val fileScheme = "file:"

        // create paths
        val path = try {
            if (openApiUrl.startsWith(fileScheme, true)) {
                Paths.get(URI.create(openApiUrl))
            }
            else {
                Paths.get(openApiUrl)
            }
        }
        // Exception is thrown if the path is not valid
        catch (e: Exception) {
            // state the exception with the error message
            throw SutProblemException("The file path provided for the OpenAPI Schema $openApiUrl" +
                        " ended up with the following error: " + e.message)
        }

        // If the path is valid but the file does not exist, an exception is thrown
        if (!Files.exists(path)) {
            throw SutProblemException("The provided OpenAPI file does not exist: $openApiUrl")
        }

        // return the schema text
        return path.toFile().readText()
    }

    private fun connectToServer(openApiUrl: String, authentication : AuthenticationInfo, attempts: Int): Response {

        for (i in 0 until attempts) {
            try {

                val swaggerCallAction = RestCallAction("swaggerRetrieve", HttpVerb.GET, RestPath("/v2/api-docs"),
                    mutableListOf<Param>())

                swaggerCallAction.auth = authentication as HttpWsAuthenticationInfo

                return RestActionUtils.handleSimpleRestCallForSwagger(swaggerCallAction, openApiUrl)

            } catch (e: Exception) {

                if (e.cause is ConnectException) {
                    /*
                        Even if SUT is running, Swagger service might not be ready
                        yet. So let's just wait a bit, and then retry
                    */
                    Thread.sleep(1_000)
                } else {
                    throw SutProblemException("Failed to connect to $openApiUrl: ${e.message}")
                }
            }
        }

        throw SutProblemException("Check if schema's URL is correct. Failed to connect to $openApiUrl")
    }
}