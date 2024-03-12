package org.evomaster.e2etests.spring.examples.wiremock.service;

import com.foo.rest.examples.spring.wiremock.service.ServiceController;
import com.google.inject.Injector;
import org.evomaster.core.EMConfig;
import org.evomaster.core.problem.externalservice.httpws.service.HttpWsExternalServiceHandler;
import org.evomaster.core.problem.rest.RestIndividual;
import org.evomaster.core.problem.rest.service.ResourceSampler;
import org.evomaster.core.problem.rest.service.RestResourceFitness;
import org.evomaster.e2etests.spring.examples.SpringTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


@Disabled("Need update code after refactoring")
public class ExternalServiceMockingEMTest extends SpringTestBase {

    @BeforeAll
    public static void initClass() throws Exception {
        ServiceController serviceController = new ServiceController();
        EMConfig config = new EMConfig();
        config.setInstrumentMR_NET(true);
        SpringTestBase.initClass(serviceController,config);
    }

    /**
     * The test is disabled since, encountered some errors within the
     * existing e2e tests. Will be completed once those sorted.
     */
    @Test
    public void externalServiceSuccessTest() throws Throwable {
        String[] args = new String[]{
                "--createTests", "false",
                "--seed", "42",
                "--sutControllerPort", "" + controllerPort,
                "--maxActionEvaluations", "1",
                "--stoppingCriterion", "FITNESS_EVALUATIONS",
                "--executiveSummary", "false",
                "--expectationsActive" , "true",
                "--outputFormat", "JAVA_JUNIT_5",
                "--outputFolder", "target/em-tests/ExternalServiceEM",
                "--externalServiceIPSelectionStrategy", "USER",
                "--externalServiceIP", "127.0.0.3"
        };

        Injector injector = init(Arrays.asList(args));

        HttpWsExternalServiceHandler externalServiceHandler = injector.getInstance(HttpWsExternalServiceHandler.class);

        RestResourceFitness restResourceFitness = injector.getInstance(RestResourceFitness.class);
        ResourceSampler resourceSampler = injector.getInstance(ResourceSampler.class);
        RestIndividual restIndividual = resourceSampler.sample(false);

        // asserts whether the call made during the start-up is captured
        assertEquals(3, externalServiceHandler.getExternalServices().size(), externalServiceHandler.getExternalServiceMappings().size());
        assertTrue( externalServiceHandler.getExternalServices().containsKey("https__foobarbazz.com__8443"));
        restResourceFitness.calculateCoverage(restIndividual, Collections.emptySet(), null);

        // assertion after the execution
        assertEquals(4, externalServiceHandler.getExternalServices().size());

    }
}
