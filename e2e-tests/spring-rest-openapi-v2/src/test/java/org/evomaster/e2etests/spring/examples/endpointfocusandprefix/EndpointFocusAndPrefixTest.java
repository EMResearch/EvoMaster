package org.evomaster.e2etests.spring.examples.endpointfocusandprefix;

import com.foo.rest.examples.spring.endpointfocusandprefix.EndpointFocusAndPrefixController;

import org.evomaster.ci.utils.JUnitExtra;
import org.evomaster.core.config.ConfigProblemException;
import org.evomaster.core.problem.httpws.auth.HttpWsNoAuth;
import org.evomaster.core.problem.rest.HttpVerb;
import org.evomaster.core.problem.rest.OpenApiAccess;
import org.evomaster.core.problem.rest.RestIndividual;
import org.evomaster.core.search.Solution;
import org.evomaster.e2etests.spring.examples.SpringTestBase;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.PathItem;

import java.util.Collections;
import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class EndpointFocusAndPrefixTest extends SpringTestBase {

    @BeforeAll
    /*
     */
    public static void initClass() throws Exception {
        SpringTestBase.initClass(new EndpointFocusAndPrefixController());
    }

    @Test
    public void testWithoutFocusOrPrefix() throws Throwable {

        String outputFolder = "BlackboxWithoutFocusOrPrefix";

        runTestHandlingFlakyAndCompilation(
                outputFolder,
                "BlackboxWithoutFocusOrPrefix",
                1000,
                (args) -> {

                    // program arguments for EvoMaster
                    args.add("--blackBox");
                    args.add("true");
                    args.add("--bbTargetUrl");
                    args.add(baseUrlOfSut);
                    args.add("--bbSwaggerUrl");
                    args.add(baseUrlOfSut + "/v2/api-docs");

                    // no endpointFocus or endpointPrefix is provided
                    Solution<RestIndividual> solution = initAndRun(args);

                    // paths to check
                    List<String> pathsToCheck = Collections.emptyList();

                    // if neither focus nor prefix is provided, then all paths should include empty path as a prefix
                    assertAllSolutionsHavePathFocusOrPrefixList(solution, pathsToCheck, false);

                    // get all paths from the swagger
                    OpenAPI swagger = OpenApiAccess.INSTANCE.getOpenAPIFromURL(baseUrlOfSut + "/v2/api-docs", new HttpWsNoAuth());

                    // api paths
                    Paths apiPaths = swagger.getPaths();

                    // current path item in the API
                    PathItem currentPathItem;

                    // All paths in the swagger has to be included in tests
                    for (String apiPathString: apiPaths.keySet()){

                        // current path item
                        currentPathItem = apiPaths.get(apiPathString);

                        // if the path item is a GET request
                        if (currentPathItem.getGet() != null) {
                            assertHasAtLeastOne(solution, HttpVerb.GET, 200, apiPathString, null);
                        }
                        // if the path item is a POST request
                        else if (currentPathItem.getPost() != null) {
                            assertHasAtLeastOne(solution, HttpVerb.POST, 200, apiPathString, null);
                        }
                        // if the path item is a PUT request
                        else if (currentPathItem.getPut() != null) {
                            assertHasAtLeastOne(solution, HttpVerb.PUT, 200, apiPathString, null);
                        }
                        // if the path item is a DELETE request
                        else if (currentPathItem.getDelete() != null) {
                            assertHasAtLeastOne(solution, HttpVerb.DELETE, 200, apiPathString, null);
                        }
                    }

                    // write test into the output folder
                    compile(outputFolder);
                });
    }

    @Test
    public void testRunBlackboxWithFocusWithoutParameters() throws Throwable {

        String outputFolder = "BlackboxWithFocusWithoutParameters";

        runTestHandlingFlakyAndCompilation(
                outputFolder,
                "BlackboxWithFocusWithoutParameters",
                1000,
                (args) -> {
                    String endpointFocus = "/api/pet";

                    // program arguments for EvoMaster
                    args.add("--blackBox");
                    args.add("true");
                    args.add("--bbTargetUrl");
                    args.add(baseUrlOfSut);
                    args.add("--bbSwaggerUrl");
                    args.add(baseUrlOfSut + "/v2/api-docs");
                    args.add("--endpointFocus");
                    args.add(endpointFocus);

                    // no endpointFocus or endpointPrefix is provided
                    Solution<RestIndividual> solution = initAndRun(args);

                    // include swagger into possible solutions as /v2/api-docs
                    List<String> pathsToCheck = Arrays.asList(endpointFocus, "/v2/api-docs");

                    // if neither focus nor prefix is provided, then all paths should include empty path as a prefix
                    assertAllSolutionsHavePathFocusOrPrefixList(solution, pathsToCheck, true);

                    // There are 2 endpoints with /api/pet, those and the failure case should be included in tests
                    // so check that the solution contains 3 elements
                    assertEquals(solution.getIndividuals().size(), 3);

                    // write test into the output folder
                    compile(outputFolder);
                });
    }

    @Test
    public void testRunBlackboxWithFocusWithParameters() throws Throwable {

        String outputFolder = "BlackboxWithFocusWithParameters";

        runTestHandlingFlakyAndCompilation(
                outputFolder,
                "BlackboxWithFocusWithParameters",
                1000,
                (args) -> {

                    String endpointFocus = "/api/pet/{petId}";

                    // program arguments for EvoMaster
                    args.add("--blackBox");
                    args.add("true");
                    args.add("--bbTargetUrl");
                    args.add(baseUrlOfSut);
                    args.add("--bbSwaggerUrl");
                    args.add(baseUrlOfSut + "/v2/api-docs");
                    args.add("--endpointFocus");
                    args.add(endpointFocus);

                    // no endpointFocus or endpointPrefix is provided
                    Solution<RestIndividual> solution = initAndRun(args);

                    // include swagger into possible solutions as /v2/api-docs
                    List<String> pathsToCheck = Arrays.asList(endpointFocus, "/v2/api-docs");

                    // if neither focus nor prefix is provided, then all paths should include empty path as a prefix
                    assertAllSolutionsHavePathFocusOrPrefixList(solution, pathsToCheck, true);

                    // The solution should include 4 solutions, 3 endpoints and 1 failure case
                    assertEquals(solution.getIndividuals().size(), 4);

                    // write test into the output folder
                    compile(outputFolder);
                });
    }

    @Test
    public void testRunBlackboxWithFocusOneEndpoint() throws Throwable {

        String outputFolder = "BlackboxWithFocusOneEndpoint";

        runTestHandlingFlakyAndCompilation(
                outputFolder,
                "BlackboxWithFocusOneEndpoint",
                1000,
                (args) -> {

                    String endpointFocus = "/api/store/inventory";

                    // program arguments for EvoMaster
                    args.add("--blackBox");
                    args.add("true");
                    args.add("--bbTargetUrl");
                    args.add(baseUrlOfSut);
                    args.add("--bbSwaggerUrl");
                    args.add(baseUrlOfSut + "/v2/api-docs");
                    args.add("--endpointFocus");
                    args.add(endpointFocus);

                    // no endpointFocus or endpointPrefix is provided
                    Solution<RestIndividual> solution = initAndRun(args);

                    // include swagger into possible solutions as /v2/api-docs
                    List<String> pathsToCheck = Arrays.asList(endpointFocus, "/v2/api-docs");

                    // if neither focus nor prefix is provided, then all paths should include empty path as a prefix
                    assertAllSolutionsHavePathFocusOrPrefixList(solution, pathsToCheck, true);

                    // The solution should include 2 solutions, 1 endpoints and 1 failure case
                    assertEquals(solution.getIndividuals().size(), 2);

                    // write test into the output folder
                    compile(outputFolder);
                });
    }

    @Test
    public void testRunBlackboxWithPrefixWithoutParameters() throws Throwable {

        String outputFolder = "BlackboxWithPrefixWithoutParameters";

        runTestHandlingFlakyAndCompilation(
                outputFolder,
                "BlackboxWithPrefixWithoutParameters",
                1000,
                (args) -> {

                    String endpointPrefix = "/api/user";

                    // program arguments for EvoMaster
                    args.add("--blackBox");
                    args.add("true");
                    args.add("--bbTargetUrl");
                    args.add(baseUrlOfSut);
                    args.add("--bbSwaggerUrl");
                    args.add(baseUrlOfSut + "/v2/api-docs");
                    args.add("--endpointPrefix");
                    args.add(endpointPrefix);

                    // no endpointFocus or endpointPrefix is provided
                    Solution<RestIndividual> solution = initAndRun(args);

                    // include swagger into possible solutions as /v2/api-docs
                    List<String> pathsToCheck = Arrays.asList(endpointPrefix, "/v2/api-docs");

                    // if neither focus nor prefix is provided, then all paths should include empty path as a prefix
                    assertAllSolutionsHavePathFocusOrPrefixList(solution, pathsToCheck, false);

                    // The solution should include 8 solutions, 7 endpoints and 1 failure case
                    assertEquals(solution.getIndividuals().size(), 8);

                    // write test into the output folder
                    compile(outputFolder);
                });
    }

    @Test
    public void testRunBlackboxWithPrefixWithParameters() throws Throwable {

        String outputFolder = "BlackboxWithPrefixWithParameters";

        runTestHandlingFlakyAndCompilation(
                outputFolder,
                "BlackboxWithPrefixWithParameters",
                1000,
                (args) -> {

                    String endpointPrefix = "/api/pet/{petId}";

                    // program arguments for EvoMaster
                    args.add("--blackBox");
                    args.add("true");
                    args.add("--bbTargetUrl");
                    args.add(baseUrlOfSut);
                    args.add("--bbSwaggerUrl");
                    args.add(baseUrlOfSut + "/v2/api-docs");
                    args.add("--endpointPrefix");
                    args.add(endpointPrefix);

                    // no endpointFocus or endpointPrefix is provided
                    Solution<RestIndividual> solution = initAndRun(args);

                    // include swagger into possible solutions as /v2/api-docs
                    List<String> pathsToCheck = Arrays.asList(endpointPrefix, "/v2/api-docs");

                    // if neither focus nor prefix is provided, then all paths should include empty path as a prefix
                    assertAllSolutionsHavePathFocusOrPrefixList(solution, pathsToCheck, false);

                    // The solution should include 5 solutions, 4 endpoints and 1 failure case
                    assertEquals(solution.getIndividuals().size(), 5);

                    // write test into the output folder
                    compile(outputFolder);
                });
    }

    @Test
    public void testRunBlackboxPrefixNonExistingFocusValidPrefix() throws Throwable {

        String outputFolder = "BlackboxPrefixNonExistingFocusValidPrefix";

        runTestHandlingFlakyAndCompilation(
                outputFolder,
                "BlackboxPrefixNonExistingFocusValidPrefix",
                1000,
                (args) -> {

                    String endpointPrefix = "/api/store";

                    // program arguments for EvoMaster
                    args.add("--blackBox");
                    args.add("true");
                    args.add("--bbTargetUrl");
                    args.add(baseUrlOfSut);
                    args.add("--bbSwaggerUrl");
                    args.add(baseUrlOfSut + "/v2/api-docs");
                    args.add("--endpointPrefix");
                    args.add(endpointPrefix);

                    // no endpointFocus or endpointPrefix is provided
                    Solution<RestIndividual> solution = initAndRun(args);

                    // include swagger into possible solutions as /v2/api-docs
                    List<String> pathsToCheck = Arrays.asList(endpointPrefix, "/v2/api-docs");

                    // if neither focus nor prefix is provided, then all paths should include empty path as a prefix
                    assertAllSolutionsHavePathFocusOrPrefixList(solution, pathsToCheck, false);

                    // The solution should include 5 solutions, 4 endpoints and 1 failure case
                    assertEquals(solution.getIndividuals().size(), 5);

                    // write test into the output folder
                    compile(outputFolder);
                });
    }

    @Test
    public void testRunBlackboxFocusNonExistingFocusValidPrefix() {

        String outputFolder = "BlackboxFocusNonExistingFocusValidPrefix";

        JUnitExtra.assertThrowsInnermost(ConfigProblemException.class, () ->

                runTestHandlingFlakyAndCompilation(
                        outputFolder,
                        "BlackboxFocusNonExistingFocusValidPrefix",
                        1000,
                        (args) -> {

                            String endpointFocus = "/api/store";

                            // program arguments for EvoMaster
                            args.add("--blackBox");
                            args.add("true");
                            args.add("--bbTargetUrl");
                            args.add(baseUrlOfSut);
                            args.add("--bbSwaggerUrl");
                            args.add(baseUrlOfSut + "/v2/api-docs");
                            args.add("--endpointFocus");
                            args.add(endpointFocus);

                            // run EvoMaster
                            initAndRun(args);

                            // write test into the output folder
                            compile(outputFolder);
                        })
        );
    }

    @Test
    public void testRunBlackboxNonExistingFocusNonExistingPrefix() {

        String outputFolder = "BlackboxNonExistingFocusNonExistingPrefix";

        JUnitExtra.assertThrowsInnermost(ConfigProblemException.class, () ->

                runTestHandlingFlakyAndCompilation(
                        outputFolder,
                        "BlackboxNonExistingFocusNonExistingPrefix",
                        1000,
                        (args) -> {

                            String endpointPrefix = "/api/ab/s1";

                            // program arguments for EvoMaster
                            args.add("--blackBox");
                            args.add("true");
                            args.add("--bbTargetUrl");
                            args.add(baseUrlOfSut);
                            args.add("--bbSwaggerUrl");
                            args.add(baseUrlOfSut + "/v2/api-docs");
                            args.add("--endpointPrefix");
                            args.add(endpointPrefix);

                            // run EvoMaster
                            initAndRun(args);

                            // write test into the output folder
                            compile(outputFolder);
                        })
        );
    }

    @Test
    public void testRunBlackboxPrefixNonExistingPrefix() {

        String outputFolder = "BlackboxPrefixNonExistingPrefix";

        JUnitExtra.assertThrowsInnermost(ConfigProblemException.class, () ->

                runTestHandlingFlakyAndCompilation(
                        outputFolder,
                        "BlackboxPrefixNonExistingPrefix",
                        1000,
                        (args) -> {

                            String endpointPrefix = "/api/store/inventory/in";

                            // program arguments for EvoMaster
                            args.add("--blackBox");
                            args.add("true");
                            args.add("--bbTargetUrl");
                            args.add(baseUrlOfSut);
                            args.add("--bbSwaggerUrl");
                            args.add(baseUrlOfSut + "/v2/api-docs");
                            args.add("--endpointPrefix");
                            args.add(endpointPrefix);

                            // run EvoMaster
                            initAndRun(args);

                            // write test into the output folder
                            compile(outputFolder);
                        })
        );
    }


    @Test
    public void testRunBlackboxBothFocusAndPrefix() {

        String outputFolder = "BlackboxBothFocusAndPrefix";

        assertThrows(ConfigProblemException.class, () ->

                runTestHandlingFlakyAndCompilation(
                        outputFolder,
                        "BlackboxBothFocusAndPrefix",
                        1000,
                        (args) -> {

                            String endpoint = "/api/store/order";

                            // program arguments for EvoMaster
                            args.add("--blackBox");
                            args.add("true");
                            args.add("--bbTargetUrl");
                            args.add(baseUrlOfSut);
                            args.add("--bbSwaggerUrl");
                            args.add(baseUrlOfSut + "/v2/api-docs");
                            args.add("--endpointPrefix");
                            args.add(endpoint);
                            args.add("--endpointFocus");
                            args.add(endpoint);

                            // run EvoMaster
                            initAndRun(args);

                            // write test into the output folder
                            compile(outputFolder);
                        })
        );
    }
}



