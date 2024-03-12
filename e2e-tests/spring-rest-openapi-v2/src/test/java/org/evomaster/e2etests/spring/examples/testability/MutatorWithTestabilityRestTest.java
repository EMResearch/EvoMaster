package org.evomaster.e2etests.spring.examples.testability;

import com.foo.rest.examples.spring.testability.TestabilityController;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import org.evomaster.core.problem.rest.RestCallAction;
import org.evomaster.core.problem.rest.RestIndividual;
import org.evomaster.core.problem.rest.service.AbstractRestSampler;
import org.evomaster.core.problem.rest.service.RestFitness;
import org.evomaster.core.problem.rest.service.RestSampler;
import org.evomaster.core.problem.util.ParamUtil;
import org.evomaster.core.search.EvaluatedIndividual;
import org.evomaster.core.search.Individual;
import org.evomaster.core.search.gene.Gene;
import org.evomaster.core.search.gene.string.StringGene;
import org.evomaster.core.search.service.Archive;
import org.evomaster.core.search.service.mutator.EvaluatedMutation;
import org.evomaster.core.search.service.mutator.StandardMutator;
import org.evomaster.e2etests.spring.examples.SpringTestBase;
import org.evomaster.ci.utils.CIUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * created by manzh on 2020-06-22
 */
public class MutatorWithTestabilityRestTest extends SpringTestBase {

    @BeforeAll
    public static void initClass() throws Exception {

        SpringTestBase.initClass(new TestabilityController());
    }

    @Test
    public void testMutator() throws Throwable {


        CIUtils.skipIfOnCircleCI();

        runTestHandlingFlakyAndCompilation(
                "EM",
                "EM",
                1_100,
                false,
                (args) -> {

                    args.add("--baseTaintAnalysisProbability");
                    args.add("0.9");

                    args.add("--maxTestSize");
                    args.add("1");

                    Injector injector = init(args);

                    StandardMutator<RestIndividual> mutator = injector.getInstance(Key.get(
                           new TypeLiteral<StandardMutator<RestIndividual>> () {}));
                    assertNotNull(mutator);

                    Archive<RestIndividual> archive = injector.getInstance(Key.get(
                            new TypeLiteral<Archive<RestIndividual>> () {}));
                    assertNotNull(archive);

                    RestFitness ff = injector.getInstance(RestFitness.class);

                    RestSampler sampler = injector.getInstance(RestSampler.class);
                    RestIndividual ind = sampler.sample(false);
                    int count = 0;
                    while (ind.seeMainExecutableActions().stream().anyMatch(a-> anyExcludedAction(sampler, a)) && count < 3){
                        ind = sampler.sample(false);
                        count++;
                    }
                    if (ind.seeMainExecutableActions().stream().anyMatch(a-> anyExcludedAction(sampler, a)))
                        fail("cannot find any valid individual");
                    archive.addIfNeeded(ff.calculateCoverage(ind, Collections.emptySet(), null));

                    assertNotNull(ind);

                    int length = 1000;
                    List<String> dates = improvingIntValues(length, 2019, "-06-22", "");
                    List<String> ns = improvingIntValues(length, 42, "", "");
                    List<String> foos = improvingStringValues(length, "foo");

                    int i = 0;
                    EvaluatedIndividual<RestIndividual> current = ff.calculateCoverage(mutate(dates.get(i), ns.get(i), foos.get(i), ind), Collections.emptySet(), null);
                    archive.addIfNeeded(current);

                    Set<Integer> targets = new HashSet<>();
                    targets.addAll(archive.notCoveredTargets());
                    while (i < length-1){
                        i++;
                        EvaluatedIndividual<RestIndividual> mutated = ff.calculateCoverage(mutate(dates.get(i), ns.get(i), foos.get(i), ind), Collections.emptySet(), null);
                        EvaluatedMutation result = mutator.evaluateMutation(mutated, current, targets, archive);
                        assertNotEquals(EvaluatedMutation.WORSE_THAN, result);

                        current = mutator.saveMutation(
                                result,
                                archive,
                                current,
                                mutated
                        );

                        assertEquals(mutated, current);

                        targets.addAll(archive.notCoveredTargets());
                    }

                },
                3);
    }

    private boolean anyExcludedAction(AbstractRestSampler sampler, RestCallAction action){
        return sampler.getExcludedActions().stream().anyMatch(s-> action.getName().equals(s.getName()));
    }

    private RestIndividual mutate(String date, String number, String setting, RestIndividual individual) {
        RestIndividual mutated =  (RestIndividual) individual.copy();
        setValue("date", date, mutated);
        setValue("number", number, mutated);
        setValue("setting", setting, mutated);

        return mutated;
    }


    private void setValue(String geneName, String value, RestIndividual individual){
        Gene gene = individual.seeGenes(Individual.GeneFilter.ALL).stream().filter(g -> ParamUtil.Companion.getValueGene(g).getName().equals(geneName))
                .findAny()
                .orElse(null);
        Gene g = ParamUtil.Companion.getValueGene(gene);
        if (g instanceof StringGene){
            ((StringGene) g).setValue(value);
        }else
            throw new IllegalArgumentException("StringGene named " + geneName + " cannot be found");
    }

    private List<String> improvingIntValues(int length, int target, String suffix, String prefix){
        List<String> values = new ArrayList<>();
        for (int i = 0; i < length; i++) values.add(0, prefix+(target - i) +suffix);
        return values;
    }

    private List<String> improvingStringValues(int length, String value){
        List<String> values = new ArrayList<>();
        String content = value;
        values.add(content);
        for (int i = 1; i < length; i++) {
            content = content + "s";
            values.add(content);
        }
        return values;
    }
}