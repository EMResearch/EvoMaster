package org.evomaster.client.java.instrumentation.coverage.methodreplacement.thirdpartyclasses;


import org.evomaster.client.java.instrumentation.ExternalServiceInfo;
import org.evomaster.client.java.instrumentation.coverage.methodreplacement.*;
import org.evomaster.client.java.instrumentation.shared.PreDefinedSSLInfo;
import org.evomaster.client.java.instrumentation.shared.ReplacementCategory;
import org.evomaster.client.java.instrumentation.shared.ReplacementType;
import org.evomaster.client.java.instrumentation.staticstate.ExecutionTracer;
import org.evomaster.client.java.instrumentation.staticstate.MethodReplacementPreserveSemantics;


import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.evomaster.client.java.instrumentation.coverage.methodreplacement.ExternalServiceInfoUtils.collectExternalServiceInfo;
import static org.evomaster.client.java.instrumentation.coverage.methodreplacement.ExternalServiceInfoUtils.skipHostnameOrIp;

public class OkHttpClientClassReplacement extends ThirdPartyMethodReplacementClass {

    private static final ThreadLocal<Object> instance = new ThreadLocal<>();

    private static final OkHttpClientClassReplacement singleton = new OkHttpClientClassReplacement();

    @Override
    protected String getNameOfThirdPartyTargetClass() {
        return "com.squareup.okhttp.OkHttpClient";
    }


    public static Object consumeInstance(){

        Object client = instance.get();
        if(client == null){
            throw new IllegalStateException("No instance to consume");
        }
        instance.set(null);
        return client;
    }

    private static void addInstance(Object x){
        Object client = instance.get();
        if(client != null){
            throw new IllegalStateException("Previous instance was not consumed");
        }
        instance.set(x);
    }

    @Replacement(replacingStatic = false,
            type = ReplacementType.TRACKER,
            id = "okhttpclient_constructor",
            usageFilter = UsageFilter.ANY,
            category = ReplacementCategory.NET,
            replacingConstructor = true,
            castTo = "com.squareup.okhttp.OkHttpClient"
    )
    public static void OkHttpClient() throws Exception {

        Constructor original = getOriginalConstructor(singleton, "okhttpclient_constructor");

        try {
            Object client =  original.newInstance();
            client.getClass().getMethod("setSslSocketFactory", SSLSocketFactory.class)
                    .invoke(client, PreDefinedSSLInfo.getTrustAllSSLSocketFactory());
            client.getClass().getMethod("setHostnameVerifier", HostnameVerifier.class)
                    .invoke(client,PreDefinedSSLInfo.allowAllHostNames());
            addInstance(client);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw (RuntimeException) e.getCause();
        }

    }


    @Replacement(replacingStatic = false,
            type = ReplacementType.TRACKER,
            id = "okhttpclient_setSslSocketFactory",
            usageFilter = UsageFilter.ANY,
            category = ReplacementCategory.NET,
            castTo = "com.squareup.okhttp.OkHttpClient"
    )
    public static Object setSslSocketFactory(
            Object caller,
            @ThirdPartyCast(actualType = "javax.net.ssl.SSLSocketFactory") Object sslSocketFactory
    ) throws Exception{

        if(caller == null){
            throw new NullPointerException();
        }

        Method original = getOriginal(singleton, "okhttpclient_setSslSocketFactory", caller);

        try{
            return  original.invoke(caller, PreDefinedSSLInfo.getTrustAllSSLSocketFactory());
        } catch (IllegalAccessException e){
            throw new RuntimeException(e);
        } catch (InvocationTargetException e){
            throw (Exception) e.getCause();
        }
    }

    @Replacement(replacingStatic = false,
            type = ReplacementType.TRACKER,
            id = "okhttpclient_setHostnameVerifier",
            usageFilter = UsageFilter.ANY,
            category = ReplacementCategory.NET,
            castTo = "com.squareup.okhttp.OkHttpClient"
    )
    public static Object setHostnameVerifier(
            Object caller,
            @ThirdPartyCast(actualType = "javax.net.ssl.HostnameVerifier") Object hostnameVerifier
    )throws Exception {
        if(caller == null){
            throw new NullPointerException();
        }

        Method original = getOriginal(singleton, "okhttpclient_setHostnameVerifier", caller);

        try{
            return original.invoke(caller, PreDefinedSSLInfo.allowAllHostNames());
        } catch (IllegalAccessException e){
            throw new RuntimeException(e);
        } catch (InvocationTargetException e){
            throw (Exception) e.getCause();
        }
    }

    @Replacement(replacingStatic = false,
            type = ReplacementType.TRACKER,
            id = "okhttpclient_newCall",
            usageFilter = UsageFilter.ANY,
            category = ReplacementCategory.NET,
            castTo = "com.squareup.okhttp.Call"
    )
    public static Object newCall(
            Object caller,
            @ThirdPartyCast(actualType = "com.squareup.okhttp.Request") Object request
    ) throws Exception{
        if(caller == null){
            throw new NullPointerException();
        }

        Method original = getOriginal(singleton, "okhttpclient_newCall", caller);

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
        Object url = request.getClass().getMethod("httpUrl").invoke(request);
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
                replacedUrl = replacedUrl + "?" + encodedQuery;

            ClassLoader loader = ExecutionTracer.getLastCallerClassLoader();
            Object builder = loader.loadClass("com.squareup.okhttp.Request$Builder").newInstance();
            builder = builder.getClass().getMethod("url", String.class).invoke(builder, replacedUrl);
            builder = builder.getClass().getMethod("method", String.class, loader.loadClass("com.squareup.okhttp.RequestBody"))
                    .invoke(builder, method, body);
            builder = builder.getClass().getMethod("headers", loader.loadClass("com.squareup.okhttp.Headers"))
                    .invoke(builder, headers);
            replaced = builder.getClass().getMethod("build").invoke(builder);
        }

        try{
            return  original.invoke(caller, replaced);
        } catch (IllegalAccessException e){
            throw new RuntimeException(e);
        } catch (InvocationTargetException e){
            throw (RuntimeException) e.getCause();
        }
    }


}
