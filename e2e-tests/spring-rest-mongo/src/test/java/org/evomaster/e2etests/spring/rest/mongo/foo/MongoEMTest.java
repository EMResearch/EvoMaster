package org.evomaster.e2etests.spring.rest.mongo.foo;

import com.foo.spring.rest.mongo.MongoStudentsAppController;
import org.evomaster.core.problem.rest.HttpVerb;
import org.evomaster.core.problem.rest.RestIndividual;
import org.evomaster.core.search.Solution;
import org.evomaster.e2etests.utils.RestTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class MongoEMTest extends RestTestBase {

    @BeforeAll
    public static void initClass() throws Exception {
        RestTestBase.initClass(new MongoStudentsAppController());
    }

    @Test
    public void testRunEM() throws Throwable {

        runTestHandlingFlakyAndCompilation(
                "MongoEM",
                "org.foo.spring.rest.mongo.MongoEM",
                200,
                (args) -> {
                    args.add("--enableWeightBasedMutationRateSelectionForGene");
                    args.add("false");

                    Solution<RestIndividual> solution = initAndRun(args);

                    assertTrue(solution.getIndividuals().size() >= 1);
                    assertHasAtLeastOne(solution, HttpVerb.POST, 200, "/students/jorge", null);
                    assertHasAtLeastOne(solution, HttpVerb.POST, 200, "/students/addAndGetJorge", null);
                    assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/students/{lastName}", null);
                });
    }
}