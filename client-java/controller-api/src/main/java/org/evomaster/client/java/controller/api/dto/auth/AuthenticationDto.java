package org.evomaster.client.java.controller.api.dto.auth;

import java.util.ArrayList;
import java.util.List;

/**
 * To authenticate a user, would need specific settings, like
 * specific values in the HTTP headers (eg, cookies)
 */
public class AuthenticationDto {

    /**
     * The name given to this authentication info.
     * This works as a unique id for this authentication configuration.
     */
    public String name;

    /**
     * Specify that the authentication for this user requires setting up mock responses from external services
     * in the API.
     * This will be done as part of the fuzzing, although only possible for white-box testing.
     *
     * One consequence here is that, even if we provide correct auth info as input, then a request might still
     * fail due to unauthorized access if the fuzzing process does not properly set up these mocked responses in the API itself.
     */
    public Boolean requireMockHandling;

    /**
     * The headers needed for authentication.
     * This is used to represent cases in which auth info is static/fixed,
     * eg when passing an id or username/password through a HTTP header (and not
     * using for example a dynamically generated token from a login endpoint first).
     */
    public List<HeaderDto> fixedHeaders = new ArrayList<>();


    /**
     * Used to represent the case in which a login endpoint is used to obtain the auth credentials.
     * These can be cookies, or a token extracted from the login endpoint's response.
     * This token can then be added to an HTTP header in the following requests.
     */
    public LoginEndpointDto loginEndpointAuth;


    /**
     * if the auth is processed based on RPC endpoints,
     * specify what info are required to execute the endpoint
     */
    public JsonAuthRPCEndpointDto jsonAuthEndpoint;

    /**
     * if the auth is processed with handleLocalAuthenticationSetup
     * specify what info is as input to setup auth with handleLocalAuthenticationSetup
     */
    public LocalAuthenticationDto localAuthSetup;


    public AuthenticationDto() {
    }

    public AuthenticationDto(String name) {
        this.name = name;
    }
}
