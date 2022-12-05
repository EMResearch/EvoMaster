package org.evomaster.client.java.instrumentation.coverage.methodreplacement.classes;

import org.evomaster.client.java.instrumentation.ExternalServiceInfo;
import org.evomaster.client.java.instrumentation.coverage.methodreplacement.ExternalServiceInfoUtils;
import org.evomaster.client.java.instrumentation.coverage.methodreplacement.MethodReplacementClass;
import org.evomaster.client.java.instrumentation.coverage.methodreplacement.Replacement;
import org.evomaster.client.java.instrumentation.coverage.methodreplacement.UsageFilter;
import org.evomaster.client.java.instrumentation.shared.ReplacementCategory;
import org.evomaster.client.java.instrumentation.shared.ReplacementType;
import org.evomaster.client.java.instrumentation.staticstate.ExecutionTracer;

import java.net.*;

/**
 * with socket connection, it will first do host lookup with java.net.InetAddress#getByName
 * if it does not exist, UnknownHostException will be thrown,
 * then the `socket.connect` cannot be reached.
 * in order to make it connected, we could do replacement for
 * 1) collecting host info
 * 2) providing an ip address
 * note that it is not used now, ie, not register it into ReplacementList
 */
public class InetAddressClassReplacement implements MethodReplacementClass {
    @Override
    public Class<?> getTargetClass() {
        return InetAddress.class;
    }

    @Replacement(
            type = ReplacementType.TRACKER,
            category = ReplacementCategory.NET,
            replacingStatic = true,
            usageFilter = UsageFilter.ANY
    )
    public static InetAddress getByName(String host) throws UnknownHostException {
        if (ExternalServiceInfoUtils.skipHostnameOrIp(host))
            return InetAddress.getByName(host);

        ExternalServiceInfo remoteHostInfo = new ExternalServiceInfo("TCP", host, -1);
        try {
            if (ExecutionTracer.hasExternalMapping(remoteHostInfo.signature())) {
                String ip = ExecutionTracer.getExternalMapping(remoteHostInfo.signature());
                return InetAddress.getByName(ip);
            } else {
                ExecutionTracer.addExternalServiceHost(remoteHostInfo);
            }
            return InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            ExecutionTracer.addExternalServiceHost(remoteHostInfo);
            throw e;
        }
    }

    @Replacement(
            type = ReplacementType.TRACKER,
            category = ReplacementCategory.NET,
            replacingStatic = true,
            usageFilter = UsageFilter.ANY
    )
    public static InetAddress[] getAllByName(String host) throws UnknownHostException {
        if (ExternalServiceInfoUtils.skipHostnameOrIp(host))
            return InetAddress.getAllByName(host);
        ExternalServiceInfo remoteHostInfo = new ExternalServiceInfo("TCP", host, -1);
        try {
            if (ExecutionTracer.hasExternalMapping(remoteHostInfo.signature())) {
                String ip = ExecutionTracer.getExternalMapping(remoteHostInfo.signature());
                return new InetAddress[]{InetAddress.getByName(ip)};
            } else {
                ExecutionTracer.addExternalServiceHost(remoteHostInfo);
            }
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            ExecutionTracer.addExternalServiceHost(remoteHostInfo);
            throw e;
        }
    }
}
