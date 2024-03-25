package org.evomaster.client.java.instrumentation.coverage.methodreplacement.classes;

import org.evomaster.client.java.distance.heuristics.DistanceHelper;
import org.evomaster.client.java.instrumentation.ExternalServiceInfo;
import org.evomaster.client.java.instrumentation.shared.PreDefinedSSLInfo;
import org.evomaster.client.java.instrumentation.coverage.methodreplacement.*;
import org.evomaster.client.java.distance.heuristics.Truthness;
import org.evomaster.client.java.instrumentation.shared.*;
import org.evomaster.client.java.instrumentation.staticstate.ExecutionTracer;
import org.evomaster.client.java.instrumentation.staticstate.MethodReplacementPreserveSemantics;
import org.evomaster.client.java.utils.SimpleLogger;

import java.net.*;
import java.util.Objects;

import static org.evomaster.client.java.instrumentation.coverage.methodreplacement.ExternalServiceInfoUtils.collectExternalServiceInfo;
import static org.evomaster.client.java.instrumentation.coverage.methodreplacement.ExternalServiceInfoUtils.skipHostnameOrIp;

public class URLClassReplacement implements MethodReplacementClass {


    private static ThreadLocal<URL> instance = new ThreadLocal<>();


    /**
     * This is an experimental implementation, not perfect yet.
     * */

    @Override
    public Class<?> getTargetClass() {
        return URL.class;
    }


    public static URL consumeInstance(){

        URL url = instance.get();
        if(url == null){
            //this should never happen, unless bug in instrumentation, or edge case we didn't think of
            throw new IllegalStateException("No instance to consume");
        }
        instance.set(null);
        return url;
    }

    private static void addInstance(URL x){
        URL url = instance.get();
        if(url != null){
            //this should never happen, unless bug in instrumentation, or edge case we didn't think of
            throw new IllegalStateException("Previous instance was not consumed");
        }
        instance.set(x);
    }

    @Replacement(
            type = ReplacementType.EXCEPTION,
            category = ReplacementCategory.EXT_0,
            replacingConstructor = true
    )
    public static void URL(String s, String idTemplate) throws MalformedURLException {
        URL(null, s, null, idTemplate);
    }

    @Replacement(
            type = ReplacementType.EXCEPTION,
            category = ReplacementCategory.EXT_0,
            replacingConstructor = true
    )
    public static void URL(URL context, String s, String idTemplate) throws MalformedURLException {
        URL(context, s, null, idTemplate);
    }

    @Replacement(
            type = ReplacementType.EXCEPTION,
            category = ReplacementCategory.EXT_0,
            replacingConstructor = true
    )
    public static void URL(URL context, String s, URLStreamHandler handler, String idTemplate) throws MalformedURLException {

        if (ExecutionTracer.isTaintInput(s)) {
            ExecutionTracer.addStringSpecialization(s,
                    new StringSpecializationInfo(StringSpecialization.URL, null));
        }

        URL url;

        if (idTemplate == null) {
            url =  new java.net.URL(context,s,handler);
        } else {

            try {
                URL res = new java.net.URL(context,s,handler);
                ExecutionTracer.executedReplacedMethod(idTemplate, ReplacementType.EXCEPTION,
                        new Truthness(1, DistanceHelper.H_NOT_NULL));
                url = res;
            } catch (RuntimeException e) {
                double h = s == null ? DistanceHelper.H_REACHED_BUT_NULL : DistanceHelper.H_NOT_NULL;
                ExecutionTracer.executedReplacedMethod(idTemplate, ReplacementType.EXCEPTION, new Truthness(h, 1));
                throw e;
            }
        }

        addInstance(url);
    }


    @Replacement(
            type = ReplacementType.TRACKER,
            category = ReplacementCategory.NET,
            id = "URL_openConnection_Replacement",
            replacingStatic = false,
            usageFilter = UsageFilter.ANY
    )
    public static URLConnection openConnection(URL caller) throws java.io.IOException {
        Objects.requireNonNull(caller);

        URL newURL = getReplacedURL(caller);
        if (newURL!=null)
            return newURL.openConnection();

        if (!caller.getProtocol().equals("jar") && !caller.getProtocol().equals("file"))
            SimpleLogger.uniqueWarn("not handle the protocol with:"+caller.getProtocol());
        return caller.openConnection();
    }


    @Replacement(
            type = ReplacementType.TRACKER,
            category = ReplacementCategory.NET,
            id = "URL_openConnection_proxy_Replacement",
            replacingStatic = false,
            usageFilter = UsageFilter.ANY
    )
    public static URLConnection openConnection(URL caller, Proxy proxy) throws java.io.IOException {
        Objects.requireNonNull(caller);

        URL newURL = getReplacedURL(caller);
        if (newURL!=null)
            return newURL.openConnection(proxy);

        if (!caller.getProtocol().equals("jar") && !caller.getProtocol().equals("file"))
            SimpleLogger.uniqueWarn("not handle the protocol with:"+caller.getProtocol());
        return caller.openConnection(proxy);
    }

    private static URL getReplacedURL(URL caller) throws java.io.IOException {
        if (MethodReplacementPreserveSemantics.shouldPreserveSemantics) {
            return null;
        }
        String protocol = caller.getProtocol();

        int port = caller.getPort();
        port = ExternalServiceInfoUtils.inferPort(port, protocol);

        if ((caller.getProtocol().equals("http") || caller.getProtocol().equals("https"))
                && !skipHostnameOrIp(caller.getHost())
                && !ExecutionTracer.skipHostnameAndPort(caller.getHost(), port))
        {

            ExternalServiceInfoUtils.analyzeDnsResolution(caller.getHost());

            if (caller.getProtocol().equalsIgnoreCase("https"))
                PreDefinedSSLInfo.setTrustAllForHttpsURLConnection();

            ExternalServiceInfo remoteHostInfo = new ExternalServiceInfo(protocol, caller.getHost(), port);
            String[] ipAndPort = collectExternalServiceInfo(remoteHostInfo, port);

            // Usage of ports below 1024 require root privileges to run
            String url = caller.getProtocol()+"://" + ipAndPort[0]+":"+ipAndPort[1] + caller.getPath();

            if (caller.getQuery() != null)
                url = url + "?" + caller.getQuery();

            return new URL(url);
        }
        return null;
    }
}
