package org.evomaster.core.problem.rest

import com.google.inject.Injector
import org.evomaster.core.problem.enterprise.SampleType
import org.evomaster.core.problem.rest.service.AbstractRestFitness
import org.evomaster.core.problem.rest.service.AbstractRestSampler
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.service.SearchGlobalState
import org.evomaster.core.seeding.service.rest.PirToRest
import org.evomaster.e2etests.utils.RestTestBase
import org.junit.jupiter.api.BeforeEach

abstract class IntegrationTestRestBase : RestTestBase() {


    protected lateinit var injector: Injector

    @BeforeEach
    fun initInjector(){
        val args = listOf(
            "--sutControllerPort", "" + controllerPort,
            "--createConfigPathIfMissing", "false"
        )
        injector = init(args)
    }

    fun getPirToRest() = injector.getInstance(PirToRest::class.java)


    /**
     * Modified by Onur to be able to create an individual with a given sample type.
     */
    fun createIndividual(actions: List<RestCallAction>, sampleT : SampleType = SampleType.SEEDED): EvaluatedIndividual<RestIndividual> {

//        val searchGlobalState = injector.getInstance(SearchGlobalState::class.java)

//        val ind = RestIndividual(actions.toMutableList(), SampleType.SEEDED)
//        ind.doGlobalInitialize(searchGlobalState)

        val sampler = injector.getInstance(AbstractRestSampler::class.java)
        /*
            the method `createIndividual` can be overridden
            in this case, the sampler is an instance of ResourceSampler,
            then check its implementation in ResourceSampler.createIndividual(...)
         */
        val ind = sampler.createIndividual(sampleT, actions.toMutableList())

        val ff = injector.getInstance(AbstractRestFitness::class.java)
        val ei = ff.calculateCoverage(ind)!!

        return ei
    }

}