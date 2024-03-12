package org.evomaster.e2etests.spring.examples.resource.nodb;

import org.evomaster.core.problem.rest.HttpVerb;
import org.evomaster.core.problem.rest.RestIndividual;
import org.evomaster.core.search.Solution;
import org.evomaster.e2etests.spring.examples.resource.ResourceTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * created by manzh on 2019-08-12
 */
public class ResourceDependencyDisableDBEMTest extends ResourceTestBase {

    @Test
    public void testRunEM() throws Throwable {

        runTestHandlingFlakyAndCompilation(
                "ResourceEM",
                "org.resource.nodb.ResourceEM",
                1_000,
                true,
                (args) -> {

                    //disable SQL
                    args.add("--heuristicsForSQL");
                    args.add("false");
                    args.add("--generateSqlDataWithSearch");
                    args.add("false");
                    args.add("--extractSqlExecutionInfo");
                    args.add("true");


                    Solution<RestIndividual> solution = initAndRun(args);

                    assertTrue(solution.getIndividuals().size() >= 1);

                    boolean anyDBExecution = solution.getIndividuals().stream().anyMatch(
                            s -> s.getFitness().isAnyDatabaseExecutionInfo()
                    );
                    assertTrue(anyDBExecution);

                    boolean anyDBAction = solution.getIndividuals().stream().anyMatch(
                            s -> !s.getIndividual().seeSqlDbActions().isEmpty());

                    assertFalse(anyDBAction);

                    boolean ok = solution.getIndividuals().stream().anyMatch(
                            s -> hasAtLeastOneSequence(s, new HttpVerb[]{HttpVerb.POST, HttpVerb.POST}, new int[]{201, 201}, new String[]{"/api/rd","/api/rpR"}) ||
                                    hasAtLeastOneSequence(s, new HttpVerb[]{HttpVerb.GET, HttpVerb.POST}, new int[]{200, 201}, new String[]{"/api/rd/{rdId}","/api/rpR"})

                    );

                    assertTrue(ok);
                }, 3);
    }
}
