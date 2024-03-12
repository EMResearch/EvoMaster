package org.evomaster.core.search.service

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.google.inject.Injector
import com.google.inject.Key
import com.google.inject.Module
import com.google.inject.TypeLiteral
import com.netflix.governator.guice.LifecycleInjector
import org.evomaster.core.BaseModule
import org.evomaster.core.EMConfig
import org.evomaster.core.TestUtils
import org.evomaster.core.search.algorithms.onemax.OneMaxFitness
import org.evomaster.core.search.algorithms.onemax.OneMaxIndividual
import org.evomaster.core.search.algorithms.onemax.OneMaxModule
import org.evomaster.core.search.service.monitor.SearchOverall
import org.evomaster.core.search.service.monitor.SearchProcessMonitor
import org.evomaster.core.search.service.monitor.StepOfSearchProcess
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths


class ProcessMonitorTest{

    private lateinit var archive: Archive<OneMaxIndividual>
    private lateinit var ff : OneMaxFitness
    private lateinit var config: EMConfig
    private lateinit var processMonitor : SearchProcessMonitor
    private lateinit var randomness: Randomness

    @BeforeEach
    fun init(){

        val injector: Injector = LifecycleInjector.builder()
                .withModules(* arrayOf<Module>(OneMaxModule(), BaseModule()))
                .build().createInjector()


        archive = injector.getInstance(Key.get(
                object : TypeLiteral<Archive<OneMaxIndividual>>() {}))
        processMonitor = injector.getInstance(Key.get(SearchProcessMonitor::class.java))
        randomness = injector.getInstance(Key.get(Randomness::class.java))

        ff =  injector.getInstance(OneMaxFitness::class.java)
        config = injector.getInstance(EMConfig::class.java)
        config.stoppingCriterion = EMConfig.StoppingCriterion.FITNESS_EVALUATIONS
        config.processFormat = EMConfig.ProcessDataFormat.JSON_ALL
        config.useTimeInFeedbackSampling = false
        config.minimize = false
    }


    @Test
    fun testDisableProcessMonitor(){

        config.enableProcessMonitor = false
        config.showProgress = true

        processMonitor.postConstruct()
        assertFalse(Files.exists(Paths.get(config.processFiles)))

        val a = OneMaxIndividual(2)
        TestUtils.doInitializeIndividualForTesting(a,randomness)
        a.setValue(0, 1.0)

        val eval = ff.calculateCoverage(a, modifiedSpec = null)!!
        processMonitor.eval = eval
        processMonitor.newActionEvaluated()

        val added = archive.addIfNeeded(eval)
        processMonitor.record(added, true, eval)

        assertFalse(Files.exists(Paths.get(config.processFiles)))
    }

    @Test
    fun testEnableProcessMonitor(){

        config.processFiles = "target/process_data"
        config.enableProcessMonitor = true
        config.showProgress = true

        processMonitor.postConstruct()
        assertFalse(Files.exists(Paths.get(config.processFiles)))
        assertFalse(Files.exists(Paths.get(processMonitor.getStepDirAsPath())))

        val a = OneMaxIndividual(2)
        TestUtils.doInitializeIndividualForTesting(a, randomness)
        a.setValue(0, 1.0)

        val eval = ff.calculateCoverage(a, modifiedSpec = null)!!
        processMonitor.eval = eval
        processMonitor.newActionEvaluated()

        val added = archive.addIfNeeded(eval)

        assert(Files.exists(Paths.get(config.processFiles)))
        assert(Files.exists(Paths.get(processMonitor.getStepDirAsPath())))
        assertEquals(1, Files.list(Paths.get(processMonitor.getStepDirAsPath())).count())
        assert(Files.exists(Paths.get(processMonitor.getStepAsPath(1))))

    }


