package org.evomaster.client.java.instrumentation.coverage.methodreplacement.classes;

import org.evomaster.client.java.instrumentation.HostnameResolutionInfo;
import org.evomaster.client.java.instrumentation.coverage.methodreplacement.ExternalServiceInfoUtils;
import org.evomaster.client.java.instrumentation.coverage.methodreplacement.MethodReplacementClass;
import org.evomaster.client.java.instrumentation.coverage.methodreplacement.Replacement;
import org.evomaster.client.java.instrumentation.coverage.methodreplacement.UsageFilter;
import org.evomaster.client.java.instrumentation.shared.ExternalServiceSharedUtils;
import org.evomaster.client.java.instrumentation.shared.ReplacementCategory;
import org.evomaster.client.java.instrumentation.shared.ReplacementType;
import org.evomaster.client.java.instrumentation.staticstate.ExecutionTracer;
import org.evomaster.client.java.instrumentation.staticstate.MethodReplacementPreserveSemantics;

import java.net.*;

/**
 * InetAddress could be called directly, or indirectly from other JDK classes like Socket and URl.openConnection.
 * Recall that we do not instrument JDK classes, as those loaded by bootstrap classloader.
 * <p>
 * with socket connection, it will first do host lookup with java.net.InetAddress#getByName
 * if it does not exist, UnknownHostException will be thrown,
 * then the `socket.connect` cannot be reached.
 * in order to make it connected, we could do replacement for
 * 1) collecting host info
 * 2) providing an ip address
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
        return getAllByName(host)[0];
    }

    @Replacement(
            type = ReplacementType.TRACKER,
            category = ReplacementCategory.NET,
            replacingStatic = true,
            usageFilter = UsageFilter.ANY
    )
    public static InetAddress[] getAllByName(String host) throws UnknownHostException {
        if (
                host == null
                        || host.isEmpty()
                        || ExternalServiceInfoUtils.isValidIP(host)
                        || ExecutionTracer.skipHostname(host)
                        || "localhost".equals(host)
                        || MethodReplacementPreserveSemantics.shouldPreserveSemantics

        ) {
            //we are only interested in hostnames... recall user could manually specify some to skip.
            //we are never going to modify localhost though
            return InetAddress.getAllByName(host);
        }

        if (ExecutionTracer.hasLocalAddressForHost(host)) {
            String ip = ExecutionTracer.getLocalAddress(host);
            return InetAddress.getAllByName(ip);
        }

        try {
            InetAddress[] inetAddress = InetAddress.getAllByName(host);
            // hostname is resolved
            ExecutionTracer.addHostnameInfo(new HostnameResolutionInfo(host, inetAddress[0].getHostAddress()));

           /*
              if the real hostname does resolve, we cannot simulate it not resolving, because that will not work
              in the generated JUnit test cases.
              But we do not want to speak with the real service. so we default to an IP address we make sure nothing
              is up and running.

              However, we need to make sure to tell the "core" of this situation, as it will need to setup this
              mapping explicitly.
              TODO this can be done in core directly, by checking presence of resolved HostnameResolutionInfo
            */
            return InetAddress.getAllByName(ExternalServiceSharedUtils.RESERVED_RESOLVED_LOCAL_IP);
        } catch (UnknownHostException e) {
            //hostname is not resolved
            ExecutionTracer.addHostnameInfo(new HostnameResolutionInfo(host, null));
            throw e;
        }
    }
}
