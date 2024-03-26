package org.evomaster.e2etests.spring.examples.authenticatedswaggeraccesstest;

import com.foo.rest.examples.spring.unauthenticatedswaggeraccesscontroller.UnauthenticatedSwaggerAccessController;
import org.evomaster.core.problem.rest.RestIndividual;
import org.evomaster.core.search.Solution;
import org.evomaster.e2etests.spring.examples.SpringTestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class UnauthenticatedSwaggerAccessEMTest extends SpringTestBase {

    @BeforeAll
    public static void initClass() throws Exception {

        UnauthenticatedSwaggerAccessController controller = new UnauthenticatedSwaggerAccessController();
        SpringTestBase.initClass(controller);
    }

    /**
     * Since the swagger endpoint is authenticated, it can be retrieved using authentication object.
     */
    @Test
    public void testRunEM() throws Throwable {

        runTestHandlingFlakyAndCompilation(
                "UnauthenticatedSwaggerAccessEM",
                "org.bar.UnauthenticatedSwaggerAccessEM",
                20,
                (args) -> {
                    Solution<RestIndividual> solution = initAndRun(args);

                    Assertions.assertTrue(solution.getIndividuals().size() >= 1);
                });
    }
}
