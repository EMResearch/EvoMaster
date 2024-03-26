package org.evomaster.e2etests.spring.examples.authenticatedswaggeraccesstest;

import com.foo.rest.examples.spring.unauthenticatedswaggeraccesscontroller.UnauthenticatedSwaggerAccessController;
import io.swagger.v3.oas.models.OpenAPI;
import org.evomaster.core.problem.httpws.auth.HttpWsNoAuth;
import org.evomaster.core.problem.rest.OpenApiAccess;
import org.evomaster.e2etests.spring.examples.SpringTestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class UnauthenticatedSwaggerAccessManualTest extends SpringTestBase {

    @BeforeAll
    public static void initClass() throws Exception {

        UnauthenticatedSwaggerAccessController controller = new UnauthenticatedSwaggerAccessController();
        SpringTestBase.initClass(controller);
    }

    /**
     * Since the swagger endpoint is authenticated, it cannot be retrieved using no authentication object
     */
    @Test
    public void accessSwaggerUnauthenticatedShouldSucceed() {

        // get all paths from the swagger
        OpenAPI swagger = OpenApiAccess.INSTANCE.getOpenAPIFromURL(baseUrlOfSut + "/v2/api-docs", new HttpWsNoAuth());

        // api paths should not be null
        Assertions.assertNotNull(swagger.getPaths());

    }





}
