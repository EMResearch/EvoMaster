package org.evomaster.core.problem.graphql.builder

import org.evomaster.core.EMConfig
import org.evomaster.core.problem.graphql.GraphQLAction
import org.evomaster.core.problem.graphql.param.GQInputParam
import org.evomaster.core.problem.graphql.param.GQReturnParam
import org.evomaster.core.search.action.Action
import org.evomaster.core.search.gene.*
import org.evomaster.core.search.gene.collection.ArrayGene
import org.evomaster.core.search.gene.collection.EnumGene
import org.evomaster.core.search.gene.collection.TupleGene
import org.evomaster.core.search.gene.numeric.IntegerGene
import org.evomaster.core.search.gene.optional.NullableGene
import org.evomaster.core.search.gene.optional.OptionalGene
import org.evomaster.core.search.gene.placeholder.CycleObjectGene
import org.evomaster.core.search.gene.placeholder.LimitObjectGene
import org.evomaster.core.search.gene.string.StringGene
import org.evomaster.core.search.gene.utils.GeneUtils
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.lang.Integer.max


class GraphQLActionBuilderTest {


    @Test
    fun testPetClinic() {

        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/PetsClinic.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(15, actionCluster.size)

        val pettypes = actionCluster["pettypes"] as GraphQLAction
        assertEquals(1, pettypes.parameters.size)
        assertTrue(pettypes.parameters[0] is GQReturnParam)
        assertTrue(pettypes.parameters[0].gene is ObjectGene)
        val objPetType = pettypes.parameters[0].gene as ObjectGene
        assertEquals(2, objPetType.fields.size)
        assertTrue(objPetType.fields.any { it is BooleanGene && it.name == "id" })
        assertTrue(objPetType.fields.any { it is BooleanGene && it.name == "name" })
        val gQlReturn = GQReturnParam(pettypes.parameters[0].name, pettypes.parameters[0].gene)
        val gQlInputcopy = gQlReturn.copy()
        assertEquals(gQlReturn.name, gQlInputcopy.name)
        assertEquals(gQlReturn.gene.name, gQlInputcopy.gene.name)
        /**/
        val vets = actionCluster["vets"] as GraphQLAction
        assertEquals(1, vets.parameters.size)
        assertTrue(vets.parameters[0] is GQReturnParam)
        assertTrue(vets.parameters[0].gene is ObjectGene)
        val objVets = vets.parameters[0].gene as ObjectGene
        assertEquals(4, objVets.fields.size)
        assertTrue(objVets.fields.any { it is BooleanGene && it.name == "id" })
        assertTrue(objVets.fields.any { it is BooleanGene && it.name == "firstName" })
        assertTrue(objVets.fields.any { it is BooleanGene && it.name == "lastName" })
        assertTrue(objVets.fields.any { it is OptionalGene && it.name == "specialties" })

        val objSpecialty = (objVets.fields.first { it.name == "specialties" } as OptionalGene).gene as ObjectGene
        assertEquals(2, objSpecialty.fields.size)
        assertTrue(objSpecialty.fields.any { it is BooleanGene && it.name == "id" })
        assertTrue(objSpecialty.fields.any { it is BooleanGene && it.name == "name" })
        /**/
        val owners = actionCluster["owners"] as GraphQLAction
        assertEquals(3, owners.parameters.size)
        assertTrue(owners.parameters[0] is GQInputParam)
        assertTrue(owners.parameters[0].name == "filter")
        //assertTrue((owners.parameters[0].gene as OptionalGene).gene is ObjectGene)
        assertTrue((owners.parameters[0].gene.getWrappedGene(ObjectGene::class.java) != null))
        //val objOwnerFilter = (owners.parameters[0].gene as OptionalGene).gene as ObjectGene
        val objOwnerFilter = owners.parameters[0].gene.getWrappedGene(ObjectGene::class.java)
        if (objOwnerFilter != null) {
            assertTrue(objOwnerFilter.fields.any { it.getWrappedGene(OptionalGene::class.java)?.gene?.name == "firstName" })
            assertTrue(objOwnerFilter.fields.any { it.getWrappedGene(OptionalGene::class.java)?.gene?.name == "lastName" })
            assertTrue(objOwnerFilter.fields.any { it.getWrappedGene(OptionalGene::class.java)?.gene?.name == "address" })
            assertTrue(objOwnerFilter.fields.any { it.getWrappedGene(OptionalGene::class.java)?.gene?.name == "city" })
            assertTrue(objOwnerFilter.fields.any { it.getWrappedGene(OptionalGene::class.java)?.gene?.name == "telephone" })
        }
        assertTrue(owners.parameters[1] is GQInputParam)
        assertTrue(owners.parameters[1].name == "orders")
        assertTrue(owners.parameters[2] is GQReturnParam)
        assertTrue(owners.parameters[2].gene is ObjectGene)
        /**/
        val owner = owners.parameters[2].gene as ObjectGene
        assertEquals(7, owner.fields.size)
        assertTrue(owner.fields.any { it is BooleanGene && it.name == "id" })
        assertTrue(owner.fields.any { it is BooleanGene && it.name == "firstName" })
        assertTrue(owner.fields.any { it is BooleanGene && it.name == "lastName" })
        assertTrue(owner.fields.any { it is BooleanGene && it.name == "address" })
        assertTrue(owner.fields.any { it is BooleanGene && it.name == "city" })
        assertTrue(owner.fields.any { it is BooleanGene && it.name == "telephone" })
        assertTrue(owner.fields.any { it is OptionalGene && it.name == "pets" })
        val objPet = ((owner.fields.first { it.name == "pets" }) as OptionalGene).gene as ObjectGene
        assertEquals(6, objPet.fields.size)
        assertTrue(objPet.fields.any { it is BooleanGene && it.name == "id" })
        assertTrue(objPet.fields.any { it is BooleanGene && it.name == "name" })
        assertTrue(objPet.fields.any { it is BooleanGene && it.name == "birthDate" })
        assertTrue(objPet.fields.any { it is OptionalGene && it.name == "type" })
        assertTrue(objPet.fields.any { it is OptionalGene && it.name == "visits" })
        assertTrue(objPet.fields[5] is OptionalGene)
        val objVisitConnection = (objPet.fields[5] as OptionalGene).gene as ObjectGene
        assertEquals(2, objVisitConnection.fields.size)
        assertTrue(objVisitConnection.fields[0] is BooleanGene)
        assertTrue(objVisitConnection.fields.any { it is BooleanGene && it.name == "totalCount" })
        assertTrue(objVisitConnection.fields.any { it is OptionalGene && it.name == "visits" })
        GeneUtils.repairBooleanSelection(owner); // this should not fail
        /**/
        val pet = actionCluster["pet"] as GraphQLAction
        assertEquals(2, pet.parameters.size)
        assertTrue(pet.parameters[0] is GQInputParam)
        assertTrue(pet.parameters[0].gene is IntegerGene)
        assertTrue(pet.parameters[0].gene.name == "id")
        assertTrue(pet.parameters[1] is GQReturnParam)
        assertTrue(pet.parameters[1].gene is ObjectGene)
        val objPet2 = (pet.parameters[1].gene as ObjectGene)
        assertEquals(6, objPet2.fields.size)
        assertTrue(objPet2.fields.any { it is OptionalGene && it.name == "visits" })
        GeneUtils.repairBooleanSelection(objPet2); // this should not fail
        /**/
        val specialties = actionCluster["specialties"] as GraphQLAction
        assertEquals(1, specialties.parameters.size)
        assertTrue(specialties.parameters[0] is GQReturnParam)
        assertTrue(specialties.parameters[0].gene is ObjectGene)

    }


    @Test
    fun bitquerySchemaTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/Bitquery.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(12, actionCluster.size)

