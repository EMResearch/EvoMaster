package org.evomaster.client.java.instrumentation.coverage.methodreplacement.thirdpartyclasses;

import org.evomaster.client.java.instrumentation.coverage.methodreplacement.*;
import org.evomaster.client.java.instrumentation.shared.ReplacementCategory;
import org.evomaster.client.java.instrumentation.shared.ReplacementType;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Objects;

public class MessageBodyReaderClassReplacement extends ThirdPartyMethodReplacementClass {

    private static final MessageBodyReaderClassReplacement singleton = new MessageBodyReaderClassReplacement();

    @Override
    protected String getNameOfThirdPartyTargetClass() {
        return " javax.ws.rs.ext.MessageBodyReader".trim();
    }

    @Replacement(replacingStatic = false,
            type = ReplacementType.TRACKER,
            id = "Javax_MessageBodyReader_readFrom",
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

        String className = caller.getClass().getName();
        if(className.contains(".jackson.")){
            return ProviderBaseClassReplacement.readFrom(caller,type,genericType,annotations,mediaType,httpHeaders,entityStream);
        }


        Method original = getOriginal(singleton, "Javax_MessageBodyReader_readFrom", caller);

        try {
            return  original.invoke(caller, type, genericType, annotations, mediaType, httpHeaders, entityStream);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
