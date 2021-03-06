package org.evomaster.core.search.gene

import org.evomaster.core.database.DbActionGeneBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.util.*

internal class GeneUtilsTest {

    @Test
    fun testPadding() {

        val x = 9
        val res = GeneUtils.padded(x, 2)

        assertEquals("09", res)
    }


    @Test
    fun testPaddingNegative() {

        val x = -2
        val res = GeneUtils.padded(x, 3)

        assertEquals("-02", res)

    }

    @Test
    fun testRepairDefaultDateGene() {
        val gene = DateGene("date")
        GeneUtils.repairGenes(listOf(gene))
        GregorianCalendar(gene.year.value, gene.month.value, gene.day.value, 0, 0)
    }

    @Test
    fun testRepairBrokenDateGene() {
        val gene = DateGene("date", IntegerGene("year", 1998), IntegerGene("month", 4), IntegerGene("day", 31))
        GeneUtils.repairGenes(gene.flatView())
        GregorianCalendar(gene.year.value, gene.month.value, gene.day.value, 0, 0)
        assertEquals(1998, gene.year.value)
        assertEquals(4, gene.month.value)
        assertEquals(30, gene.day.value)
    }

    @Test
    fun testRepairBrokenSqlTimestampGene() {
        val sqlTimestampGene = DbActionGeneBuilder().buildSqlTimestampGene("timestamp")
        sqlTimestampGene.date.apply {
            year.value = 1998
            month.value = 4
            day.value = 31
        }
        sqlTimestampGene.time.apply {
            hour.value = 23
            minute.value = 13
            second.value = 41
        }

        GeneUtils.repairGenes(sqlTimestampGene.flatView())
        sqlTimestampGene.apply {
            GregorianCalendar(this.date.year.value, this.date.month.value, this.date.day.value, this.time.hour.value, this.time.minute.value)
        }
        sqlTimestampGene.date.apply {
            assertEquals(1998, this.year.value)
            assertEquals(4, this.month.value)
            assertEquals(30, this.day.value)
        }
        sqlTimestampGene.time.apply {
            assertEquals(23, this.hour.value)
            assertEquals(13, this.minute.value)
            assertEquals(41, this.second.value)
        }
    }

    @Test
    fun testFlatViewWithExcludeDateGene() {
        val sqlTimestampGene = DbActionGeneBuilder().buildSqlTimestampGene("timestamp")
        sqlTimestampGene.date.apply {
            year.value = 1998
            month.value = 4
            day.value = 31
        }
        sqlTimestampGene.time.apply {
            hour.value = 23
            minute.value = 13
            second.value = 41
        }
        val excludePredicate = { gene: Gene -> (gene is DateGene) }
        sqlTimestampGene.flatView(excludePredicate).apply {
            assertFalse(contains(sqlTimestampGene.date.year))
            assertFalse(contains(sqlTimestampGene.date.month))
            assertFalse(contains(sqlTimestampGene.date.day))
            assert(contains(sqlTimestampGene.date))
            assert(contains(sqlTimestampGene.time))
            assert(contains(sqlTimestampGene.time.hour))
            assert(contains(sqlTimestampGene.time.minute))
            assert(contains(sqlTimestampGene.time.second))
        }
    }


    @Test
    fun testBooleanSelectionSimple() {

        val obj = ObjectGene("foo", listOf(StringGene("a", "hello"), IntegerGene("b", 42)))

        val selection = GeneUtils.getBooleanSelection(obj)

        val rep = selection.getValueAsPrintableString(mode = GeneUtils.EscapeMode.BOOLEAN_SELECTION_MODE)
                .replace(" ", "") // remove empty space to make assertion less brittle

        //without randomization, should be both on by default
        assertEquals("foo{a,b}", rep)
    }

    @Test
    fun testBooleanSectionSkip() {

        val obj = ObjectGene("foo", listOf(StringGene("a", "hello"), IntegerGene("b", 42)))

        val selection = GeneUtils.getBooleanSelection(obj)
        val a = selection.fields.find { it.name == "a" } as BooleanGene
        a.value = false

        val rep = selection.getValueAsPrintableString(mode = GeneUtils.EscapeMode.BOOLEAN_SELECTION_MODE)
                .replace(" ", "") // remove empty space to make assertion less brittle

        assertEquals("foo{b}", rep)
    }


    @Test
    fun testBooleanSectionTwoNestedObjects() {

        val obj = ObjectGene("Obj1", listOf(ObjectGene("Obj2", listOf(StringGene("a", "hello")))))

        val selection = GeneUtils.getBooleanSelection(obj)

        val rep = selection.getValueAsPrintableString(mode = GeneUtils.EscapeMode.BOOLEAN_SELECTION_MODE)
                .replace(" ", "") // remove empty space to make assertion less brittle

        assertEquals("Obj1{Obj2{a}}", rep)
    }

    @Test
    fun testBooleanSectionThreeNestedObjects() {

        val obj = ObjectGene("Obj1", listOf(ObjectGene("Obj2", listOf(ObjectGene("Obj3", listOf(StringGene("a", "hello")))))))

        val selection = GeneUtils.getBooleanSelection(obj)

        val rep = selection.getValueAsPrintableString(mode = GeneUtils.EscapeMode.BOOLEAN_SELECTION_MODE)
                .replace(" ", "") // remove empty space to make assertion less brittle

        assertEquals("Obj1{Obj2{Obj3{a}}}", rep)
    }


    @Test
    fun testBooleanSectionArray() {

        val array = ArrayGene("Array", ObjectGene("Obj1", listOf(StringGene("a", "hello"))))

        val selection = GeneUtils.getBooleanSelection(array)

        val rep = selection.getValueAsPrintableString(mode = GeneUtils.EscapeMode.BOOLEAN_SELECTION_MODE)
                .replace(" ", "") // remove empty space to make assertion less brittle

        assertEquals("Obj1{a}", rep)
    }

    @Test
    fun testBooleanSectionString() {

        val string = StringGene("a", "hello")

        val selection = GeneUtils.getBooleanSelection(string)

        val rep = selection.getValueAsPrintableString(mode = GeneUtils.EscapeMode.BOOLEAN_SELECTION_MODE)
                .replace(" ", "") // remove empty space to make assertion less brittle

        assertEquals("a", rep)
    }

    @Test
    fun testBooleanSectionInteger() {

        val integer = IntegerGene("a", 1)

        val selection = GeneUtils.getBooleanSelection(integer)

        val rep = selection.getValueAsPrintableString(mode = GeneUtils.EscapeMode.BOOLEAN_SELECTION_MODE)
                .replace(" ", "") // remove empty space to make assertion less brittle

        assertEquals("a", rep)
    }

    @Test
    fun testBooleanSectionOptionalObject() {

        val opt = OptionalGene("Opti", StringGene("Opti", "hello"))
        val selection = GeneUtils.getBooleanSelection(opt)

        val rep = selection.getValueAsPrintableString(mode = GeneUtils.EscapeMode.BOOLEAN_SELECTION_MODE)
                .replace(" ", "") // remove empty space to make assertion less brittle

        assertEquals("Opti", rep)
    }

    @Test
    fun testBooleanSectionBoolean() {

        val boolean = BooleanGene("a")

        val selection = GeneUtils.getBooleanSelection(boolean)

        val rep = selection.getValueAsPrintableString(mode = GeneUtils.EscapeMode.BOOLEAN_SELECTION_MODE)
                .replace(" ", "") // remove empty space to make assertion less brittle

        assertEquals("a", rep)
    }

}