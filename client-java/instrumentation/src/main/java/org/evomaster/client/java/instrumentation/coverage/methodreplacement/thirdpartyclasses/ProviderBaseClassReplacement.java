package org.evomaster.client.java.instrumentation.coverage.methodreplacement.thirdpartyclasses;

import org.evomaster.client.java.instrumentation.coverage.methodreplacement.*;
import org.evomaster.client.java.instrumentation.object.ClassToSchema;
import org.evomaster.client.java.instrumentation.object.JsonTaint;
import org.evomaster.client.java.instrumentation.shared.ReplacementCategory;
import org.evomaster.client.java.instrumentation.shared.ReplacementType;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Objects;

public class ProviderBaseClassReplacement extends ThirdPartyMethodReplacementClass {

   private static final ProviderBaseClassReplacement singleton = new ProviderBaseClassReplacement();

    @Override
    protected String getNameOfThirdPartyTargetClass() {
        //workaround shade plugin bug
        return "  com.fasterxml.jackson.jaxrs.base.ProviderBase".trim();
    }


    @Replacement(replacingStatic = false,
            type = ReplacementType.TRACKER,
            id = "Jackson_ProviderBaser_readFrom",
            usageFilter = UsageFilter.ANY,
            category = ReplacementCategory.EXT_0)
    public static Object readFrom(
            Object caller,
            Class<Object> type,
            Type genericType,
            Annotation[] annotations,
            @ThirdPartyCast(actualType = "javax.ws.rs.core.MediaType") Object mediaType,
            @ThirdPartyCast(actualType = "javax.ws.rs.core.MultivaluedMap") Object httpHeaders,
            InputStream entityStream
    ) throws Throwable {

        Objects.requireNonNull(caller);

        String content = JsonUtils.readStream(entityStream);
        ClassToSchema.registerSchemaIfNeeded(type);
        JsonTaint.handlePossibleJsonTaint(content, type);
        entityStream = JsonUtils.toStream(content);

        Method original = getOriginal(singleton, "Jackson_ProviderBaser_readFrom", caller);

        try {
            return  original.invoke(caller, type, genericType, annotations, mediaType, httpHeaders, entityStream);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
