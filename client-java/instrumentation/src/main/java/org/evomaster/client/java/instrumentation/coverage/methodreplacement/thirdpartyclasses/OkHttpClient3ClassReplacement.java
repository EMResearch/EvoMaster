package org.evomaster.client.java.instrumentation.coverage.methodreplacement.thirdpartyclasses;


import org.evomaster.client.java.instrumentation.ExternalServiceInfo;
import org.evomaster.client.java.instrumentation.coverage.methodreplacement.*;
import org.evomaster.client.java.instrumentation.shared.ReplacementCategory;
import org.evomaster.client.java.instrumentation.shared.ReplacementType;
import org.evomaster.client.java.instrumentation.staticstate.ExecutionTracer;
import org.evomaster.client.java.instrumentation.staticstate.MethodReplacementPreserveSemantics;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


import static org.evomaster.client.java.instrumentation.coverage.methodreplacement.ExternalServiceInfoUtils.collectExternalServiceInfo;
import static org.evomaster.client.java.instrumentation.coverage.methodreplacement.ExternalServiceInfoUtils.skipHostnameOrIp;

public class OkHttpClient3ClassReplacement extends ThirdPartyMethodReplacementClass {

    private static ThreadLocal<Object> instance = new ThreadLocal<>();

    private static final OkHttpClient3ClassReplacement singleton = new OkHttpClient3ClassReplacement();

    @Override
    protected String getNameOfThirdPartyTargetClass() {
        return "okhttp3.OkHttpClient";
    }


    public static Object consumeInstance(){

        Object client =  instance.get();
        if(client == null){
            throw new IllegalStateException("No instance to consume");
        }
        instance.set(null);
        return client;
    }

    private static void addInstance(Object x){
        Object client =  instance.get();
        if(client != null){
            throw new IllegalStateException("Previous instance was not consumed");
        }
        instance.set(x);
    }

    @Replacement(replacingStatic = false,
            type = ReplacementType.TRACKER,
            id = "okhttpclient3_constructor",
            usageFilter = UsageFilter.ANY,
            category = ReplacementCategory.NET,
            replacingConstructor = true,
            castTo = "okhttp3.OkHttpClient"
    )
    public static void OkHttpClient()  throws Exception{

        if (!OkHttpClient3BuilderClassReplacement.hasInstance())
            OkHttpClient3BuilderClassReplacement.Builder();

        Object builder = OkHttpClient3BuilderClassReplacement.consumeInstance();
        try {
            Object client = builder.getClass().getMethod("build")
                            .invoke(builder);
            addInstance(client);
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    @Replacement(replacingStatic = false,
            type = ReplacementType.TRACKER,
            id = "okhttpclient3_newCall",
            usageFilter = UsageFilter.ANY,
            category = ReplacementCategory.NET,
            castTo = "okhttp3.Call"
    )
    public static Object newCall(
            Object caller,
            @ThirdPartyCast(actualType = "okhttp3.Request") Object request
    ) throws Exception{
        if(caller == null){
            throw new NullPointerException();
        }

        Method original = getOriginal(singleton, "okhttpclient3_newCall", caller);

        if (MethodReplacementPreserveSemantics.shouldPreserveSemantics) {
            try{
                return  original.invoke(caller, request);
            } catch (IllegalAccessException e){
                throw new RuntimeException(e);
            } catch (InvocationTargetException e){
                throw (Exception) e.getCause();
            }
        }

        Object replaced = request;

        Object url = request.getClass().getMethod("url").invoke(request);
        String urlScheme = (String) url.getClass().getMethod("scheme").invoke(url);
        String urlHost = (String) url.getClass().getMethod("host").invoke(url);
        String method = (String) request.getClass().getMethod("method").invoke(request);
        Object body = request.getClass().getMethod("body").invoke(request);
        Object headers = request.getClass().getMethod("headers").invoke(request);
        int urlPort = (int) url.getClass().getMethod("port").invoke(url);
        String urlEncodedPath = (String) url.getClass().getMethod("encodedPath").invoke(url);

        if ((urlScheme.equalsIgnoreCase("https") || urlScheme.equalsIgnoreCase("http"))
                && !skipHostnameOrIp(urlHost)
                && !ExecutionTracer.skipHostname(urlHost)
        ){
            // To fetch DNS information
            ExternalServiceInfoUtils.analyzeDnsResolution(urlHost);

            ExternalServiceInfo remoteHostInfo = new ExternalServiceInfo(urlScheme, urlHost, urlPort);
            String[] ipAndPort = collectExternalServiceInfo(remoteHostInfo, urlPort);

            String replacedUrl = urlScheme+"://"+ipAndPort[0]+":"+ipAndPort[1]+urlEncodedPath;
            Object encodedQuery = url.getClass().getMethod("encodedQuery").invoke(url);
            if (encodedQuery != null && !((String)encodedQuery).isEmpty())
                replacedUrl = replacedUrl + "?" + (String)encodedQuery;
            ClassLoader loader = ExecutionTracer.getLastCallerClassLoader();
            Object builder = loader.loadClass("okhttp3.Request$Builder").newInstance();
            builder = builder.getClass().getMethod("url", String.class).invoke(builder, replacedUrl);
            builder = builder.getClass().getMethod("method", String.class, loader.loadClass("okhttp3.RequestBody"))
                    .invoke(builder, method, body);
            builder = builder.getClass().getMethod("headers", loader.loadClass("okhttp3.Headers"))
                    .invoke(builder, headers);
            replaced = builder.getClass().getMethod("build").invoke(builder);
        }

        try{
            return  original.invoke(caller, replaced);
        } catch (IllegalAccessException e){
            throw new RuntimeException(e);
        } catch (InvocationTargetException e){
            throw (Exception) e.getCause();
        }
    }
}
