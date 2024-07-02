package org.evomaster.client.java.instrumentation.coverage.methodreplacement.thirdpartyclasses;

import org.evomaster.client.java.instrumentation.coverage.methodreplacement.*;
import org.evomaster.client.java.instrumentation.object.ClassToSchema;
import org.evomaster.client.java.instrumentation.object.JsonTaint;
import org.evomaster.client.java.instrumentation.shared.ReplacementCategory;
import org.evomaster.client.java.instrumentation.shared.ReplacementType;


import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public class JacksonObjectMapperClassReplacement extends ThirdPartyMethodReplacementClass {

    private static final JacksonObjectMapperClassReplacement singleton = new JacksonObjectMapperClassReplacement();

    @Override
    protected String getNameOfThirdPartyTargetClass() {
        /*
            this is to avoid a 2013 bug in shade plugin:
            https://issues.apache.org/jira/browse/MSHADE-156
            note that Jackson is shaded in pom of controller
         */
        return "   com.fasterxml.jackson.databind.ObjectMapper".trim();
    }


    private static void analyzeClass(Class<?> valueType, String content){
        ClassToSchema.registerSchemaIfNeeded(valueType);
        JsonTaint.handlePossibleJsonTaint(content, valueType);
    }



    /*
        TODO:
        ideally, should provide method replacements for every single "readValue(...)" implementation.
        These can call each other (after modifying and preparing the inputs), and, as Jackson itself is instrumented,
        it should not be a problem.
        But we have seen issues in internal changes of Jackson, eg 2.11.0 vs 2.9.6, in which this would not work.
     */



    @Replacement(replacingStatic = false,
            type = ReplacementType.TRACKER,
            id = "Jackson_ObjectMapper_readValue_InputStream_Generic_class",
            usageFilter = UsageFilter.ANY,
            category = ReplacementCategory.EXT_0)
    public static <T> T readValue(Object caller, InputStream src, Class<T> valueType) throws Throwable {
        Objects.requireNonNull(caller);

        String content = JsonUtils.readStream(src);
        analyzeClass(valueType, content);
        src = JsonUtils.toStream(content);

        Method original = getOriginal(singleton, "Jackson_ObjectMapper_readValue_InputStream_Generic_class", caller);

        try {
            return (T) original.invoke(caller, src, valueType);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Replacement(replacingStatic = false,
            type = ReplacementType.TRACKER,
            id = "Jackson_ObjectMapper_readValue_InputStream_JavaType_class",
            usageFilter = UsageFilter.ANY,
            category = ReplacementCategory.EXT_0)
    public static <T> T readValue(Object caller, InputStream src,
                                  @ThirdPartyCast(actualType = "  com.fasterxml.jackson.databind.JavaType") Object valueType)
            throws Throwable {
        Objects.requireNonNull(caller);

        Class<?> typeClass = (Class) valueType.getClass().getMethod("getRawClass").invoke(valueType);
        String content = JsonUtils.readStream(src);
        analyzeClass(typeClass, content);
        src = JsonUtils.toStream(content);

        Method original = getOriginal(singleton, "Jackson_ObjectMapper_readValue_InputStream_JavaType_class", caller);

        try {
            return (T) original.invoke(caller, src, valueType);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Replacement(replacingStatic = false,
            type = ReplacementType.TRACKER,
            id = "Jackson_ObjectMapper_readValue_String_JavaType_class",
            usageFilter = UsageFilter.ANY,
            category = ReplacementCategory.EXT_0)
    public static <T> T readValue_EM_0(Object caller, String content,
                                  @ThirdPartyCast(actualType = "  com.fasterxml.jackson.databind.JavaType") Object valueType)
            throws Throwable {
        Objects.requireNonNull(caller);

        Class<?> typeClass = (Class) valueType.getClass().getMethod("getRawClass").invoke(valueType);
        analyzeClass(typeClass, content);

        Method original = getOriginal(singleton, "Jackson_ObjectMapper_readValue_String_JavaType_class", caller);

        try {
            return (T) original.invoke(caller, content, valueType);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }


    @Replacement(replacingStatic = false,
            type = ReplacementType.TRACKER,
            id = "Jackson_ObjectMapper_readValue_String_TypeReference_class",
            usageFilter = UsageFilter.ANY,
            category = ReplacementCategory.EXT_0)
    public static <T> T readValue_EM_1(Object caller, String content,
                                  @ThirdPartyCast(actualType = "  com.fasterxml.jackson.core.type.TypeReference") Object valueTypeRef)
            throws Throwable {

        Objects.requireNonNull(caller);
        //_typeFactory.constructType(valueTypeRef)
        Field _typeFactoryField = caller.getClass().getDeclaredField("_typeFactory");
        _typeFactoryField.setAccessible(true);
        Object _typeFactory = _typeFactoryField.get(caller);
        Method constructType = Arrays.stream(_typeFactory.getClass().getDeclaredMethods())
                .filter(m -> m.getName().equals("constructType"))
                .filter(m -> m.getParameterTypes().length == 1)
                .filter(m-> m.getParameterTypes()[0].getName().endsWith("TypeReference"))
                .findFirst().orElse(null);
        Object javaType = constructType.invoke(_typeFactory, valueTypeRef);
        return readValue_EM_0(caller, content, javaType);
    }



    @Replacement(replacingStatic = false,
            type = ReplacementType.TRACKER,
            id = "Jackson_ObjectMapper_readValue_String_Generic_class",
            usageFilter = UsageFilter.ANY,
            category = ReplacementCategory.EXT_0)
    public static <T> T readValue(Object caller, String content, Class<T> valueType) throws Throwable {
        Objects.requireNonNull(caller);

        analyzeClass(valueType, content);

        // JSON can be unwrapped using different approaches
        // val dto: FooDto = mapper.readValue(json)
        // To support this way, Jackson should be used inside the instrumentation
        // as shaded dependency. And that crates new problems.
        // Note: For now it's not supported

        Method original = getOriginal(singleton, "Jackson_ObjectMapper_readValue_String_Generic_class", caller);

        try {
            return (T) original.invoke(caller, content, valueType);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }


    @Replacement(replacingStatic = false,
            type = ReplacementType.TRACKER,
            id = "Jackson_ObjectMapper_convertValue_Generic_class",
            usageFilter = UsageFilter.ANY,
            category = ReplacementCategory.EXT_0)
    public static <T> T convertValue(Object caller, Object fromValue, Class<T> toValueType) throws Throwable {
        Objects.requireNonNull(caller);

        ClassToSchema.registerSchemaIfNeeded(toValueType);

        if (fromValue instanceof String) {
            JsonTaint.handlePossibleJsonTaint((String) fromValue, toValueType);
        }

        Method original = getOriginal(singleton, "Jackson_ObjectMapper_convertValue_Generic_class", caller);

        try {
            return (T) original.invoke(caller, fromValue, toValueType);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

}
