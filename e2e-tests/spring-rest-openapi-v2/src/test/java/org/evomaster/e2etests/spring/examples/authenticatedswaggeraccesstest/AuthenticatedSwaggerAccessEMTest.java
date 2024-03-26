package org.evomaster.e2etests.spring.examples.authenticatedswaggeraccesstest;

import com.foo.rest.examples.spring.authenticatedswaggeraccess.AuthenticatedSwaggerAccessController;

import org.evomaster.core.problem.rest.RestIndividual;
import org.evomaster.e2etests.spring.examples.SpringTestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.evomaster.core.search.Solution;


public class AuthenticatedSwaggerAccessEMTest extends SpringTestBase {

    @BeforeAll
    public static void initClass() throws Exception {

        AuthenticatedSwaggerAccessController controller = new AuthenticatedSwaggerAccessController();
        SpringTestBase.initClass(controller);
    }


    /**
     * Since the swagger endpoint is authenticated, it can be retrieved using authentication object.
     */
    @Test
    public void testRunEM() throws Throwable {

        runTestHandlingFlakyAndCompilation(
                "AuthenticatedSwaggerAccessEM",
                "org.bar.AuthenticatedSwaggerAccessEM",
                20,
                (args) -> {
                    Solution<RestIndividual> solution = initAndRun(args);

                    Assertions.assertTrue(solution.getIndividuals().size() >= 1);
                });


    }

}
