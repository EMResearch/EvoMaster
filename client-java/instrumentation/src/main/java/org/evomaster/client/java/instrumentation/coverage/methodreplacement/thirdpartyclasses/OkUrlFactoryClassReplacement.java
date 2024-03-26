package org.evomaster.client.java.instrumentation.coverage.methodreplacement.thirdpartyclasses;

import org.evomaster.client.java.instrumentation.ExternalServiceInfo;
import org.evomaster.client.java.instrumentation.coverage.methodreplacement.ExternalServiceInfoUtils;
import org.evomaster.client.java.instrumentation.coverage.methodreplacement.Replacement;
import org.evomaster.client.java.instrumentation.coverage.methodreplacement.ThirdPartyMethodReplacementClass;
import org.evomaster.client.java.instrumentation.coverage.methodreplacement.UsageFilter;
import org.evomaster.client.java.instrumentation.shared.ReplacementCategory;
import org.evomaster.client.java.instrumentation.shared.ReplacementType;
import org.evomaster.client.java.instrumentation.staticstate.ExecutionTracer;
import org.evomaster.client.java.instrumentation.staticstate.MethodReplacementPreserveSemantics;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;

import static org.evomaster.client.java.instrumentation.coverage.methodreplacement.ExternalServiceInfoUtils.collectExternalServiceInfo;
import static org.evomaster.client.java.instrumentation.coverage.methodreplacement.ExternalServiceInfoUtils.skipHostnameOrIp;

public class OkUrlFactoryClassReplacement extends ThirdPartyMethodReplacementClass {

    private static final OkUrlFactoryClassReplacement singleton = new OkUrlFactoryClassReplacement();

    @Override
    protected String getNameOfThirdPartyTargetClass() {
        return "com.squareup.okhttp.OkUrlFactory";
    }

    @Replacement(replacingStatic = false,
            type = ReplacementType.TRACKER,
            id = "okhttpclient_OkUrlFactory_open",
            usageFilter = UsageFilter.ANY,
            category = ReplacementCategory.NET
    )
    public static HttpURLConnection open(Object caller, URL url) {
        if(caller == null){
            throw new NullPointerException();
        }

        Method original = getOriginal(singleton, "okhttpclient_OkUrlFactory_open", caller);

        URL replaced = getReplacedURL(url);
        try{
            return (HttpURLConnection) original.invoke(caller, replaced);
        } catch (IllegalAccessException e){
            throw new RuntimeException(e);
        } catch (InvocationTargetException e){
            throw (RuntimeException) e.getCause();
        }
    }

    @Replacement(replacingStatic = false,
            type = ReplacementType.TRACKER,
            id = "okhttpclient_OkUrlFactory_open_proxy",
            usageFilter = UsageFilter.ANY,
            category = ReplacementCategory.NET
    )
    public static HttpURLConnection open(Object caller, URL url, Proxy proxy) {
        if(caller == null){
            throw new NullPointerException();
        }

        Method original = getOriginal(singleton, "okhttpclient_OkUrlFactory_open_proxy", caller);

        URL replaced = getReplacedURL(url);
        try{
            return (HttpURLConnection) original.invoke(caller, replaced, proxy);
        } catch (IllegalAccessException e){
            throw new RuntimeException(e);
        } catch (InvocationTargetException e){
            throw (RuntimeException) e.getCause();
        }
    }

    private static URL getReplacedURL(URL url){
        if (MethodReplacementPreserveSemantics.shouldPreserveSemantics) {
            return url;
        }

        URL replaced = url;
        if ((url.getProtocol().equalsIgnoreCase("https") || url.getProtocol().equalsIgnoreCase("http"))
                && !skipHostnameOrIp(url.getHost())
                && !ExecutionTracer.skipHostname(url.getHost()))
        {

            int port = ExternalServiceInfoUtils.inferPort(url.getPort(), url.getProtocol());

            ExternalServiceInfo remoteHostInfo = new ExternalServiceInfo(url.getProtocol(), url.getHost(), port);
            String[] ipAndPort = collectExternalServiceInfo(remoteHostInfo, port);
            try {
                String urlString = url.getProtocol()+"://" + ipAndPort[0]+":"+ipAndPort[1] + url.getPath();
                replaced = new URL(urlString);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
        return replaced;
    }
}