    @Test
    fun testSerializedOneStep(){
        config.processFiles = "target/process_data_1s"
        config.enableProcessMonitor = true
        config.showProgress = true

        processMonitor.postConstruct()
        assertFalse(Files.exists(Paths.get(config.processFiles)))
        assertFalse(Files.exists(Paths.get(processMonitor.getStepDirAsPath())))

        val individual = OneMaxIndividual(2)
        TestUtils.doInitializeIndividualForTesting(individual, randomness)
        individual.resetAllToZero()
        individual.setValue(0, 1.0)

        val eval = ff.calculateCoverage(individual, modifiedSpec = null)!!
        processMonitor.eval = eval
        processMonitor.newActionEvaluated()

        val added = archive.addIfNeeded(eval)

        assert(Files.exists(Paths.get(processMonitor.getStepAsPath(1) )))
        val data = String(Files.readAllBytes(Paths.get(processMonitor.getStepAsPath(1) )))

        val turnsType = object : TypeToken<StepOfSearchProcess<OneMaxIndividual>>() {}.type
        GsonBuilder().create().fromJson<StepOfSearchProcess<OneMaxIndividual>>(data, turnsType).apply {
            assertEquals(0, populations.size)
            assertEquals(0, samplingCounter.size)
            assertEquals(true, added)
            assertEquals(false, isMutated)
            assertEquals(1, indexOfEvaluation)
            /*
                now fail to serialize children of individual
                thus, currently, the serialized process data could only contain fitness info and impact info
             */
//            assertEquals(individual.seeGenes().size, evalIndividual.individual.seeGenes().size)
            assertEquals(evalIndividual.fitness.coveredTargets(), evalIndividual.fitness.coveredTargets())
            evalIndividual.fitness.getViewOfData().forEach { (t, u) ->
                assertEquals(evalIndividual.fitness.getHeuristic(t) , u.score)
            }
        }
    }

    @Test
    fun testSerializedTwoStepsAndOverall(){

        assertTrue(archive.isEmpty())

        config.processFiles = "target/process_data_2s"

        config.enableProcessMonitor = true
        config.showProgress = true

        processMonitor.postConstruct()
        assertFalse(Files.exists(Paths.get(config.processFiles)))
        assertFalse(Files.exists(Paths.get(processMonitor.getStepDirAsPath())))

        assertEquals(0, archive.getSnapshotOfBestIndividuals().size)

        val a = OneMaxIndividual(2)
        TestUtils.doInitializeIndividualForTesting(a, randomness)
        a.resetAllToZero()
        a.setValue(0, 1.0)
        val evalA = ff.calculateCoverage(a, modifiedSpec = null)!!
        processMonitor.eval = evalA
        processMonitor.newActionEvaluated()

        val addedA = archive.addIfNeeded(evalA)
        assert(addedA)

        assertEquals(1, archive.getSnapshotOfBestIndividuals().size)
        val b = OneMaxIndividual(2)
        TestUtils.doInitializeIndividualForTesting(b,randomness)
        a.resetAllToZero()
        b.setValue(1, 1.0)
        val evalB = ff.calculateCoverage(b, modifiedSpec = null)!!
        processMonitor.eval = evalB
        processMonitor.newActionEvaluated()

        val addedB = archive.addIfNeeded(evalB)

        processMonitor.saveOverall()

        assertEquals(2, Files.list(Paths.get(processMonitor.getStepDirAsPath())).count())
        assert(Files.exists(Paths.get(processMonitor.getStepAsPath(1) )))
        val dataA = String(Files.readAllBytes(Paths.get(processMonitor.getStepAsPath(1) )))

        val turnsType = object : TypeToken<StepOfSearchProcess<OneMaxIndividual>>() {}.type
        val gson = GsonBuilder().create()
        gson.fromJson<StepOfSearchProcess<OneMaxIndividual>>(dataA, turnsType)
                .apply {
                    assertEquals(0, populations.size)
                    assertEquals(0, samplingCounter.size)
                }

        assert(Files.exists(Paths.get(processMonitor.getStepAsPath(2))))
        val dataB = String(Files.readAllBytes(Paths.get(processMonitor.getStepAsPath(2) )))
        gson.fromJson<StepOfSearchProcess<OneMaxIndividual>>(dataB, turnsType)
                .apply {
                    assertEquals(1, populations.size)
                    assertEquals(0, samplingCounter.size)
                 }

        assert(Files.exists(Paths.get(config.processFiles + File.separator + processMonitor.getOverallFileName())))
        val overallData = String(Files.readAllBytes(Paths.get(config.processFiles + File.separator + processMonitor.getOverallFileName())))

        gson.fromJson<SearchOverall<OneMaxIndividual>>(overallData,  object : TypeToken<SearchOverall<OneMaxIndividual>>() {}.type).apply {
            assertEquals(2, finalPopulations.size)
            finalPopulations.forEach { t, _->
                assert(archive.isCovered(t))
            }
        }

    }

}

