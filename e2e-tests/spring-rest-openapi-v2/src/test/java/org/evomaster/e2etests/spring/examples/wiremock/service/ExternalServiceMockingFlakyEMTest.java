package org.evomaster.e2etests.spring.examples.wiremock.service;

import com.foo.rest.examples.spring.wiremock.service.ServiceController;
import org.evomaster.ci.utils.CIUtils;
import org.evomaster.core.EMConfig;
import org.evomaster.core.problem.rest.HttpVerb;
import org.evomaster.core.problem.rest.RestIndividual;
import org.evomaster.core.problem.rest.resource.RestResourceCalls;
import org.evomaster.core.search.action.Action;
import org.evomaster.core.search.action.ActionFilter;
import org.evomaster.core.search.EvaluatedIndividual;
import org.evomaster.core.search.Solution;
import org.evomaster.e2etests.spring.examples.SpringTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExternalServiceMockingFlakyEMTest extends SpringTestBase {

    @BeforeAll
    public static void initClass() throws Exception {
        ServiceController serviceController = new ServiceController();
        EMConfig config = new EMConfig();
        config.setInstrumentMR_NET(true);
        SpringTestBase.initClass(serviceController, config);
    }

    @Test
    public void externalServiceMockingTest() throws Throwable {
        runTestHandlingFlakyAndCompilation(
                "ExternalServiceMockingEMGeneratedTest",
                "org.bar.ExternalServiceMockingEMGeneratedTest",
                1500,
                !CIUtils.isRunningGA(),
                (args) -> {

                    // IP set to 127.0.0.5 to confirm the test failure
                    // Use USER for external service IP selection strategy
                    // when running on a personal computer if it's macOS
                    // Note: When running parallel tests it's always good select
                    //  Random as strategy.
                    args.add("--externalServiceIPSelectionStrategy");
                    args.add("USER");
                    args.add("--externalServiceIP");
                    args.add("127.0.0.5");
                    args.add("--minimize");
                    args.add("false");

                    Solution<RestIndividual> solution = initAndRun(args);

                    // The below block of code is an experiment
                    // The value 13 is decided by looking at the generated actions count
                    // manually.
                    List<Action> actions = new ArrayList<>();
                    for (EvaluatedIndividual<RestIndividual> individual : solution.getIndividuals()) {
//                        for (RestResourceCalls call : individual.getIndividual().getResourceCalls()) {
//                            actions.addAll(call.seeActions(ActionFilter.ONLY_EXTERNAL_SERVICE));
//                        }
                        actions.addAll(individual.getIndividual().seeExternalServiceActions());
                    }
                    //assertEquals(actions.size(), 13);
                    //Andrea: there should be clear reason for hardcoded numbers like 13. otherwise, when we get a new
                    // value (like 12 in my case) how do we know it is a bug and not just a harmless change in the search process?
                    assertTrue(actions.size() > 0);

                    assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wiremock/external", "true");
                    assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wiremock/external/complex", "true");
                },
                3);
    }
}
