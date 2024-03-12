package org.evomaster.e2etests.spring.examples.resource.structuralelement;

import com.google.inject.Injector;
import org.evomaster.core.problem.rest.RestIndividual;
import org.evomaster.core.problem.rest.service.ResourceRestMutator;
import org.evomaster.core.problem.rest.service.ResourceSampler;
import org.evomaster.core.problem.rest.service.RestResourceFitness;
import org.evomaster.core.search.EvaluatedIndividual;
import org.evomaster.e2etests.spring.examples.resource.ResourceMIOHWTestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RestMutatorIndividualStructureTest extends ResourceMIOHWTestBase {

    @Test
    public void testResourceSamplerAndMutator() {

        List<String> args = generalArgs(100, 42);
        hypmutation(args, false);
        adaptiveMutation(args, 0.0);
        defaultResourceConfig(args, true);
        args.add("--probOfApplySQLActionToCreateResources");
        args.add("0.1");

        Injector injector = init(args);

        ResourceSampler sampler = injector.getInstance(ResourceSampler.class);
        ResourceRestMutator mutator = injector.getInstance(ResourceRestMutator.class);
        RestResourceFitness ff= injector.getInstance(RestResourceFitness.class);

        for (int i = 0; i < 50; i++){

            RestIndividual ind = sampler.sample(false);
            EvaluatedIndividual<RestIndividual> eind = ff.calculateCoverage(ind, new HashSet<>(), null);
            assertNotNull(eind);
            assertFalse(ind.getViewOfChildren().isEmpty());


            RestIndividual mind = mutator.mutate(eind, new HashSet<>(), null);
            assertFalse(mind.getViewOfChildren().isEmpty());
        }

    }

}
