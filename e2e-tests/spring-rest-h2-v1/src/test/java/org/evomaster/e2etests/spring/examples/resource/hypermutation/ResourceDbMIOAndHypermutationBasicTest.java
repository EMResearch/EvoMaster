package org.evomaster.e2etests.spring.examples.resource.hypermutation;

import com.google.inject.Injector;
import org.evomaster.core.problem.rest.RestIndividual;
import org.evomaster.core.problem.enterprise.SampleType;
import org.evomaster.core.problem.rest.resource.RestResourceCalls;
import org.evomaster.core.problem.rest.resource.RestResourceNode;
import org.evomaster.core.problem.rest.service.ResourceManageService;
import org.evomaster.core.problem.rest.service.ResourceRestMutator;
import org.evomaster.core.problem.rest.service.RestResourceFitness;
import org.evomaster.core.problem.rest.service.RestResourceStructureMutator;
import org.evomaster.core.problem.util.BindingBuilder;
import org.evomaster.core.search.action.ActionFilter;
import org.evomaster.core.search.EvaluatedIndividual;
import org.evomaster.core.search.Individual.GeneFilter;
import org.evomaster.core.search.service.mutator.MutatedGeneSpecification;
import org.evomaster.e2etests.spring.examples.resource.ResourceMIOHWTestBase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ResourceDbMIOAndHypermutationBasicTest extends ResourceMIOHWTestBase {


    @Test
    public void testResourceHypermutation(){

        List<String> args = generalArgs(3, 0);
        hypmutation(args, true);
        adaptiveMutation(args, 0.0);
        defaultResourceConfig(args, false);
        args.add("--probOfApplySQLActionToCreateResources");
        args.add("0.0");
        args.add("--structureMutationProbability");
        args.add("0.0");

        Injector injector = init(args);

        ResourceManageService rmanger = injector.getInstance(ResourceManageService.class);
        ResourceRestMutator mutator = injector.getInstance(ResourceRestMutator.class);
        RestResourceFitness ff = injector.getInstance(RestResourceFitness.class);

        String raIdkey = "/api/rA/{rAId}";
        String rdkey = "/api/rd";

        List<RestResourceCalls> calls = new ArrayList<>();
        rmanger.sampleCall(raIdkey, true, calls, 10, false, Collections.emptyList(), "POST-GET");
        rmanger.sampleCall(rdkey, true, calls, 8, false, Collections.emptyList(), "POST-POST");
        assertEquals(2, calls.size());

        RestIndividual twoCalls = new RestIndividual(calls, SampleType.SMART_RESOURCE, null, Collections.emptyList(), null, 1);
        twoCalls.doInitializeLocalId();
        EvaluatedIndividual<RestIndividual> twoCallsEval = ff.calculateCoverage(twoCalls, Collections.emptySet(), null);
        assertEquals(4, mutator.genesToMutation(twoCalls, twoCallsEval, Collections.emptySet()).stream().filter(s-> !BindingBuilder.INSTANCE.isExtraTaintParam(s.getName())).count());

        MutatedGeneSpecification spec = new MutatedGeneSpecification();
        RestIndividual mutatedTwoCalls = mutator.mutate(twoCallsEval, Collections.emptySet(), spec);
        assertEquals(0, spec.mutatedDbGeneInfo().size());
        // it might be flaky. but with specified seed, this should be determinate
        assertFalse(spec.mutatedGeneInfo().isEmpty());

//        Gene rdObj = calls.get(0).seeGenes(GeneFilter.NO_SQL).stream().findFirst().orElse(null);
//        Gene mrdObj = mutatedTwoCalls.getResourceCalls().get(0).seeGenes(GeneFilter.NO_SQL).stream().findFirst().orElse(null);
//        assert(rdObj instanceof ObjectGene);
//        assert(mrdObj instanceof ObjectGene);
//        //two fields are mutated (hypermutation is applied)
//        assertEquals(2, IntStream.range(0, ((ObjectGene)rdObj).getFields().size()).filter(i->
//                !((ObjectGene) rdObj).getFields().get(i).containsSameValueAs(((ObjectGene) mrdObj).getFields().get(i))
//        ).count());

        //value should be bound
        mutatedTwoCalls.getResourceCalls().forEach(c->
                checkingBinding(c, c.getRestTemplate(), c.getResourceNodeKey(), false));
    }

    @Test
    public void testResourceDBHypermutation() {
        List<String> args = generalArgs(3, 42);
        hypmutation(args, false);
        adaptiveMutation(args, 0.0);
        defaultResourceConfig(args, true);
        args.add("--probOfApplySQLActionToCreateResources");
        args.add("1.0");
        args.add("--structureMutationProbability");
        args.add("0.0");

        Injector injector = init(args);

        ResourceManageService rmanger = injector.getInstance(ResourceManageService.class);
        ResourceRestMutator mutator = injector.getInstance(ResourceRestMutator.class);
        RestResourceFitness ff = injector.getInstance(RestResourceFitness.class);
        RestResourceStructureMutator structureMutator = injector.getInstance(RestResourceStructureMutator.class);

        assertEquals(keysToTemplate.keySet(), rmanger.getResourceCluster().keySet());

        for (String key : keysToTemplate.keySet()){
            RestResourceNode node = rmanger.getResourceNodeFromCluster(key);
            assertEquals(keysToTemplate.get(key), node.getTemplates().keySet(), key);
            // check derived tables
            assertEquals(keysToTable.get(key), node.getResourceToTable().getDerivedMap().keySet(), key);
        }

        List<RestResourceCalls> calls = new ArrayList<>();

        String raKey = "/api/rA";
        String raPostTemplate = "POST";
        rmanger.sampleCall(raKey, true, calls, 10, true, Collections.emptyList(), raPostTemplate);

        RestResourceCalls rAcall = calls.get(0);
        assertEquals("POST", rAcall.getRestTemplate());
        assertEquals("POST-POST", rAcall.extractTemplate());

        assertEquals(1, rAcall.seeActions(ActionFilter.ONLY_SQL).size());
        assertEquals(1, rAcall.seeActions(ActionFilter.NO_SQL).size());
        assertEquals(2, rAcall.seeActions(ActionFilter.ALL).size());
        // check whether POST is bound with SQL
        checkingBinding(rAcall, "POST", raKey, true);

        // all SQL genes can be bound with POST, so the mutable SQL genes should be empty.
        assertEquals(0, rAcall.seeGenes(GeneFilter.ONLY_SQL).size());
        assertEquals(1, rAcall.seeGenes(GeneFilter.ALL).stream().filter(s-> !BindingBuilder.INSTANCE.isExtraTaintParam(s.getName()) && s.isMutable()).count());

        String raIdKey = "/api/rA/{rAId}";
        String raIdPostTemplate = "GET";
        rmanger.sampleCall(raIdKey, true, calls, 9, true, Collections.emptyList(), raIdPostTemplate);
        assertEquals(2, calls.size());
        RestResourceCalls rAIdcall = calls.get(1);
        assertEquals("GET",rAIdcall.getRestTemplate());
        assertEquals("POST-GET", rAIdcall.extractTemplate());

        assertEquals(1, rAIdcall.seeActionSize(ActionFilter.ONLY_SQL));
        assertEquals(1, rAIdcall.seeActionSize(ActionFilter.NO_SQL));
        assertEquals(2, rAIdcall.seeActionSize(ActionFilter.ALL));
        // check whether get is bounded with SQL
        checkingBinding(rAIdcall, "GET", raIdKey,true);

        //exclude 'id' gene as it can be bound with GET
        assertEquals(2, rAIdcall.seeGenes(GeneFilter.ONLY_SQL).stream().filter(s-> !BindingBuilder.INSTANCE.isExtraTaintParam(s.getName())).count());
        assertEquals(1, rAIdcall.seeGenes(GeneFilter.NO_SQL).stream().filter(s-> !BindingBuilder.INSTANCE.isExtraTaintParam(s.getName())).count());

        //test binding after value mutator
        RestIndividual raIdInd = new RestIndividual(calls, SampleType.SMART_RESOURCE, null, Collections.emptyList(), null, 1);
        raIdInd.doInitializeLocalId();
        EvaluatedIndividual<RestIndividual> rdIdEval = ff.calculateCoverage(raIdInd, Collections.emptySet(), null);
        // mutable genes should be 0+1+2+1=4
        assertEquals(4, mutator.genesToMutation(raIdInd, rdIdEval, Collections.emptySet()).stream().filter(s-> !BindingBuilder.INSTANCE.isExtraTaintParam(s.getName())).count());

        MutatedGeneSpecification mutatedSpec = new MutatedGeneSpecification();
        RestIndividual mutatedInd = mutator.mutate(rdIdEval, Collections.emptySet(), mutatedSpec);
        assertFalse(mutatedSpec.didStructureMutation());
        checkingBinding(mutatedInd.getResourceCalls().get(0), "POST", raKey, true);
        checkingBinding(mutatedInd.getResourceCalls().get(1), "GET", raIdKey,true);

    }
}
