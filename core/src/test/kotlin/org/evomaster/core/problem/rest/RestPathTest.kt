package org.evomaster.core.problem.rest

import io.swagger.v3.oas.models.parameters.Parameter
import org.evomaster.core.problem.rest.param.PathParam
import org.evomaster.core.problem.rest.param.QueryParam
import org.evomaster.core.search.gene.collection.ArrayGene
import org.evomaster.core.search.gene.optional.CustomMutationRateGene
import org.evomaster.core.search.gene.numeric.IntegerGene
import org.evomaster.core.search.gene.string.StringGene
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class RestPathTest{


    @Test
    fun testNameQualifier(){

        val balance = RestPath("/users/{id}/balance")
        val user = RestPath("/users/{id}")
        val users = RestPath("/users")
        val root = RestPath("/{x}/{y}")

        assertEquals("balance", balance.nameQualifier)
        assertEquals("user", user.nameQualifier)
        assertEquals("users", users.nameQualifier)
        assertEquals("/", root.nameQualifier)
    }

    @Test
    fun testGestaohospitalIssue(){

        val original = "/v1/hospitais/"

        val path = RestPath(original)

        assertEquals(original, path.toString())
    }

    @Test
    fun testResolvePathWithSlash(){

        val id = 77
        val pathParam = PathParam("id", CustomMutationRateGene("d_", IntegerGene("id", id), 1.0))

        val path = "/api/foo/{id}/"
        val restPath = RestPath(path)

        val resolved = restPath.resolve(listOf(pathParam))
        assertEquals("/api/foo/$id/", resolved)
    }

    @Test
    fun testQueryIntWithSlash(){

        val path = RestPath("/x/")

        val a = QueryParam("a", IntegerGene("a", 5))

        val uri = path.resolve(listOf(a))

        assertEquals("/x/?a=5", uri)
    }

    @Test
    fun testMatchResolvedPathWithSlash(){
        val path = RestPath("/x/{y}/z/")

        val resolvedPath = "/x/example/z/"

        assertTrue(path.matches(resolvedPath))
    }

    @Test
    fun testResolveMultiVariableInterluded(){

        val x = 7
        val xParam = PathParam("x", CustomMutationRateGene("d_", IntegerGene("x", x), 1.0))

        val y = 42
        val yParam = PathParam("y", CustomMutationRateGene("d_", IntegerGene("y", y), 1.0))

        val path = "/api/foo/{x}/{y}/x-{x}/{y}-y/a{x}b{y}c"
        val restPath = RestPath(path)

        val resolved = restPath.resolve(listOf(yParam,xParam))
        assertEquals("/api/foo/$x/$y/x-$x/$y-y/a${x}b${y}c", resolved)
    }

    @Test
    fun testResolveMultiVariable(){
        val x = 7
        val xParam = PathParam("x", CustomMutationRateGene("d_", IntegerGene("x", x), 1.0))

        val y = 42
        val yParam = PathParam("y", CustomMutationRateGene("d_", IntegerGene("y", y), 1.0))

        val path = "/api/foo/{x}{y}"
        val restPath = RestPath(path)

        val resolved = restPath.resolve(listOf(yParam,xParam))
        assertEquals("/api/foo/$x$y", resolved)
    }

    @Test
    fun testResolvePathWithVariable(){

        val id = 5
        val pathParam = PathParam("id", CustomMutationRateGene("d_", IntegerGene("id", id), 1.0))

        val path = "/api/foo/{id}"
        val restPath = RestPath(path)

        val resolved = restPath.resolve(listOf(pathParam))
        assertEquals("/api/foo/$id", resolved)
    }

    @Test
    fun testExtraSlashes(){

        val path = "/x/y/z"
        val restPath = RestPath("//$path")

        val toS = restPath.toString()
        assertEquals(path, toS)

        val resolved = restPath.resolve(listOf())
        assertEquals(path, resolved)
    }

    @Test
    fun testEquivalent(){

        val path = "/x/w"

        val a = RestPath(path)
        val b = RestPath(path)

        assertTrue(a.isEquivalent(b))
        assertTrue(b.isEquivalent(a))
        assertTrue(a.isEquivalent(a))

        val withParam = "/x/{w}"
        val c = RestPath(withParam)
        assertFalse(a.isEquivalent(c))
    }

    @Test
    fun testLastIsParam(){

        val a = RestPath("/x/w")
        val b = RestPath("/x/{w}")
        val c = RestPath("/x/{w}/y")
        val d = RestPath("/x/{w}/y/{z}")

        assertFalse(a.isLastElementAParameter())
        assertTrue( b.isLastElementAParameter())
        assertFalse(c.isLastElementAParameter())
        assertTrue( d.isLastElementAParameter())
    }

    @Test
    fun testDirectChild(){
        val a = RestPath("/x/w")
        val b = RestPath("/x/{w}")
        val c = RestPath("/x/{w}/y")
        val d = RestPath("/x/{w}/y/{z}")

        assertFalse(a.isDirectChildOf(a))
        assertFalse(a.isDirectChildOf(b))
        assertFalse(a.isDirectChildOf(c))
        assertFalse(a.isDirectChildOf(d))

        assertFalse(c.isDirectChildOf(a))
        assertTrue(c.isDirectChildOf(b))

        assertFalse(d.isDirectChildOf(b))
        assertTrue(d.isDirectChildOf(c))
    }

    @Test
    fun testQueryInt(){

        val path = RestPath("/x")

        val a = QueryParam("a", IntegerGene("a", 5))

        val uri = path.resolve(listOf(a))

        assertEquals("/x?a=5", uri)
    }

    @Test
    fun testQueryString(){

        val path = RestPath("/x")

        val a = QueryParam("a", StringGene("a", "foo"))

        val uri = path.resolve(listOf(a))

        assertEquals("/x?a=foo", uri)
    }

    @Test
    fun testQueryCombination(){

        val path = RestPath("/x")

        val a = QueryParam("a", IntegerGene("a", 5))
        val b = QueryParam("b", StringGene("b", "foo"))

        val uri = path.resolve(listOf(a,b))

        assertEquals("/x?a=5&b=foo", uri)
    }

    @Test
    fun testQueryEmptySpace(){

        val path = RestPath("/x")

        val a = QueryParam("a", StringGene("a", "foo bar"))

        val uri = path.resolve(listOf(a))

        assertEquals("/x?a=foo+bar", uri)
    }

    @Test
    fun testQuerySquareBrackets(){

        val path = RestPath("/x")

        val a = QueryParam("a", StringGene("a", "[foo]"))

        val uri = path.resolve(listOf(a))

        assertEquals("/x?a=%5Bfoo%5D", uri)
    }

    @Test
    fun testQueryAndEqual(){

        val path = RestPath("/x")

        val a = QueryParam("a", StringGene("a", "foo &= bar"))
        val b = QueryParam("b", StringGene("b", "z"))

        val uri = path.resolve(listOf(a,b))

        assertEquals("/x?a=foo+%26%3D+bar&b=z", uri)
    }

    @Test
    fun testQueryQuotesAndUTF8(){

        val path = RestPath("/x")

        val a = QueryParam("a", StringGene("a", "\"A\" ± B"))

        val uri = path.resolve(listOf(a))

        assertEquals("/x?a=%22A%22+%C2%B1+B", uri)
    }

    @Test
    fun testEscapeInPath(){

        val path = RestPath("/x y")

        val uri = path.resolve(listOf())

        assertEquals("/x%20y", uri)
    }

    @Test
    fun testEscapeInPathAndQuery(){

        val path = RestPath("/x y")

        val a = QueryParam("a", StringGene("a", "k w"))

        val uri = path.resolve(listOf(a))

        assertEquals("/x%20y?a=k+w", uri)
    }

    @Test
    fun testPlus(){
        val path = RestPath("/x + y")

        val a = QueryParam("a", StringGene("a", "k + w"))

        val uri = path.resolve(listOf(a))

        assertEquals("/x%20+%20y?a=k+%2B+w", uri)
    }

    @Test
    fun testMatchResolvedPath(){
        val path = RestPath("/x/{y}/z")

        val resolvedPath = "/x/example/z"

        assertTrue(path.matches(resolvedPath))
    }

    @Test
    fun testMatchResolvedPathCompound(){
        val path = RestPath("/x/{y}-{anotherParam}/z")

        val resolvedPath = "/x/example-2/z"

        assertTrue(path.matches(resolvedPath))
    }

    @Test
    fun testMatchResolvedPathSemiCompound(){
        val path = RestPath("/x/y-{anotherParam}/z")

        val resolvedPath = "/x/y-2/z"

        assertTrue(path.matches(resolvedPath))
    }

    @Test
    fun testNotMatchResolvedPath(){
        val path = RestPath("/x/y")

        val resolvedPath = "/x/y/z"

        assertFalse(path.matches(resolvedPath))
    }

    @Test
    fun testNotMatchResolvedPathWithParam(){
        val path = RestPath("/x/y/{param}")

        val resolvedPath = "/x/error/param"

        assertFalse(path.matches(resolvedPath))
    }

    @Test
    fun testNotMatchResolvedPathCompound(){
        val path = RestPath("/x/{y}-{anotherParam}/z")

        val resolvedPath = "/x/example/z"

        assertFalse(path.matches(resolvedPath))
    }

    @Test
    fun testNotMatchResolvedPathSemiCompound(){
        val path = RestPath("/x/y-{anotherParam}/z")

        val resolvedPath = "/x/example/z"

        assertFalse(path.matches(resolvedPath))
    }

    @Test
    fun testGetKeyValuesWrongPath() {
        val path = RestPath("/x/{y}/{z}")

        val resolvedPath = "/x/example/example2/example3"

        assertNull(path.getKeyValues(resolvedPath))
    }

    @Test
    fun testGetKeyValues() {
        val path = RestPath("/x/{y}/{z}")

        val resolvedPath = "/x/example/z"

        val keyValues = path.getKeyValues(resolvedPath)

        assertEquals(2, keyValues?.size)
        assertEquals("example", keyValues?.get("y"))
        assertEquals("z", keyValues?.get("z"))
    }

    @Test
    fun testGetKeyValuesPathCompound(){
        val path = RestPath("/x/{y}-{anotherParam}/z")

        val resolvedPath = "/x/example-2/z"

        val keyValues = path.getKeyValues(resolvedPath)

        assertEquals(2, keyValues?.size)
        assertEquals("example", keyValues?.get("y"))
        assertEquals("2", keyValues?.get("anotherParam"))
    }

    @Test
    fun testGetKeyValuesPathSemiCompound(){
        val path = RestPath("/{firsParam}/y-{anotherParam}/{thirdParam}/z")

        val resolvedPath = "/x/y-2/z/z"

        val keyValues = path.getKeyValues(resolvedPath)

        assertEquals(3, keyValues?.size)
        assertEquals("x", keyValues?.get("firsParam"))
        assertEquals("2", keyValues?.get("anotherParam"))
        assertEquals("z", keyValues?.get("thirdParam"))
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "/v2/api-docs?group=1ocDashboardsApi&foo=42",
        "/v2/api-docs?group="
    ])
    fun testGivenUrl(path: String){
        val base = "http://localhost:12625"
        val action = RestActionBuilderV3.buildActionBasedOnUrl(base,"call to swagger", HttpVerb.GET, "$base$path", true)

        assertNotNull(action)
        assertEquals(path, action!!.resolvedPath())

    }

    @Test
    fun testFragmentInURL(){
        val base = "http://localhost:12625"
        val path = "/v3/foo"
        val fragment = "#hello"
        val action = RestActionBuilderV3.buildActionBasedOnUrl(base,"call to swagger", HttpVerb.GET, "$base$path$fragment", true)
        assertNotNull(action)
        assertEquals(path, action!!.resolvedPath())
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "/v2/api-docs?group",
        "/v2/api-docs?group=1ocDashboardsApi&=42#sec"
    ])
    fun testNotFullySupportUrl(path: String){
        val base = "http://localhost:12625"
        val action = RestActionBuilderV3.buildActionBasedOnUrl(base,"call to swagger", HttpVerb.GET, "$base$path", true)

        assertNull(action)
    }

    @Test
    fun testArrayExplode(){

        val path = RestPath("/x")

        val array = ArrayGene("a", IntegerGene("template"),
            elements = mutableListOf(IntegerGene("a", 1), IntegerGene("a", 2)))

        val a = QueryParam("a", array, explode = true)

        val uri = path.resolve(listOf(a))

        assertEquals("/x?a=1&a=2", uri)
    }

    @Test
    fun testArrayNotExplode(){

        val path = RestPath("/x")

        val array = ArrayGene("a", IntegerGene("template"),
            elements = mutableListOf(IntegerGene("a", 1), IntegerGene("a", 2)))

        val a = QueryParam("a", array, explode = false)

        val uri = path.resolve(listOf(a))

        assertEquals("/x?a=1%2C2", uri)
    }

    @Test
    fun testArrayNotExplodeSpace(){

        val path = RestPath("/x")

        val array = ArrayGene("a", IntegerGene("template"),
            elements = mutableListOf(IntegerGene("a", 1), IntegerGene("a", 2)))

        val a = QueryParam("a", array, explode = false, style = Parameter.StyleEnum.SPACEDELIMITED)

        val uri = path.resolve(listOf(a))

        /*
            spaces could be encoded with %20 or + (depending on context)
            TODO double-check if here should really be + and not %20
         */
        assertEquals("/x?a=1+2", uri)
    }


    @Test
    fun testisPossibleAncestorOf(){
        val rootFooBarPath = RestPath("/root/{rootName}/foo/{fooName}/bar/{barName}")
        val rootBarPath = RestPath("/root/{rootName}/bar/{barName}")
        val rootFooPath = RestPath("/root/{rootName}/foo/{fooName}")
        val rootPath = RestPath("/root/{rootName}")

        assertTrue(rootPath.isDirectOrPossibleAncestorOf(rootBarPath))
        assertTrue(rootPath.isDirectOrPossibleAncestorOf(rootFooPath))
        assertTrue(rootPath.isDirectOrPossibleAncestorOf(rootFooBarPath))

        assertTrue(rootBarPath.isDirectOrPossibleAncestorOf(rootFooBarPath))
        assertTrue(rootFooPath.isDirectOrPossibleAncestorOf(rootFooBarPath))

        assertFalse(rootFooPath.isDirectOrPossibleAncestorOf(rootBarPath))
        assertFalse(rootBarPath.isDirectOrPossibleAncestorOf(rootFooPath))
    }

    @Test
    fun testIsSibling(){
        val rootFooBarPath = RestPath("/root/{rootName}/foo/{fooName}/bar/{barName}")
        val rootFooBar = RestPath("/root/{rootName}/foo/{fooName}/bar")
        assertTrue(rootFooBarPath.isSiblingForPreparingResource(rootFooBar))
        assertTrue(rootFooBar.isSiblingForPreparingResource(rootFooBarPath))
    }

    @Test
    fun testParent(){

        val path = RestPath("/api/users/{id}/balance/")
        assertFalse(path.isRoot())

        var parent =  path.parentPath()
        assertEquals("/api/users/{id}/balance", parent.toString())

        parent = parent.parentPath()
        assertEquals("/api/users/{id}", parent.toString())

        parent = parent.parentPath()
        assertEquals("/api/users", parent.toString())

        parent = parent.parentPath()
        assertEquals("/api", parent.toString())

        parent = parent.parentPath()
        assertEquals("/", parent.toString())
        assertTrue(parent.isRoot())
    }

    @Test
    fun testResolveOnlyPathNoParam(){

        val x = "/api/foo/bar/"
        val resolvedX = RestPath(x).resolveOnlyPath(listOf())
        assertEquals(x, resolvedX)

        val y = "/"
        val resolvedY = RestPath(y).resolveOnlyPath(listOf())
        assertEquals(y, resolvedY)
    }

}