        val algorand = actionCluster["algorand"] as GraphQLAction
        assertEquals(2, algorand.parameters.size)
        assertTrue(algorand.parameters[0] is GQInputParam)
        assertTrue(algorand.parameters[1] is GQReturnParam)
        assertTrue(algorand.parameters[1].gene is ObjectGene)
        val objAlgorand = algorand.parameters[1].gene as ObjectGene
        assertEquals(7, objAlgorand.fields.size)
        assertTrue(objAlgorand.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "address" })//1st

    }

    @Test
    fun catalysisHubSchemaTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/CatalysisHub.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(11, actionCluster.size)

    }

    @Test
    fun contentfulSchemaTest() {
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/Contentful.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(23, actionCluster.size)

        val asset = actionCluster["asset"] as GraphQLAction
        assertEquals(4, asset.parameters.size)
        assertTrue(asset.parameters[0] is GQInputParam)
        assertTrue(asset.parameters[1] is GQInputParam)
        assertTrue(asset.parameters[2] is GQInputParam)
        assertTrue(asset.parameters[3] is GQReturnParam)
        assertTrue(asset.parameters[0].gene is StringGene)
        //assertTrue((asset.parameters[1].gene as OptionalGene).gene is BooleanGene)
        assertTrue(asset.parameters[1].gene.getWrappedGene(BooleanGene::class.java) != null)
        assertTrue(asset.parameters[3].gene is ObjectGene)
        /**/
        val categoryCollection = actionCluster["categoryCollection"] as GraphQLAction
        assertEquals(7, categoryCollection.parameters.size)
        assertTrue(categoryCollection.parameters[0] is GQInputParam)
        assertTrue(categoryCollection.parameters[1] is GQInputParam)
        assertTrue(categoryCollection.parameters[2] is GQInputParam)
        assertTrue(categoryCollection.parameters[6] is GQReturnParam)
        //assertTrue((categoryCollection.parameters[0].gene as OptionalGene).gene is IntegerGene)
        assertTrue(categoryCollection.parameters[0].gene.getWrappedGene(IntegerGene::class.java) != null)
        //assertTrue((categoryCollection.parameters[4].gene as OptionalGene).gene is ObjectGene)
        assertTrue((categoryCollection.parameters[4].gene.getWrappedGene(ObjectGene::class.java) != null))
        //assertTrue((((categoryCollection.parameters[4].gene as OptionalGene).gene as ObjectGene).fields[6] as OptionalGene).gene is StringGene)
        assertTrue(((categoryCollection.parameters[4].gene.getWrappedGene(ObjectGene::class.java))?.fields?.get(6)
                ?.getWrappedGene(StringGene::class.java) != null))
        /**/
        val lessonCodeSnippets = actionCluster["lessonCodeSnippets"] as GraphQLAction
        assertEquals(4, lessonCodeSnippets.parameters.size)
        assertTrue(lessonCodeSnippets.parameters[0] is GQInputParam)
        assertTrue(lessonCodeSnippets.parameters[1] is GQInputParam)
        assertTrue(lessonCodeSnippets.parameters[2] is GQInputParam)
        assertTrue(lessonCodeSnippets.parameters[3] is GQReturnParam)
        assertTrue(lessonCodeSnippets.parameters[3].gene is ObjectGene)

        val objLessonCodeSnippets = lessonCodeSnippets.parameters[3].gene as ObjectGene
        assertTrue(objLessonCodeSnippets.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "linkedFrom" })

        val optLinkedFrom = objLessonCodeSnippets.fields.first { it.name == "linkedFrom" }
        val tupleLinkedFrom = optLinkedFrom.getWrappedGene(TupleGene::class.java)
        assertEquals(2, tupleLinkedFrom?.elements?.size)
        if (tupleLinkedFrom !=null) {
            assertTrue(tupleLinkedFrom.elements.any { it is ObjectGene && it.name == "linkedFrom" })
        }

        val objLinkedFrom = tupleLinkedFrom?.elements?.get(1)  as ObjectGene
        assertEquals(2, objLinkedFrom.fields.size)
        assertTrue(objLinkedFrom.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "entryCollection" })

        val optEntryCollection = objLinkedFrom.fields.first { it.name == "entryCollection" }
        val tupleEntryCollection = optEntryCollection.getWrappedGene(TupleGene::class.java)
        assertEquals(5, tupleEntryCollection?.elements?.size)
        //assertTrue(tupleEntryCollection.elements.any { it is OptionalGene && it.name == "skip" })
        if (tupleEntryCollection != null) {
            assertTrue(tupleEntryCollection.elements.any { it.getWrappedGene(OptionalGene::class.java)?.gene?.name == "skip" })
        //assertTrue(tupleEntryCollection.elements.any { it is OptionalGene && it.name == "limit" })
            assertTrue(tupleEntryCollection.elements.any { it.getWrappedGene(OptionalGene::class.java)?.gene?.name == "limit" })
        //assertTrue(tupleEntryCollection.elements.any { it is OptionalGene && it.name == "preview" })
            assertTrue(tupleEntryCollection.elements.any { it.getWrappedGene(OptionalGene::class.java)?.gene?.name == "preview" })
        //assertTrue(tupleEntryCollection.elements.any { it is OptionalGene && it.name == "locale" })
            assertTrue(tupleEntryCollection.elements.any { it.getWrappedGene(OptionalGene::class.java)?.gene?.name == "locale" })
            assertTrue(tupleEntryCollection.elements.any { it is ObjectGene && it.name == "entryCollection" })
            }

    }

    @Test
    fun countriesSchemaTest() {
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/Countries.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(6, actionCluster.size)

        val continents = actionCluster["continents"] as GraphQLAction
        assertEquals(2, continents.parameters.size)
        assertTrue(continents.parameters[0] is GQInputParam)
        assertTrue(continents.parameters[1] is GQReturnParam)
        assertTrue(continents.parameters[1].gene is ObjectGene)
        val objContinents = continents.parameters[1].gene as ObjectGene
        assertTrue(objContinents.fields[2] is OptionalGene)
        val objCountry = (objContinents.fields[2] as OptionalGene).gene as ObjectGene
        assertTrue(objCountry.fields[7] is OptionalGene)

    }

    @Test
    fun deutscheBahnSchemaTest() {
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/DeutscheBahn.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(7, actionCluster.size)

        val routing = actionCluster["routing"] as GraphQLAction
        assertEquals(3, routing.parameters.size)
        assertTrue(routing.parameters[0] is GQInputParam)
        assertTrue(routing.parameters[2] is GQReturnParam)
        assertTrue(routing.parameters[2].gene is ObjectGene)

    }

    @Test
    fun digitransitHSLSchemaTest() {
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/DigitransitHSL.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(33, actionCluster.size)

        val node = actionCluster["node"] as GraphQLAction
        assertEquals(2, node.parameters.size)
        assertTrue(node.parameters[1] is GQReturnParam)

        assertTrue(node.parameters[1].gene is ObjectGene)
        val interfaceObjectNode = node.parameters[1].gene as ObjectGene
        assertEquals(15, interfaceObjectNode.fields.size)

        assertTrue(interfaceObjectNode.fields[0] is OptionalGene)
        assertTrue((interfaceObjectNode.fields[0] as OptionalGene).gene is ObjectGene)
        val objAgency = (interfaceObjectNode.fields[0] as OptionalGene).gene as ObjectGene
        assertEquals(9, objAgency.fields.size)
        assertTrue(objAgency.fields.any { it is BooleanGene && it.name == "lang" })
        assertTrue(objAgency.fields.any { it is BooleanGene && it.name == "phone" })
        /**/
        val stations = actionCluster["stations"] as GraphQLAction
        assertEquals(5, stations.parameters.size)
        assertTrue(stations.parameters[4] is GQReturnParam)

        assertTrue(stations.parameters[4].gene is ObjectGene)
       val objectStop = stations.parameters[4].gene as ObjectGene
        assertEquals(27, objectStop.fields.size)

        assertTrue(objectStop.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "stopTimesForPattern" })

        val optStopTimesForPattern = objectStop.fields.first { it.name == "stopTimesForPattern" }
        val tupleStopTimesForPattern = optStopTimesForPattern.getWrappedGene(TupleGene::class.java)
        assertEquals(7, tupleStopTimesForPattern?.elements?.size)

        if (tupleStopTimesForPattern !=null) {
            assertTrue(tupleStopTimesForPattern.elements.any { it is ObjectGene && it.name == "stopTimesForPattern" })
        }

        val objStopTimesForPattern = tupleStopTimesForPattern?.elements?.get(6)  as ObjectGene
        assertEquals(17, objStopTimesForPattern.fields.size)
        assertTrue(objStopTimesForPattern.fields.any { it.getWrappedGene(ObjectGene::class.java)?.name == "trip" })


        val optTrip = objStopTimesForPattern.fields.first { it.name == "trip" }
        val objTrip = optTrip.getWrappedGene(ObjectGene::class.java)

        if(objTrip!=null) {
            assertEquals(22, objTrip?.fields?.size)
            assertTrue(objTrip.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "departureStoptime" })//nbr17
            val optDepartureStoptime = objTrip.fields.first { it.name == "departureStoptime" }
            val tupleDepartureStoptime = optDepartureStoptime.getWrappedGene(TupleGene::class.java)
            assertEquals(2, tupleDepartureStoptime?.elements?.size)
            assertTrue(tupleDepartureStoptime?.elements?.last() is (CycleObjectGene))
        }


    }

    @Test
    fun eHRISchemaTest() {
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/EHRI.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(19, actionCluster.size)
        /**/
        val country = actionCluster["Country"] as GraphQLAction
        assertEquals(2, country.parameters.size)
        assertTrue(country.parameters[1] is GQReturnParam)

        assertTrue(country.parameters[1].gene is ObjectGene)

        val objCountry = country.parameters[1].gene as ObjectGene

        assertTrue(objCountry.fields.any { it.getWrappedGene(ObjectGene::class.java)?.name == "annotations" })

        val optAnnotations = objCountry.fields.first { it.name == "annotations" }
        val objAnnotations = optAnnotations.getWrappedGene(ObjectGene::class.java)

        if (objAnnotations != null) {
            val interfaceObjectTargets = objAnnotations.fields[6].getWrappedGene(ObjectGene::class.java)//targets
            if (interfaceObjectTargets != null) {
                assertEquals(10, interfaceObjectTargets.fields.size)
                assertTrue(interfaceObjectTargets.fields[8].getWrappedGene(ObjectGene::class.java)?.name == "Link")

                val optLink = interfaceObjectTargets.fields.first { it.name == "Link" }
                val objLink = optLink.getWrappedGene(ObjectGene::class.java)

                if (objLink != null) {
                    assertEquals(6, objLink.fields.size)
                    assertTrue(objLink.fields.any { it is BooleanGene && it.name == "field" })
                }
            }
        }
        /**/
        val documentaryUnits = actionCluster["documentaryUnits"] as GraphQLAction
        assertEquals(5, documentaryUnits.parameters.size)
        assertTrue(documentaryUnits.parameters[4] is GQReturnParam)

        assertTrue(documentaryUnits.parameters[4].gene is ObjectGene)
        val objectDocumentaryUnits = documentaryUnits.parameters[4].gene as ObjectGene
        assertEquals(3, objectDocumentaryUnits.fields.size)
        assertTrue(objectDocumentaryUnits.fields.any { it.getWrappedGene(ObjectGene::class.java)?.name == "edges" })

        val optDocumentaryUnitEdge = objectDocumentaryUnits.fields.first { it.name == "edges" }
        val objDocumentaryUnitEdge = optDocumentaryUnitEdge.getWrappedGene(ObjectGene::class.java)
        if(objDocumentaryUnitEdge!=null) {
            assertEquals(2, objDocumentaryUnitEdge.fields.size)
            assertTrue(objDocumentaryUnitEdge.fields.any { it.getWrappedGene(ObjectGene::class.java)?.name == "node" })
            val optNode = objDocumentaryUnitEdge.fields.first { it.name == "node" }
            val objNode = optNode.getWrappedGene(ObjectGene::class.java)
            if (objNode != null) {
                assertEquals(14, objNode.fields.size)
                assertTrue(objNode.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "children" })//nbr07
                val optChildren = objNode.fields.first { it.name == "children" }
                val tupleChildren = optChildren.getWrappedGene(TupleGene::class.java)
                assertEquals(5, tupleChildren?.elements?.size)
                assertTrue(tupleChildren?.elements?.last() is (CycleObjectGene))

            }
        }
    }

    @Test
    fun etMDBSchemaTest() {
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/EtMDB.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(24, actionCluster.size)

    }

    @Test
    fun everbaseSchemaTest() {
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/Everbase.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(14, actionCluster.size)

    }


    @Test
    fun graphQLJobsSchemaTest() {
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/GraphQLJobs.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(15, actionCluster.size)

    }

    @Test
    fun HIVDBSchemaTest() {
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/HIVDB.json").readText()
        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(9, actionCluster.size)
        val sequenceAnalysis = actionCluster["sequenceAnalysis"] as GraphQLAction
        assertEquals(2, sequenceAnalysis.parameters.size)
        assertTrue(sequenceAnalysis.parameters[1] is GQReturnParam)
        val objSequenceAnalysis = sequenceAnalysis.parameters[1].gene as ObjectGene

        assertTrue(objSequenceAnalysis.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "drugResistance" })
        val optDrugResistance = objSequenceAnalysis.fields.first { it.name == "drugResistance" }
        val tupleDrugResistance = optDrugResistance.getWrappedGene(TupleGene::class.java)
        if (tupleDrugResistance != null) {
            assertEquals(2, tupleDrugResistance.elements.size)
            assertTrue(tupleDrugResistance.elements.any { it.getWrappedGene(ObjectGene::class.java)?.name == "drugResistance" })
            val objDrugResistance = tupleDrugResistance?.elements?.get(1) as ObjectGene
            assertTrue(objDrugResistance.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "drugScores" })
            val optDrugScores = objDrugResistance.fields.first { it.name == "drugScores" }
            val tupleDrugScores = optDrugScores.getWrappedGene(TupleGene::class.java)
            if (tupleDrugScores != null) {
                assertEquals(2, tupleDrugScores.elements.size)
                assertTrue(tupleDrugScores.elements.any { it.getWrappedGene(EnumGene::class.java)?.name == "drugClass" })
            }
        }
        /**/
        val patternAnalysis = actionCluster["patternAnalysis"] as GraphQLAction
        assertEquals(2, patternAnalysis.parameters.size)
        assertTrue(patternAnalysis.parameters[1] is GQReturnParam)

        assertTrue(patternAnalysis.parameters[1].gene is ObjectGene)

        val obMutationsAnalysis = patternAnalysis.parameters[1].gene as ObjectGene

        assertTrue(obMutationsAnalysis.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "algorithmComparison" })


        val optAlgorithmComparison = obMutationsAnalysis.fields.first { it.name == "algorithmComparison" }
        val tupleAlgorithmComparison = optAlgorithmComparison.getWrappedGene(TupleGene::class.java)
        assertEquals(3, tupleAlgorithmComparison?.elements?.size)

        if (tupleAlgorithmComparison !=null) {
            assertTrue(tupleAlgorithmComparison.elements.any { it.getWrappedGene(ArrayGene::class.java)?.name == "customAlgorithms" })
        }
    }

    @Test
    fun melodyRepoSchemaTest() {
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/MelodyRepo.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(2, actionCluster.size)

        val ppackage = actionCluster["package"] as GraphQLAction
        assertEquals(2, ppackage.parameters.size)
        assertTrue(ppackage.parameters[0] is GQInputParam)
        assertTrue(ppackage.parameters[0].gene is StringGene)
        val objPackage = ppackage.parameters[1].gene as ObjectGene
        assertTrue(objPackage.fields.any { it is BooleanGene && it.name == "isMain" })
        assertTrue(objPackage.fields[2] is OptionalGene)
        val objVersion = (objPackage.fields[2] as OptionalGene).gene as ObjectGene
        objVersion.fields.any { it is BooleanGene && it.name == "name" }
        assertTrue(ppackage.parameters[1] is GQReturnParam)
        assertTrue(ppackage.parameters[1].gene is ObjectGene)
    }

    @Test
    fun reactFinlandSchemaTest() {
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/ReactFinland.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(12, actionCluster.size)

    }

    @Test
    fun travelgateXSchemaTest() {
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/TravelgateX.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(9, actionCluster.size)
        /**/
        val admin = actionCluster["admin"] as GraphQLAction
        assertEquals(1, admin.parameters.size)
        assertTrue(admin.parameters[0] is GQReturnParam)
        assertTrue(admin.parameters[0].gene is ObjectGene)
        /**/
        val hotelX = actionCluster["hotelX"] as GraphQLAction
        assertEquals(1, admin.parameters.size)
        assertTrue(hotelX.parameters[0] is GQReturnParam)
        /**/
        val logging = actionCluster["logging"] as GraphQLAction
        assertEquals(1, logging.parameters.size)
        assertTrue(logging.parameters[0] is GQReturnParam)
        assertTrue(logging.parameters[0].gene is ObjectGene)
    }


    @Test
    fun recEgTest() {
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/artificial/recEg.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(1, actionCluster.size)
    }

    @Test
    fun spaceXTest() {
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/SpaceX.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(43, actionCluster.size)

        val coresUpcoming = actionCluster["coresUpcoming"] as GraphQLAction
        assertEquals(6, coresUpcoming.parameters.size)
        assertTrue(coresUpcoming.parameters[0] is GQInputParam)
        assertTrue(coresUpcoming.parameters[1] is GQInputParam)
        assertTrue(coresUpcoming.parameters[2] is GQInputParam)
        assertTrue(coresUpcoming.parameters[5] is GQReturnParam)
        //assertTrue((coresUpcoming.parameters[0].gene as OptionalGene).gene is ObjectGene)
        assertTrue(coresUpcoming.parameters[0].gene.getWrappedGene(ObjectGene::class.java) != null)
        assertTrue(coresUpcoming.parameters[5].gene is ObjectGene)
        val objCore = coresUpcoming.parameters[5].gene as ObjectGene
        assertTrue(objCore.fields.any { it is BooleanGene && it.name == "water_landing" })

    }

    @Test
    fun bookTest() {
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/artificial/Book.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(3, actionCluster.size)
    }


    @Test
    fun interfaceEgTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/artificial/interfaceEg.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(1, actionCluster.size)


        val stores = actionCluster["stores"] as GraphQLAction
        assertEquals(1, stores.parameters.size)
        assertTrue(stores.parameters[0] is GQReturnParam)

        assertTrue(stores.parameters[0].gene is ObjectGene)
        val interfaceObjectStore = stores.parameters[0].gene as ObjectGene
        assertEquals(2, interfaceObjectStore.fields.size)

        assertTrue(interfaceObjectStore.fields[0] is OptionalGene)
        assertTrue((interfaceObjectStore.fields[0] as OptionalGene).gene is ObjectGene)
        val objPotStore = (interfaceObjectStore.fields[0] as OptionalGene).gene as ObjectGene
        assertEquals(1, objPotStore.fields.size)
        assertTrue(objPotStore.fields.any { it is BooleanGene && it.name == "address" })

        assertTrue(interfaceObjectStore.fields[1] is OptionalGene)
        assertTrue((interfaceObjectStore.fields[1] as OptionalGene).gene is ObjectGene)
        val objStore = (interfaceObjectStore.fields[1] as OptionalGene).gene as ObjectGene
        assertEquals(2, objStore.fields.size)
        assertTrue(objStore.fields.any { it is BooleanGene && it.name == "id" })
        assertTrue(objStore.fields.any { it is BooleanGene && it.name == "name" })

    }

    @Test
    fun interfaceEgFunctionTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json =
            GraphQLActionBuilderTest::class.java.getResource("/graphql/artificial/interfaceEgFunction.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(1, actionCluster.size)

        val stores = actionCluster["stores"] as GraphQLAction
        assertEquals(1, stores.parameters.size)
        assertTrue(stores.parameters[0] is GQReturnParam)

        assertTrue(stores.parameters[0].gene is ObjectGene)
        val interfaceObjectStore = stores.parameters[0].gene as ObjectGene
        assertEquals(2, interfaceObjectStore.fields.size)

        assertTrue(interfaceObjectStore.fields[0] is OptionalGene)
        assertTrue((interfaceObjectStore.fields[0] as OptionalGene).gene is ObjectGene)
        val objPotStore = (interfaceObjectStore.fields[0] as OptionalGene).gene as ObjectGene
        assertEquals(1, objPotStore.fields.size)

        assertTrue(objPotStore.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "address" })

        val optAddress = objPotStore.fields.first { it.name == "address" }
        val tupleAddress = optAddress.getWrappedGene(TupleGene::class.java)
        if (tupleAddress != null) {
            assertEquals(1, tupleAddress.elements.size)
        //assertTrue(tupleAddress.elements.any { it is OptionalGene && it.gene is IntegerGene && it.name == "y" })
            assertTrue(tupleAddress.elements.any { it.getWrappedGene(IntegerGene::class.java)?.name == "y" })
        }

        assertTrue(interfaceObjectStore.fields[1] is OptionalGene)
        assertTrue((interfaceObjectStore.fields[1] as OptionalGene).gene is ObjectGene)
        val objStore = (interfaceObjectStore.fields[1] as OptionalGene).gene as ObjectGene
        assertEquals(2, objStore.fields.size)
        assertTrue(objStore.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "id" })
        assertTrue(objStore.fields.any { it is BooleanGene && it.name == "name" })

        val optId = objStore.fields.first { it.name == "id" }
        val tupleId = optId.getWrappedGene(TupleGene::class.java)

        if (tupleId != null) {
            assertEquals(1, tupleId.elements.size)
            //assertTrue(tupleId.elements.any { it is OptionalGene && it.gene is IntegerGene && it.name == "x" })
            assertTrue(tupleId.elements.any { it.getWrappedGene(IntegerGene::class.java)?.name == "x" })
        }
    }

    @Test
    fun interfaceInternalEgTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json =
            GraphQLActionBuilderTest::class.java.getResource("/graphql/artificial/interfaceInternalEg.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(1, actionCluster.size)

        val stores = actionCluster["stores"] as GraphQLAction
        assertEquals(1, stores.parameters.size)
        assertTrue(stores.parameters[0] is GQReturnParam)

        assertTrue(stores.parameters[0].gene is ObjectGene)
        val objectStore1 = stores.parameters[0].gene as ObjectGene
        assertEquals(1, objectStore1.fields.size)

        assertTrue(objectStore1.fields[0] is OptionalGene)
        assertTrue((objectStore1.fields[0] as OptionalGene).gene is ObjectGene)
        val interfaceObjectStore = (objectStore1.fields[0] as OptionalGene).gene as ObjectGene
        assertEquals(2, interfaceObjectStore.fields.size)

        assertTrue(interfaceObjectStore.fields[0] is OptionalGene)
        assertTrue((interfaceObjectStore.fields[0] as OptionalGene).gene is ObjectGene)
        val objPotStore = (interfaceObjectStore.fields[0] as OptionalGene).gene as ObjectGene
        assertEquals(1, objPotStore.fields.size)
        assertTrue(objPotStore.fields.any { it is BooleanGene && it.name == "address" })

        assertTrue(interfaceObjectStore.fields[1] is OptionalGene)
        assertTrue((interfaceObjectStore.fields[1] as OptionalGene).gene is ObjectGene)
        val objStore = (interfaceObjectStore.fields[1] as OptionalGene).gene as ObjectGene
        assertEquals(2, objStore.fields.size)
        assertTrue(objStore.fields.any { it is BooleanGene && it.name == "id" })
        assertTrue(objStore.fields.any { it is BooleanGene && it.name == "name" })

    }

    @Test
    fun unionInternalEgTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json =
            GraphQLActionBuilderTest::class.java.getResource("/graphql/artificial/unionInternalEg.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(1, actionCluster.size)

        val stores = actionCluster["stores"] as GraphQLAction
        assertEquals(1, stores.parameters.size)
        assertTrue(stores.parameters[0] is GQReturnParam)

        assertTrue(stores.parameters[0].gene is ObjectGene)
        val objectStore = stores.parameters[0].gene as ObjectGene
        assertEquals(2, objectStore.fields.size)
        assertTrue(objectStore.fields[0] is BooleanGene)
        assertTrue(objectStore.fields[1] is OptionalGene)
        assertTrue((objectStore.fields[1] as OptionalGene).gene is ObjectGene)
        val unionObjBouquet = (objectStore.fields[1] as OptionalGene).gene as ObjectGene
        assertEquals(2, unionObjBouquet.fields.size)
        assertTrue(unionObjBouquet.fields[0] is OptionalGene)
        assertTrue((unionObjBouquet.fields[0] as OptionalGene).gene is ObjectGene)
        val objFlower = (unionObjBouquet.fields[0] as OptionalGene).gene as ObjectGene
        assertTrue(objFlower.name == "Flower")
        assertTrue(objFlower.fields.any { it is BooleanGene && it.name == "id" })
        assertTrue(objFlower.fields.any { it is BooleanGene && it.name == "color" })
        /**/
        assertTrue(unionObjBouquet.fields[1] is OptionalGene)
        assertTrue((unionObjBouquet.fields[1] as OptionalGene).gene is ObjectGene)
        val objPot = (unionObjBouquet.fields[1] as OptionalGene).gene as ObjectGene

        assertTrue(objPot.fields.any { it is BooleanGene && it.name == "id" })
        assertTrue(objPot.fields.any { it is BooleanGene && it.name == "size" })

    }

    @Test
    fun unionInternalFunctionsEgTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/artificial/unionInternalFunctionsEg.json")
            .readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(1, actionCluster.size)

        val stores = actionCluster["stores"] as GraphQLAction
        assertEquals(1, stores.parameters.size)
        assertTrue(stores.parameters[0] is GQReturnParam)

        assertTrue(stores.parameters[0].gene is ObjectGene)
        val objectStore = stores.parameters[0].gene as ObjectGene
        assertEquals(2, objectStore.fields.size)
        assertTrue(objectStore.fields[0] is BooleanGene)
        assertTrue(objectStore.fields[1] is OptionalGene)
        assertTrue((objectStore.fields[1] as OptionalGene).gene is ObjectGene)
        val unionObjBouquet = (objectStore.fields[1] as OptionalGene).gene as ObjectGene
        assertEquals(2, unionObjBouquet.fields.size)

        assertTrue(unionObjBouquet.fields[0] is OptionalGene)
        assertTrue((unionObjBouquet.fields[0] as OptionalGene).gene is ObjectGene)
        val objFlower = (unionObjBouquet.fields[0] as OptionalGene).gene as ObjectGene
        assertTrue(objFlower.fields.any { it is BooleanGene && it.name == "id" })
        assertTrue(objFlower.fields.any { it is BooleanGene && it.name == "color" })
        assertTrue(objFlower.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "name" })

        val optName = objFlower.fields.first { it.name == "name" }
        val tupleName = optName.getWrappedGene(TupleGene::class.java)
        if (tupleName != null) {
            assertEquals(1, tupleName.elements.size)
            //assertTrue(tupleName.elements.any { it is OptionalGene && it.gene is IntegerGene && it.name == "x" })
            assertTrue(tupleName.elements.any { it.getWrappedGene(IntegerGene::class.java)?.name == "x" })
        }
        /**/
        assertTrue(unionObjBouquet.fields[1] is OptionalGene)
        assertTrue((unionObjBouquet.fields[1] as OptionalGene).gene is ObjectGene)
        val objPot = (unionObjBouquet.fields[1] as OptionalGene).gene as ObjectGene

        assertTrue(objPot.fields.any { it is BooleanGene && it.name == "id" })
        assertTrue(objPot.fields.any { it is BooleanGene && it.name == "size" })
        assertTrue(objPot.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "color" })

        val optColor = objPot.fields.first { it.name == "color" }
        val tupleColor = optColor.getWrappedGene(TupleGene::class.java)
        if (tupleColor != null) {
            assertEquals(1, tupleColor.elements.size)
            //assertTrue(tupleColor.elements.any { it is OptionalGene && it.gene is IntegerGene && it.name == "y" })
            assertTrue(tupleColor.elements.any { it.getWrappedGene(IntegerGene::class.java)?.name == "y" })
        }
    }

    @Test
    fun unionInternalRecEgTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json =
            GraphQLActionBuilderTest::class.java.getResource("/graphql/artificial/unionInternalRecEg.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(1, actionCluster.size)

        val stores = actionCluster["stores"] as GraphQLAction
        assertEquals(1, stores.parameters.size)
        assertTrue(stores.parameters[0] is GQReturnParam)

        assertTrue(stores.parameters[0].gene is ObjectGene)
        val objectStore = stores.parameters[0].gene as ObjectGene
        assertEquals(2, objectStore.fields.size)
        assertTrue(objectStore.fields[0] is BooleanGene)
        assertTrue(objectStore.fields[1] is OptionalGene)
        assertTrue((objectStore.fields[1] as OptionalGene).gene is ObjectGene)
        val unionObjBouquet = (objectStore.fields[1] as OptionalGene).gene as ObjectGene
        assertEquals(2, unionObjBouquet.fields.size)
        assertTrue(unionObjBouquet.fields[0] is OptionalGene)
        assertTrue((unionObjBouquet.fields[0] as OptionalGene).gene is ObjectGene)
        val objFlower = (unionObjBouquet.fields[0] as OptionalGene).gene as ObjectGene
        assertTrue(objFlower.fields.any { it is BooleanGene && it.name == "id" })
        assertTrue(objFlower.fields.any { it is OptionalGene && it.name == "color" })
        /**/
        assertTrue(unionObjBouquet.fields[1] is OptionalGene)
        assertTrue((unionObjBouquet.fields[1] as OptionalGene).gene is ObjectGene)
        val objPot = (unionObjBouquet.fields[1] as OptionalGene).gene as ObjectGene

        assertTrue(objPot.fields.any { it is BooleanGene && it.name == "id" })
        assertTrue(objPot.fields.any { it is BooleanGene && it.name == "size" })
    }

    @Test
    fun unionInternalRecEg2Test() {

        val actionCluster = mutableMapOf<String, Action>()
        val json =
            GraphQLActionBuilderTest::class.java.getResource("/graphql/artificial/unionInternalRecEg2.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(1, actionCluster.size)
    }


    @Test
    fun enumInterfaceTest() {
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/artificial/enumInterface.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(1, actionCluster.size)

    }

    @Test
    fun interfaceHisTest() {
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/artificial/interfaceHis.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(1, actionCluster.size)

        val node = actionCluster["node"] as GraphQLAction
        assertEquals(1, node.parameters.size)
        assertTrue(node.parameters[0] is GQReturnParam)

        assertTrue(node.parameters[0].gene is ObjectGene)
        val interfaceObjectNode = node.parameters[0].gene as ObjectGene
        assertEquals(5, interfaceObjectNode.fields.size)

        assertTrue(interfaceObjectNode.fields[0] is OptionalGene)
        assertTrue((interfaceObjectNode.fields[0] as OptionalGene).gene is ObjectGene)
        val objAgency = (interfaceObjectNode.fields[0] as OptionalGene).gene as ObjectGene
        assertEquals(1, objAgency.fields.size)
        assertTrue(objAgency.fields.any { it is OptionalGene && it.name == "routes" })
    }


    @Test
    fun recEgTest2() {
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/artificial/recEg2.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(1, actionCluster.size)
    }

    @Test
    fun handleAllCyclesInObjectFieldsTest() {

        val objI = ObjectGene("obj2", listOf(OptionalGene("cyc", CycleObjectGene("a"), isActive = true)))

        val obj = OptionalGene("obj1", ObjectGene("obj1", listOf(objI)), isActive = true)

        assertTrue(obj.isActive)

        obj.flatView().forEach {
            if (it is ObjectGene)
                GraphQLActionBuilder.handleAllCyclesAndLimitInObjectFields(it)
        }

        assertTrue(!obj.isActive)

    }

    @Test
    fun handleAllCyclesXInObjectFieldsTest() {

        val objI = ObjectGene("obj2", listOf(OptionalGene("lim", LimitObjectGene("lim"), isActive = true)))

        val obj = OptionalGene("obj1", ObjectGene("obj1", listOf(objI)), isActive = true)

        assertTrue(obj.isActive)

        obj.flatView().forEach {
            if (it is ObjectGene)
                GraphQLActionBuilder.handleAllCyclesAndLimitInObjectFields(it)
        }

        assertTrue(!obj.isActive)

    }


    @Test
    fun depthTest() {
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/artificial/abstract2.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, max(config.treeDepth, 5))
        assertEquals(2, actionCluster.size)

        val a = actionCluster["a"] as GraphQLAction
        assertEquals(1, a.parameters.size)
        assertTrue(a.parameters[0] is GQReturnParam)
        assertTrue(a.parameters[0].gene is ObjectGene)

        val objA = a.parameters[0].gene as ObjectGene//first level
        assertEquals(3, objA.fields.size)
        assertTrue(objA.fields.any { it is BooleanGene && it.name == "id" })
        assertTrue(objA.fields.any { it is OptionalGene && it.name == "b" })// second level
        assertTrue(objA.fields.any { it is OptionalGene && it.name == "f" })// second level

        val objB = (objA.fields.first { it.name == "b" } as OptionalGene).gene as ObjectGene
        assertEquals(1, objB.fields.size)
        assertTrue(objB.fields.any { it is OptionalGene && it.name == "c" })//third level

        val objC = (objB.fields[0] as OptionalGene).gene as ObjectGene
        assertEquals(1, objC.fields.size)
        assertTrue(objC.fields.any { it is OptionalGene && it.name == "d" })//fourth level

        val objD = (objC.fields[0] as OptionalGene).gene as ObjectGene
        assertEquals(2, objD.fields.size)
        assertTrue(objD.fields.any { it is OptionalGene && it.name == "e" })//fifth level

        val objF = (objA.fields.first { it.name == "f" } as OptionalGene).gene as ObjectGene// second level
        assertEquals(1, objF.fields.size)

    }

    @Test
    fun functionInReturnedObjectsTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json =
            GraphQLActionBuilderTest::class.java.getResource("/graphql/artificial/functionInReturnedObjectsBase.json")
                .readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(2, actionCluster.size)
        val page2 = actionCluster["page2"] as GraphQLAction

        val page = actionCluster["page"] as GraphQLAction
        assertEquals(2, page.parameters.size)
        assertTrue(page.parameters[0] is GQInputParam)
        //assertTrue((page.parameters[0].gene as OptionalGene).gene is IntegerGene)
        assertTrue(page.parameters[0].gene.getWrappedGene(IntegerGene::class.java) != null)

        assertTrue(page.parameters[1] is GQReturnParam)

        assertTrue(page.parameters[1].gene is ObjectGene)
        val objPage = page.parameters[1].gene as ObjectGene

        assertEquals(8, objPage.fields.size)
        assertTrue(objPage.fields.any { it is OptionalGene && it.gene is ObjectGene && it.name == "pageInfo" })
        assertTrue(objPage.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "users" })
        assertTrue(objPage.fields.any { it is OptionalGene && it.gene is ObjectGene && it.name == "pageInfo2" })
        assertTrue(objPage.fields.any { it is OptionalGene && it.gene is ObjectGene && it.name == "pageInfo3" })
        assertTrue(objPage.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "users2" })
        assertTrue(objPage.fields.any { it is BooleanGene && it.name == "pageInfo4" })
        assertTrue(objPage.fields.any { it is OptionalGene && it.gene is ObjectGene && it.name == "pageInfo5" })
        assertTrue(objPage.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "users3" })

        val objPageInfo = (objPage.fields.first { it.name == "pageInfo" } as OptionalGene).gene as ObjectGene
        assertEquals(1, objPageInfo.fields.size)
        assertTrue(objPageInfo.fields.any { it is BooleanGene && it.name == "total" })

        val objPageInfo2 = (objPage.fields.first { it.name == "pageInfo2" } as OptionalGene).gene as ObjectGene

        assertEquals(1, objPageInfo2.fields.size)
        assertTrue(objPageInfo2.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "total2" })
        val optTotal2 = objPageInfo2.fields.first { it.name == "total2" }
        val tupleTotal2 = optTotal2.getWrappedGene(TupleGene::class.java)
        if (tupleTotal2 != null) {
            assertEquals(1, tupleTotal2.elements.size)
            //assertTrue(tupleTotal2.elements.any { it is OptionalGene && it.gene is IntegerGene && it.name == "id" })
            assertTrue(tupleTotal2.elements.any { it.getWrappedGene(IntegerGene::class.java)?.name == "id" })
        }
        val objPageInfo3 = (objPage.fields.first { it.name == "pageInfo3" } as OptionalGene).gene as ObjectGene
        assertEquals(1, objPageInfo3.fields.size)

        assertTrue(objPageInfo3.fields.any { it is OptionalGene && it.gene is ObjectGene && it.name == "total3" })

        val objTotal3 = (objPageInfo3.fields.first { it.name == "total3" } as OptionalGene).gene as ObjectGene
        assertEquals(1, objTotal3.fields.size)
        assertTrue(objTotal3.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "price" })

        val optPrice = objTotal3.fields.first { it.name == "price" }
        val tuplePrice = optPrice.getWrappedGene(TupleGene::class.java)
        if (tuplePrice != null) {
            assertEquals(1, tuplePrice.elements.size)
            //assertTrue(tuplePrice.elements.any { it is OptionalGene && it.gene is StringGene && it.name == "Name" })
            //This name is correct since it belongs to the input
            assertTrue(tuplePrice.elements.any { it.getWrappedGene(StringGene::class.java)?.name == "Name" })
        }
        /**/
        val optUsers2 = objPage.fields.first { it.name == "users2" }
        val tupleUsers2 = optUsers2.getWrappedGene(TupleGene::class.java)
        if (tupleUsers2 != null) {
            assertEquals(2, tupleUsers2.elements.size)
            //assertTrue(tupleUsers2.elements.any { it is OptionalGene && it.gene is StringGene && it.name == "Search2" })
            assertTrue(tupleUsers2.elements.any { it.getWrappedGene(StringGene::class.java)?.name == "Search2" })
            assertTrue(tupleUsers2.elements.any { it is ObjectGene && it.name == "users2" })
        }
        val objUser2 = tupleUsers2?.elements?.last() as ObjectGene
        assertEquals(1, objUser2.fields.size)
        assertTrue(objUser2.fields.any { it is OptionalGene && it.gene is ObjectGene && it.name == "about2" })
        /**/
        val objAbout2 = (objUser2.fields.first { it.name == "about2" } as OptionalGene).gene as ObjectGene
        assertEquals(1, objAbout2.fields.size)
        assertTrue(objAbout2.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "html" })

        val optHtml = objAbout2.fields.first { it.name == "html" }
        val tupleHtml = optHtml.getWrappedGene(TupleGene::class.java)
        if (tupleHtml != null) {
            assertEquals(1, tupleHtml.elements.size)
            //assertTrue(tupleHtml.elements.any { it is OptionalGene && it.gene is StringGene && it.name == "Name" })
            assertTrue(tupleHtml.elements.any { it.getWrappedGene(StringGene::class.java)?.name == "Name" })
        }
        /**/
        val objPageInfo5 = (objPage.fields.first { it.name == "pageInfo5" } as OptionalGene).gene as ObjectGene
        assertEquals(1, objPageInfo5.fields.size)
        assertTrue(objPageInfo5.fields.any { it is BooleanGene && it.name == "total4" })
        /**/
        val optUsers3 = objPage.fields.first { it.name == "users3" }
        val tupleUsers3 = optUsers3.getWrappedGene(TupleGene::class.java)
        if (tupleUsers3 != null) {
            assertEquals(3, tupleUsers3.elements.size)
            //assertTrue(tupleUsers3.elements.any { it is OptionalGene && it.gene is StringGene && it.name == "Search" })
            assertTrue(tupleUsers3.elements.any { it.getWrappedGene(StringGene::class.java)?.name == "Search" })
            assertTrue(tupleUsers3.elements.any { it is ArrayGene<*> && it.template is ObjectGene && it.name == "store" })
        }
        val objStore = (tupleUsers3?.elements?.first { it.name == "store" } as ArrayGene<*>).template as ObjectGene
        assertEquals(1, objStore.fields.size)
        assertTrue(objStore.fields.any { it is IntegerGene && it.name == "id" })

        assertTrue(tupleUsers3.elements.any { it is ObjectGene && it.name == "users3" })

        val objUser3 = tupleUsers3.elements.last() as ObjectGene
        assertEquals(1, objUser3.fields.size)
        assertTrue(objUser3.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "about3" })

        val optAbout3 = objUser3.fields.first { it.name == "about3" }
        val tupleAbout3 = optAbout3.getWrappedGene(TupleGene::class.java)
        if (tupleAbout3 != null) {
            assertEquals(1, tupleAbout3.elements.size)
            //assertTrue(tupleAbout3.elements.any { it is OptionalGene && it.gene is BooleanGene && it.name == "AsHtml2" })
            assertTrue(tupleAbout3.elements.any { it.getWrappedGene(BooleanGene::class.java)?.name == "AsHtml2" })
        }
    }

    /*
    The test underneath are for testing schemas without the boolean selection.
    It helps when investigating the structure of each component, and Gc error
     */
    @Disabled
    @Test
    fun functionInReturnedObjectsWithOutBooleanSelectionTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json =
            GraphQLActionBuilderTest::class.java.getResource("/graphql/artificial/functionInReturnedObjectsBase.json")
                .readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(2, actionCluster.size)
        val page = actionCluster.get("page") as GraphQLAction
        assertEquals(2, page.parameters.size)
        assertTrue(page.parameters[0] is GQInputParam)
        assertTrue((page.parameters[0].gene as OptionalGene).gene is IntegerGene)
        assertTrue(page.parameters[1] is GQReturnParam)

        assertTrue((page.parameters[1].gene as OptionalGene).gene is ObjectGene)
        val objPage = (page.parameters[1].gene as OptionalGene).gene as ObjectGene

        assertEquals(8, objPage.fields.size)
        assertTrue(objPage.fields.any { it is OptionalGene && it.gene is ObjectGene && it.name == "pageInfo" })
        assertTrue(objPage.fields.any { it is OptionalGene && it.gene is TupleGene && it.name == "users" })
        assertTrue(objPage.fields.any { it is OptionalGene && it.gene is ObjectGene && it.name == "pageInfo2" })
        assertTrue(objPage.fields.any { it is OptionalGene && it.gene is ObjectGene && it.name == "pageInfo3" })
        assertTrue(objPage.fields.any { it is OptionalGene && it.gene is TupleGene && it.name == "users2" })
        assertTrue(objPage.fields.any { it is OptionalGene && it.gene is StringGene && it.name == "pageInfo4" })
        assertTrue(objPage.fields.any { it is OptionalGene && it.gene is ObjectGene && it.name == "pageInfo5" })
        assertTrue(objPage.fields.any { it is OptionalGene && it.gene is TupleGene && it.name == "users3" })

        val objPageInfo = (objPage.fields.first { it.name == "pageInfo" } as OptionalGene).gene as ObjectGene
        assertEquals(1, objPageInfo.fields.size)
        assertTrue(objPageInfo.fields.any { it is OptionalGene && it.gene is IntegerGene && it.name == "total" })

        val objPageInfo2 = (objPage.fields.first { it.name == "pageInfo2" } as OptionalGene).gene as ObjectGene

        assertEquals(1, objPageInfo2.fields.size)
        assertTrue(objPageInfo2.fields.any { it is OptionalGene && it.gene is TupleGene && it.name == "total2" })
        val tupleTotal2 = (objPageInfo2.fields.first { it.name == "total2" } as OptionalGene).gene as TupleGene
        assertEquals(2, tupleTotal2.elements.size)
        assertTrue(tupleTotal2.elements.any { it is OptionalGene && it.gene is IntegerGene && it.name == "id" })
        assertTrue(tupleTotal2.elements.any { it is OptionalGene && it.gene is BooleanGene && it.name == "total2" })

        val objPageInfo3 = (objPage.fields.first { it.name == "pageInfo3" } as OptionalGene).gene as ObjectGene
        assertEquals(1, objPageInfo3.fields.size)

        assertTrue(objPageInfo3.fields.any { it is OptionalGene && it.gene is ObjectGene && it.name == "total3" })

        val objTotal3 = (objPageInfo3.fields.first { it.name == "total3" } as OptionalGene).gene as ObjectGene
        assertEquals(1, objTotal3.fields.size)
        assertTrue(objTotal3.fields.any { it is OptionalGene && it.gene is TupleGene && it.name == "price" })

        val tuplePrice = (objTotal3.fields.first { it.name == "price" } as OptionalGene).gene as TupleGene
        assertEquals(2, tuplePrice.elements.size)
        assertTrue(tuplePrice.elements.any { it is OptionalGene && it.gene is StringGene && it.name == "Name" })
        assertTrue(tuplePrice.elements.any { it is OptionalGene && it.gene is IntegerGene && it.name == "price" })
        /**/
        val tupleUsers2 = (objPage.fields.first { it.name == "users2" } as OptionalGene).gene as TupleGene
        assertEquals(2, tupleUsers2.elements.size)
        assertTrue(tupleUsers2.elements.any { it is OptionalGene && it.gene is StringGene && it.name == "Search2" })
        assertTrue(tupleUsers2.elements.any { it is OptionalGene && it.gene is ObjectGene && it.name == "users2" })

        val objUser2 = (tupleUsers2.elements.last() as OptionalGene).gene as ObjectGene
        assertEquals(1, objUser2.fields.size)
        assertTrue(objUser2.fields.any { it is OptionalGene && it.gene is ObjectGene && it.name == "about2" })
        /**/
        val objAbout2 = (objUser2.fields.first { it.name == "about2" } as OptionalGene).gene as ObjectGene
        assertEquals(1, objAbout2.fields.size)
        assertTrue(objAbout2.fields.any { it is OptionalGene && it.gene is TupleGene && it.name == "html" })

        val tupleHtml = (objAbout2.fields.first { it.name == "html" } as OptionalGene).gene as TupleGene
        assertEquals(2, tupleHtml.elements.size)
        assertTrue(tupleHtml.elements.any { it is OptionalGene && it.gene is StringGene && it.name == "Name" })
        assertTrue(tupleHtml.elements.any { it is OptionalGene && it.gene is BooleanGene && it.name == "html" })
        /**/
        val objPageInfo5 = (objPage.fields.first { it.name == "pageInfo5" } as OptionalGene).gene as ObjectGene
        assertEquals(1, objPageInfo5.fields.size)
        assertTrue(objPageInfo5.fields.any { it is OptionalGene && it.gene is EnumGene<*> && it.name == "total4" })

        val enumTotal4 = (objPageInfo5.fields.first { it.name == "total4" } as OptionalGene).gene as EnumGene<*>
        assertEquals(2, enumTotal4.values.size)
        assertTrue(enumTotal4.values.any { it == "TOTALENUM1" })
        assertTrue(enumTotal4.values.any { it == "TOTALENUM2" })
        /**/
        val tupleUsers3 = (objPage.fields.first { it.name == "users3" } as OptionalGene).gene as TupleGene
        assertEquals(3, tupleUsers3.elements.size)
        assertTrue(tupleUsers3.elements.any { it is OptionalGene && it.gene is StringGene && it.name == "Search" })
        assertTrue(tupleUsers3.elements.any { it is ArrayGene<*> && it.template is ObjectGene && it.name == "store" })
        val objStore = (tupleUsers3.elements.first { it.name == "store" } as ArrayGene<*>).template as ObjectGene
        assertEquals(1, objStore.fields.size)
        assertTrue(objStore.fields.any { it is IntegerGene && it.name == "id" })
    }


    @Test
    fun anigListSchemaTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/AniList.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(54, actionCluster.size)
        val page = actionCluster["Page"] as GraphQLAction
        assertEquals(3, page.parameters.size)
        assertTrue(page.parameters[0] is GQInputParam)
        //assertTrue((page.parameters[0].gene as OptionalGene).gene is IntegerGene)
        assertTrue(page.parameters[0].gene.getWrappedGene(IntegerGene::class.java) != null)
        //assertTrue((page.parameters[1].gene as OptionalGene).gene is IntegerGene)
        assertTrue(page.parameters[1].gene.getWrappedGene(IntegerGene::class.java) != null)

        assertTrue(page.parameters[1] is GQInputParam)
        assertTrue(page.parameters[2] is GQReturnParam)

        /**/
        //primitive type that is not part of the search
        //query will look like: {GenreCollection}
        val genreCollection = actionCluster["GenreCollection"] as GraphQLAction

        val mediaTagCollection = actionCluster["MediaTagCollection"] as GraphQLAction
        assertTrue(mediaTagCollection.parameters[1].gene is ObjectGene)
        /**/
        val objPage = page.parameters[2].gene as ObjectGene
        assertTrue(objPage.fields[0] is OptionalGene)
        val objPageInfo = (objPage.fields[0] as OptionalGene).gene as ObjectGene
        objPageInfo.fields.any { it is BooleanGene && it.name == "Total" }
        assertTrue(objPageInfo.fields[0] is BooleanGene)

        assertTrue(objPage.fields.any {
            it.getWrappedGene(TupleGene::class.java)?.name == "activities" &&
                    it.getWrappedGene(TupleGene::class.java)?.lastElementTreatedSpecially == true })//nbr12

        val optActivities = objPage.fields.first { it.getWrappedGene(TupleGene::class.java)?.name == "activities" }

        val tupleActivities = optActivities.getWrappedGene(TupleGene::class.java)
        if (tupleActivities != null) {
            assertTrue(tupleActivities.elements.any { it.getWrappedGene(ObjectGene::class.java)?.name == "activities#UNION#" })
        }

        val unionObjectActivities = tupleActivities?.elements?.last() as ObjectGene

        assertEquals(3, unionObjectActivities.fields.size)

        assertTrue(unionObjectActivities.fields.any { it.getWrappedGene(ObjectGene::class.java)?.name == "MessageActivity" })
        val objMessageActivity2 = (unionObjectActivities.fields[2] as OptionalGene).gene as ObjectGene

        assertTrue(objMessageActivity2.fields.any { it.getWrappedGene(ObjectGene::class.java)?.name == "messenger" })
        val objUser3 = (objMessageActivity2.fields[14] as OptionalGene).gene as ObjectGene
        assertTrue(objUser3.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "favourites" })
        val optFavourites3 = objUser3.fields.first { it.getWrappedGene(TupleGene::class.java)?.name == "favourites" }
        val tupleFavourites3 = optFavourites3.getWrappedGene(TupleGene::class.java)
        if (tupleFavourites3 != null) {
            assertEquals(2, tupleFavourites3.elements.size)
            assertTrue(tupleFavourites3.elements.any { it.getWrappedGene(ObjectGene::class.java)?.name == "favourites" })
        }
        val objFavorites3 = tupleFavourites3?.elements?.last() as ObjectGene
        assertEquals(5, objFavorites3.fields.size)

        assertTrue(objFavorites3.fields.any {
            it.getWrappedGene(TupleGene::class.java)?.name == "anime" &&
                    it.getWrappedGene(TupleGene::class.java)?.elements?.size == 3 &&
                    it.getWrappedGene(TupleGene::class.java)?.lastElementTreatedSpecially == true })
        assertTrue(objFavorites3.fields.any {
            it.getWrappedGene(TupleGene::class.java)?.name == "manga" &&
                    it.getWrappedGene(TupleGene::class.java)?.elements?.size == 3 &&
                    it.getWrappedGene(TupleGene::class.java)?.lastElementTreatedSpecially == true })
        assertTrue(objFavorites3.fields.any {
            it.getWrappedGene(TupleGene::class.java)?.name == "characters" &&
                    it.getWrappedGene(TupleGene::class.java)?.elements?.size == 3 &&
                    it.getWrappedGene(TupleGene::class.java)?.lastElementTreatedSpecially == true })
        assertTrue(objFavorites3.fields.any {
            it.getWrappedGene(TupleGene::class.java)?.name == "staff" &&
                    it.getWrappedGene(TupleGene::class.java)?.elements?.size == 3 &&
                    it.getWrappedGene(TupleGene::class.java)?.lastElementTreatedSpecially == true })
        assertTrue(objFavorites3.fields.any {
            it.getWrappedGene(TupleGene::class.java)?.name == "studios" &&
                    it.getWrappedGene(TupleGene::class.java)?.elements?.size == 3 &&
                    it.getWrappedGene(TupleGene::class.java)?.lastElementTreatedSpecially == true })

        /**/
        val media = actionCluster["Media"] as GraphQLAction
        assertEquals(67, media.parameters.size)
        //assertTrue((media.parameters[6].gene as OptionalGene).gene is EnumGene<*>)
        assertTrue(media.parameters[6].gene.getWrappedGene(EnumGene::class.java) != null)

        val objMedia = media.parameters[66].gene as ObjectGene
        assertTrue(objMedia.fields.any { it is BooleanGene && it.name == "type" })
        /**/
        val notification = actionCluster["Notification"] as GraphQLAction
        assertEquals(4, notification.parameters.size)
        assertTrue(notification.parameters[0] is GQInputParam)
        assertTrue(notification.parameters[3] is GQReturnParam)

        assertTrue(notification.parameters[3].gene is ObjectGene)
        val unionObjectsNotificationUnion = notification.parameters[3].gene as ObjectGene
        assertEquals(14, unionObjectsNotificationUnion.fields.size)

        assertTrue(unionObjectsNotificationUnion.fields[0] is OptionalGene)
        assertTrue((unionObjectsNotificationUnion.fields[0] as OptionalGene).gene is ObjectGene)
        val objAiringNotification = (unionObjectsNotificationUnion.fields[0] as OptionalGene).gene as ObjectGene
        assertEquals(7, objAiringNotification.fields.size)
        assertTrue(objAiringNotification.fields.any { it is BooleanGene && it.name == "id" })
        assertTrue(objAiringNotification.fields.any { it is OptionalGene && it.name == "media" })

        val objMediaa = (objAiringNotification.fields.first { it.name == "media" } as OptionalGene).gene as ObjectGene
        assertEquals(53, objMediaa.fields.size)
        assertTrue(objMediaa.fields.any { it is BooleanGene && it.name == "id" })
        assertTrue(objMediaa.fields.any { it is BooleanGene && it.name == "modNotes" })

        /**/
        val saveMessageActivity = actionCluster["SaveMessageActivity"] as GraphQLAction
        assertTrue(saveMessageActivity.parameters[6].gene is ObjectGene)
        val objMessageActivity = saveMessageActivity.parameters[6].gene as ObjectGene
        assertTrue(objMessageActivity.fields.any { it.getWrappedGene(ObjectGene::class.java)?.name == "recipient" })
        val objUser2 = (objMessageActivity.fields[13] as OptionalGene).gene as ObjectGene
        assertTrue(objUser2.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "favourites" })
        val optFavourites2 = objUser2.fields.first { it.getWrappedGene(TupleGene::class.java)?.name == "favourites" }
        val tupleFavourites2 = optFavourites2.getWrappedGene(TupleGene::class.java)
        if (tupleFavourites2 != null) {
            assertEquals(2, tupleFavourites2.elements.size)
            assertTrue(tupleFavourites2.elements.any { it.getWrappedGene(ObjectGene::class.java)?.name == "favourites" })
        }
        val objFavorites = tupleFavourites2?.elements?.last() as ObjectGene
        assertEquals(5, objFavorites.fields.size)
        assertTrue(objFavorites.fields.any {
            it.getWrappedGene(TupleGene::class.java)?.name == "anime" &&
                    it.getWrappedGene(TupleGene::class.java)?.elements?.size == 3 &&
                    it.getWrappedGene(TupleGene::class.java)?.lastElementTreatedSpecially == true })
        assertTrue(objFavorites.fields.any {
            it.getWrappedGene(TupleGene::class.java)?.name == "manga" &&
                    it.getWrappedGene(TupleGene::class.java)?.elements?.size == 3 &&
                    it.getWrappedGene(TupleGene::class.java)?.lastElementTreatedSpecially == true })
        assertTrue(objFavorites.fields.any {
            it.getWrappedGene(TupleGene::class.java)?.name == "characters" &&
                    it.getWrappedGene(TupleGene::class.java)?.elements?.size == 3 &&
                    it.getWrappedGene(TupleGene::class.java)?.lastElementTreatedSpecially == true })
        assertTrue(objFavorites.fields.any {
            it.getWrappedGene(TupleGene::class.java)?.name == "staff" &&
                    it.getWrappedGene(TupleGene::class.java)?.elements?.size == 3 &&
                    it.getWrappedGene(TupleGene::class.java)?.lastElementTreatedSpecially == true })
        assertTrue(objFavorites.fields.any {
            it.getWrappedGene(TupleGene::class.java)?.name == "studios" &&
                    it.getWrappedGene(TupleGene::class.java)?.elements?.size == 3 &&
                    it.getWrappedGene(TupleGene::class.java)?.lastElementTreatedSpecially == true })
        /**/
        val following = actionCluster["Following"] as GraphQLAction
        assertEquals(3, following.parameters.size)
        assertTrue(following.parameters[1] is GQInputParam)
        assertTrue(following.parameters[1].gene.getWrappedGene(ArrayGene::class.java) != null)
        assertTrue(following.parameters[1].gene.getWrappedGene(ArrayGene::class.java)?.template?.getWrappedGene(EnumGene::class.java) != null)
        /**/
        val review = actionCluster["Review"] as GraphQLAction
        assertEquals(6, review.parameters.size)
        assertTrue(review.parameters[3] is GQInputParam)
        assertTrue(review.parameters[3].gene.getWrappedGene(EnumGene::class.java) != null)
        /**/
        val  activityReply = actionCluster["ActivityReply"] as GraphQLAction
        assertEquals(3, activityReply.parameters.size)
        assertTrue(activityReply.parameters[2] is GQReturnParam)
        val objActivityReply = activityReply.parameters[2].gene as ObjectGene
        assertTrue(objActivityReply.fields[7] is OptionalGene)//user
        val objUser4 = (objActivityReply.fields[7] as OptionalGene).gene as ObjectGene
        objUser4.fields.any { it is ObjectGene && it.name == "statistics" }
        assertTrue(objUser4.fields.any { it.getWrappedGene(ObjectGene::class.java)?.name == "statistics" })
        val objStatistics = (objUser4.fields[12] as OptionalGene).gene as ObjectGene
        objStatistics.fields.any { it is ObjectGene && it.name == "anime" }
        val objAnime = (objStatistics.fields[0] as OptionalGene).gene as ObjectGene

        assertTrue(objAnime.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "scores" })
        val optTupleScores = objAnime.fields.first { it.getWrappedGene(TupleGene::class.java)?.name == "scores"}
        val tupleScores = optTupleScores.getWrappedGene(TupleGene::class.java)
        if (tupleScores != null) {assertEquals(3, tupleScores.elements.size)}

        assertTrue(objAnime.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "lengths" })
        val optTupleLengths = objAnime.fields.first { it.getWrappedGene(TupleGene::class.java)?.name == "lengths"}
        val tupleLengths = optTupleLengths.getWrappedGene(TupleGene::class.java)
        if (tupleLengths != null) {assertEquals(3, tupleLengths.elements.size)}
        /**/
        val  siteStatistics = actionCluster["SiteStatistics"] as GraphQLAction
        assertEquals(1, siteStatistics.parameters.size)
        assertTrue(siteStatistics.parameters[0] is GQReturnParam)
        val objSiteStatistics = siteStatistics.parameters[0].gene as ObjectGene
        assertEquals(7,objSiteStatistics.fields.size)
    }

    @Test
    fun anigListSchemaV2Test() {
        // An updated schema of anigList
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/AniListV2.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)
        assertEquals(56, actionCluster.size)
        val  activityReply = actionCluster["ActivityReply"] as GraphQLAction
        assertEquals(3, activityReply.parameters.size)
        assertTrue(activityReply.parameters[2] is GQReturnParam)
        val objActivityReply = activityReply.parameters[2].gene as ObjectGene
        assertTrue(objActivityReply.fields[7] is OptionalGene)//user
        val objUser4 = (objActivityReply.fields[7] as OptionalGene).gene as ObjectGene
        assertTrue(objUser4.fields.any { it.getWrappedGene(BooleanGene::class.java)?.name == "moderatorRoles" })

    }


    @Test
    fun arrayEnumInputTest() {
        val actionCluster = mutableMapOf<String, Action>()
        val json =
            GraphQLActionBuilderTest::class.java.getResource("/graphql/artificial/arrayEnumInput.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(1, actionCluster.size)

        val flowersByType = actionCluster["flowersByType"] as GraphQLAction
        assertEquals(2, flowersByType.parameters.size)
        assertTrue(flowersByType.parameters[0] is GQInputParam)
        assertTrue(flowersByType.parameters[1] is GQReturnParam)

        assertTrue(flowersByType.parameters[0].gene.getWrappedGene(ArrayGene::class.java) != null)
        assertTrue(flowersByType.parameters[0].gene.getWrappedGene(ArrayGene::class.java)?.template?.
        getWrappedGene(EnumGene::class.java) != null)

    }

    @Test
    fun gitLabSchemaTest() {
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/GitLab.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(169, actionCluster.size)

        val jiraImportUsers = actionCluster["jiraImportUsers"] as GraphQLAction
        assertEquals(2, jiraImportUsers.parameters.size)
        assertTrue(jiraImportUsers.parameters[0] is GQInputParam)
        assertTrue(jiraImportUsers.parameters[1] is GQReturnParam)

        assertTrue(jiraImportUsers.parameters[1].gene is ObjectGene)
        val objJiraImportUsersPayload = jiraImportUsers.parameters[1].gene as ObjectGene
        assertEquals(3, objJiraImportUsersPayload.fields.size)
        assertTrue(objJiraImportUsersPayload.fields.any { it is BooleanGene && it.name == "clientMutationId" })
        assertTrue(objJiraImportUsersPayload.fields.any { it is BooleanGene && it.name == "errors" })

    }


    @Test
    fun universeSchemaTest() {
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/Universe.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(87, actionCluster.size)
    }

    @Test
    fun historyInFunctionInReturnedObject() {

        val actionCluster = mutableMapOf<String, Action>()
        val json =
            GraphQLActionBuilderTest::class.java.getResource("/graphql/artificial/HistoryInFunctionInReturnedObject.json")
                .readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(2, actionCluster.size)

        val page = actionCluster["page"] as GraphQLAction
        assertEquals(2, page.parameters.size)
        assertTrue(page.parameters[0] is GQInputParam)
        // assertTrue((page.parameters[0].gene as OptionalGene).gene is IntegerGene)
        assertTrue(page.parameters[0].gene.getWrappedGene(IntegerGene::class.java) != null)
        assertTrue(page.parameters[1] is GQReturnParam)

        assertTrue(page.parameters[1].gene is ObjectGene)
        val objPage = page.parameters[1].gene as ObjectGene

        assertEquals(1, objPage.fields.size)
        assertTrue(objPage.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "users" })

        val optUsers = objPage.fields.first {  it.getWrappedGene(TupleGene::class.java)?.name == "users"}
        val tupleUsers = optUsers.getWrappedGene(TupleGene::class.java)

        if (tupleUsers != null) {
            assertEquals(2, tupleUsers.elements.size)
            //assertTrue(tupleUsers.elements.any { it is OptionalGene && it.gene is StringGene && it.name == "Search" })
            assertTrue(tupleUsers.elements.any { it.getWrappedGene(StringGene::class.java)?.name == "Search" })
        }
        val objUser = tupleUsers?.elements?.last()  as ObjectGene
        assertEquals(1, objUser.fields.size)
        assertTrue(objUser.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "about" })

        val optAbout = objUser.fields.first { it.name == "about" }
        val tupleAbout = optAbout.getWrappedGene(TupleGene::class.java)
        if (tupleAbout != null) {
            assertEquals(1, tupleAbout.elements.size)
            //assertTrue(tupleAbout.elements.any { it is OptionalGene && it.gene is BooleanGene && it.name == "AsHtml" })
            assertTrue(tupleAbout.elements.any { it.getWrappedGene(BooleanGene::class.java)?.name == "AsHtml" })
        }
        /**/
        val pageInfo = actionCluster["pageInfo"] as GraphQLAction
        assertEquals(1, pageInfo.parameters.size)
        assertTrue(pageInfo.parameters[0] is GQReturnParam)

        assertTrue(pageInfo.parameters[0].gene is ObjectGene)
        val objPageInfo = pageInfo.parameters[0].gene as ObjectGene

        assertEquals(2, objPageInfo.fields.size)
        assertTrue(objPageInfo.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "total" })

        val optTotal = objPageInfo.fields.first { it.name == "total" }
        val tupleTotal = optTotal.getWrappedGene(TupleGene::class.java)

        if (tupleTotal != null) {
            assertEquals(2, tupleTotal.elements.size)
            //assertTrue(tupleTotal.elements.any { it is OptionalGene && it.gene is IntegerGene && it.name == "id" })
            assertTrue(tupleTotal.elements.any { it.getWrappedGene(IntegerGene::class.java)?.name == "id" })
            assertTrue(tupleTotal.elements.last()  is ObjectGene)
        }
        /**/
        assertTrue(objPageInfo.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "total2" })
    }

    @Test
    fun timbuctooSchemaTest() {
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/Timbuctoo.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(19, actionCluster.size)

        val aboutMe = actionCluster["aboutMe"] as GraphQLAction
        assertEquals(1, aboutMe.parameters.size)
        assertTrue(aboutMe.parameters[0] is GQReturnParam)

        assertTrue(aboutMe.parameters[0].gene is ObjectGene)
        val objAboutMe = aboutMe.parameters[0].gene as ObjectGene

        assertEquals(6, objAboutMe.fields.size)
        assertTrue(objAboutMe.fields.any { it is OptionalGene && it.gene is ObjectGene && it.name == "dataSets" })
        assertTrue(objAboutMe.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "dataSetMetadataList" })

        val optDataSetMetadataList = objAboutMe.fields.first { it.name == "dataSetMetadataList" }

        val tupleDataSetMetadataList = optDataSetMetadataList.getWrappedGene(TupleGene::class.java)
        assertEquals(3, tupleDataSetMetadataList?.elements?.size)
        assertTrue(tupleDataSetMetadataList?.elements?.last() !is CycleObjectGene)
    }

    //@Disabled("this gives lot of GC issues")
    @Test
    fun zoraTest() {
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/Zora.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(85, actionCluster.size)
    }

    @Test
    fun faunaTest() {
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/Fauna.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(13, actionCluster.size)
    }

    @Test
    fun rootNameTest() {
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/artificial/RootNames.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(2, actionCluster.size)
    }

    @Test
    fun primitivesTest() {
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/artificial/Primitives.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(2, actionCluster.size)

        val flowers = actionCluster["flowers"] as GraphQLAction

        val stores = actionCluster["stores"] as GraphQLAction
        assertEquals(1, stores.parameters.size)
        assertTrue(stores.parameters[0] is GQReturnParam)

        assertTrue(stores.parameters[0].gene is ObjectGene)
        val objStore = stores.parameters[0].gene as ObjectGene
        assertEquals(2, objStore.fields.size)
        assertTrue(objStore.fields.any { it is BooleanGene && it.name == "id" })
        assertTrue(objStore.fields.any { it is BooleanGene && it.name == "bouquets" })

    }

    @Disabled
    @Test
    fun gitHubTest() {
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/GitHub.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(204, actionCluster.size)
    }

    @Test
    fun barcelonaUrbanMobilityTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json =
            GraphQLActionBuilderTest::class.java.getResource("/graphql/online/barcelonaUrbanMobility.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(10, actionCluster.size)
    }

    @Test
    fun buildkiteTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/buildkite.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(70, actionCluster.size)

        val teamMemberCreate = actionCluster["teamMemberCreate"] as GraphQLAction
        assertEquals(2, teamMemberCreate.parameters.size)
        assertTrue(teamMemberCreate.parameters[1] is GQReturnParam)

        assertTrue(teamMemberCreate.parameters[1].gene is ObjectGene)
        val objTeamMemberCreate = teamMemberCreate.parameters[1].gene as ObjectGene

        assertEquals(3, objTeamMemberCreate.fields.size)
        assertTrue(objTeamMemberCreate.fields.any { it is OptionalGene && it.gene is ObjectGene && it.name == "team" })

        val objTeam = (objTeamMemberCreate.fields.first { it.name == "team" } as OptionalGene).gene as ObjectGene
        assertEquals(17, objTeam.fields.size)
        assertTrue(objTeam.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "members" })

        val optMembers = objTeam.fields.first { it.getWrappedGene(TupleGene::class.java)?.name == "members" }

        val tupleMembers = optMembers.getWrappedGene(TupleGene::class.java)
        assertEquals(8, tupleMembers?.elements?.size)
        /**/
        val pipelineSchedule = actionCluster["pipelineSchedule"] as GraphQLAction
        assertEquals(2, pipelineSchedule.parameters.size)
        assertTrue(pipelineSchedule.parameters[1] is GQReturnParam)
        /**/

        val organizationInvitationCreate = actionCluster["organizationInvitationCreate"] as GraphQLAction
        assertEquals(2, organizationInvitationCreate.parameters.size)
        assertTrue(organizationInvitationCreate.parameters[0] is GQInputParam)

        assertTrue(organizationInvitationCreate.parameters[0].name == "input")
        assertTrue((organizationInvitationCreate.parameters[0].gene.getWrappedGene(ObjectGene::class.java) != null))
        val objOrganizationInvitationCreate = organizationInvitationCreate.parameters[0].gene.getWrappedGene(ObjectGene::class.java)
        if ( objOrganizationInvitationCreate != null) {

           assertTrue( objOrganizationInvitationCreate.fields.any { it.getWrappedGene(ArrayGene::class.java)?.name == "emails" }) }


    }

    @Test
    fun buildkiteFragmentTest() {
        //This fragment is used for the issues with inputs
        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/artificial/buildkite(fragment).json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(1, actionCluster.size)
        val organizationInvitationCreate = actionCluster["organizationInvitationCreate"] as GraphQLAction
        assertEquals(2, organizationInvitationCreate.parameters.size)
        assertTrue(organizationInvitationCreate.parameters[0] is GQInputParam)

        assertTrue(organizationInvitationCreate.parameters[0].name == "input")
        assertTrue((organizationInvitationCreate.parameters[0].gene.getWrappedGene(ObjectGene::class.java) != null))
        val objOrganizationInvitationCreate =
            organizationInvitationCreate.parameters[0].gene.getWrappedGene(ObjectGene::class.java)
        if (objOrganizationInvitationCreate != null) {

            assertTrue(objOrganizationInvitationCreate.fields.any { it.getWrappedGene(ArrayGene::class.java)?.name == "emails" })
            assertTrue(objOrganizationInvitationCreate.fields.any { it.getWrappedGene(ObjectGene::class.java)?.name == "sso" })
            assertTrue(objOrganizationInvitationCreate.fields.any { it.getWrappedGene(ArrayGene::class.java)?.name == "teams" })
        }


    }

    @Test
    fun camaraDeputadosTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/camaraDeputados.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(33, actionCluster.size)
    }

    @Test
    fun demotivationQuotesTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json =
            GraphQLActionBuilderTest::class.java.getResource("/graphql/online/demotivationQuotes.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(2, actionCluster.size)
    }

    @Test
    fun directionsTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/directions.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(6, actionCluster.size)
    }

    @Test
    fun fruitsTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/fruits.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(7, actionCluster.size)
    }

    @Test
    fun mockiTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/mocki.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(4, actionCluster.size)
    }

    @Test
    fun musicBrainzTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/musicBrainz.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(6, actionCluster.size)
    }

    @Test
    fun pokemonTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/pokemon.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(13, actionCluster.size)
    }

    @Test
    fun rickandmortyapiTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/rickandmortyapi.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(9, actionCluster.size)
    }

    @Test
    fun spotifyTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/spotify.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(11, actionCluster.size)
    }

    @Test
    fun swapiTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/swapi.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(13, actionCluster.size)
    }

    @Test
    fun swopTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/swop.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(6, actionCluster.size)
    }

    @Test
    fun weatherTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/weather.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(2, actionCluster.size)
    }

    @Test
    fun composeTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/compose.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(9, actionCluster.size)
    }

    @Test
    fun allLimitReachedTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json =
            GraphQLActionBuilderTest::class.java.getResource("/graphql/artificial/allLimitReached.json").readText()

        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, 2)

        assertEquals(1, actionCluster.size)
    }

    @Test
    fun allLimitNotReachedTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json =
            GraphQLActionBuilderTest::class.java.getResource("/graphql/artificial/allLimitNotReached.json").readText()

        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, 2)

        assertEquals(2, actionCluster.size)
    }


    @Test
    fun stratzTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json = GraphQLActionBuilderTest::class.java.getResource("/graphql/online/stratz.json").readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(19, actionCluster.size)

        val live = actionCluster["live"] as GraphQLAction
        assertEquals(1, live.parameters.size)
        assertTrue(live.parameters[0] is GQReturnParam)
        assertTrue(live.parameters[0].gene is ObjectGene)

        val objLive = live.parameters[0].gene as ObjectGene
        assertTrue(objLive.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "matches" })

        val optMatches = objLive.fields.first { it.name == "matches" }

        val tupleMatches = optMatches.getWrappedGene(TupleGene::class.java)
        if (tupleMatches != null) {
            assertEquals(2, tupleMatches.elements.size)
            assertTrue(tupleMatches.elements.any { it is ObjectGene && it.name == "matches" })
        }

    }

    @Test
    fun fieldWithDifferentArgumentTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json =
            GraphQLActionBuilderTest::class.java.getResource("/graphql/artificial/fieldWithDifferentArgument.json")
                .readText()

        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(2, actionCluster.size)


        val entryCollection = actionCluster["entryCollection"] as GraphQLAction
        assertEquals(3, entryCollection.parameters.size)
        assertTrue(entryCollection.parameters[0] is GQInputParam)
        //assertTrue((entryCollection.parameters[0].gene as OptionalGene).gene.name == "skip" )
        assertTrue(entryCollection.parameters[0].gene.getWrappedGene(OptionalGene::class.java)?.gene?.name == "skip")

        //assertTrue((entryCollection.parameters[0].gene as OptionalGene).gene is IntegerGene)
        assertTrue(entryCollection.parameters[0].gene.getWrappedGene(IntegerGene::class.java) != null)

        assertTrue(entryCollection.parameters[1] is GQInputParam)
        //assertTrue((entryCollection.parameters[1].gene as OptionalGene).gene is BooleanGene)
        assertTrue(entryCollection.parameters[1].gene.getWrappedGene(BooleanGene::class.java) != null)
        //assertTrue((entryCollection.parameters[1].gene as OptionalGene).gene.name == "preview")
        assertTrue(entryCollection.parameters[1].gene.getWrappedGene(OptionalGene::class.java)?.gene?.name == "preview")

        assertTrue(entryCollection.parameters[2] is GQReturnParam)
        assertTrue(entryCollection.parameters[2].gene is ObjectGene)

        val objEntryCollection = entryCollection.parameters[2].gene as ObjectGene
        assertTrue(objEntryCollection.fields.any { it is BooleanGene && it.name == "total" })

        /**/
        val lessonCodeSnippets = actionCluster["lessonCodeSnippets"] as GraphQLAction
        assertEquals(1, lessonCodeSnippets.parameters.size)
        assertTrue(lessonCodeSnippets.parameters[0] is GQReturnParam)
        assertTrue(lessonCodeSnippets.parameters[0].gene is ObjectGene)

        val objLessonCodeSnippets = lessonCodeSnippets.parameters[0].gene as ObjectGene
        assertTrue(objLessonCodeSnippets.fields.any { it.getWrappedGene(TupleGene::class.java)?.name == "entryCollection" })

        val optEntryCollection = objLessonCodeSnippets.fields.first { it.name == "entryCollection" }

        val tupleEntryCollection = optEntryCollection.getWrappedGene(TupleGene::class.java)
        if (tupleEntryCollection != null) {
            assertEquals(2, tupleEntryCollection.elements.size) //should not fail
            //assertTrue(tupleEntryCollection.elements.any { it is OptionalGene && it.name == "skip" })
            assertTrue( tupleEntryCollection.elements.any { it.getWrappedGene(OptionalGene::class.java)?.gene?.name == "skip" })
            assertTrue(tupleEntryCollection.elements.any { it is ObjectGene && it.name == "entryCollection" })
        }

    }


    @Test
    fun nullableInputTest() {

        val actionCluster = mutableMapOf<String, Action>()
        // The .graphqls schema is from: e2e-tests/spring-graphql/src/main/resources/nullable.array.graphqls
        val json =
            GraphQLActionBuilderTest::class.java.getResource("/graphql/artificial/nullableInput.json").readText()
        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(4, actionCluster.size)

        val flowersNullInNullOut = actionCluster["flowersNullInNullOut"] as GraphQLAction
        assertEquals(2, flowersNullInNullOut.parameters.size)
        assertTrue(flowersNullInNullOut.parameters[0] is GQInputParam)
        assertTrue(((flowersNullInNullOut.parameters[0].gene as OptionalGene).gene as NullableGene).gene is ArrayGene<*>)
        val arrayNullInNullOut =
            ((flowersNullInNullOut.parameters[0].gene as OptionalGene).gene as NullableGene).gene as ArrayGene<*>
        assertTrue(((arrayNullInNullOut.template as OptionalGene).gene as NullableGene).gene is IntegerGene)

        /**/
        val flowersNullIn = actionCluster["flowersNullIn"] as GraphQLAction
        assertEquals(2, flowersNullIn.parameters.size)
        assertTrue(flowersNullIn.parameters[0] is GQInputParam)
        assertTrue(flowersNullIn.parameters[0].gene is ArrayGene<*>)
        assertTrue((((flowersNullIn.parameters[0].gene as ArrayGene<*>).template as OptionalGene).gene as NullableGene).gene is IntegerGene)

        /**/
        val flowersNullOut = actionCluster["flowersNullOut"] as GraphQLAction
        assertEquals(2, flowersNullOut.parameters.size)
        assertTrue(flowersNullOut.parameters[0] is GQInputParam)
        assertTrue(((flowersNullOut.parameters[0].gene as OptionalGene).gene as NullableGene).gene is ArrayGene<*>)
        val arrayNullOut =
            ((flowersNullOut.parameters[0].gene as OptionalGene).gene as NullableGene).gene as ArrayGene<*>
        assertTrue(arrayNullOut.template is IntegerGene)

        /**/
        val flowersNotNullInOut = actionCluster["flowersNotNullInOut"] as GraphQLAction
        assertEquals(2, flowersNotNullInOut.parameters.size)
        assertTrue((flowersNotNullInOut.parameters[0].gene as ArrayGene<*>).template is IntegerGene)

    }

    @Test
    fun interfacesObjectsTest() {

        val actionCluster = mutableMapOf<String, Action>()
        val json =
            GraphQLActionBuilderTest::class.java.getResource("/graphql/artificial/interfacesObjects.json").readText()
        val config = EMConfig()
        GraphQLActionBuilder.addActionsFromSchema(json, actionCluster, config.treeDepth)

        assertEquals(1, actionCluster.size)

        val stores = actionCluster["stores"] as GraphQLAction
        assertEquals(1, stores.parameters.size)
        assertTrue(stores.parameters[0] is GQReturnParam)

        assertTrue(stores.parameters[0].gene is ObjectGene)
        val objectStore1 = stores.parameters[0].gene as ObjectGene
        assertEquals(1, objectStore1.fields.size)

        assertTrue(objectStore1.fields[0] is OptionalGene)
        assertTrue((objectStore1.fields[0] as OptionalGene).gene is ObjectGene)
        val interfaceBouquet = (objectStore1.fields[0] as OptionalGene).gene as ObjectGene
        assertEquals(2, interfaceBouquet.fields.size)

        assertTrue(interfaceBouquet.fields[0] is OptionalGene)
        assertTrue((interfaceBouquet.fields[0] as OptionalGene).gene is ObjectGene)
        val objPotStore = (interfaceBouquet.fields[0] as OptionalGene).gene as ObjectGene
        assertEquals(1, objPotStore.fields.size)
        assertTrue(objPotStore.fields.any { it.getWrappedGene(ObjectGene::class.java) != null })//interface: address

        val interfaceAddress =
            (objPotStore.fields.first { it.getWrappedGene(ObjectGene::class.java)?.name == "address#INTERFACE#" } as OptionalGene).gene as ObjectGene
        assertEquals(3, interfaceAddress.fields.size)

        assertTrue(interfaceAddress.fields[0] is OptionalGene)
        assertTrue((interfaceAddress.fields[0] as OptionalGene).gene is ObjectGene)
        val objAddressFlower = (interfaceAddress.fields[0] as OptionalGene).gene as ObjectGene
        assertEquals(1, objAddressFlower.fields.size)

        assertTrue(interfaceAddress.fields[0] is OptionalGene)
        assertTrue((interfaceAddress.fields[0] as OptionalGene).gene is ObjectGene)
        val objAddressStore = (interfaceAddress.fields[0] as OptionalGene).gene as ObjectGene
        assertEquals(1, objAddressStore.fields.size)
        /**/
        assertTrue(interfaceBouquet.fields[1] is OptionalGene)
        assertTrue((interfaceBouquet.fields[1] as OptionalGene).gene is ObjectGene)
        val objStore = (interfaceBouquet.fields[1] as OptionalGene).gene as ObjectGene
        assertEquals(2, objStore.fields.size)
        assertTrue(objStore.fields.any { it is BooleanGene && it.name == "id" })
        assertTrue(objStore.fields.any { it is BooleanGene && it.name == "name" })

    }
}