package org.evomaster.client.java.instrumentation.coverage.methodreplacement.classes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

class SocketClassReplacementTest {


    @Test
    @Timeout(5)
    public void testSlowOnWindows(){
        String ip = "192.168.2.3";

        for(int i=0; i<1000; i++) {
            try {
                InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(ip),80);
                SocketClassReplacement.connect(null,address, 10);
            } catch (IOException | NullPointerException e) {
            }
            if(Thread.interrupted()){
                fail();
            }
        }

    }


    //@Timeout(5)
    //@Test // no need to have this as a test, it is just a check to study behavior of getHostName
    public void testGetHostName() throws Exception{
        /*
            should never call socketAddress.getHostName() in EM as it can take forever
            on Windows when dealing non-existent hostnames in a reversed-lookup
         */

        String ip = "127.1.2.3";
        // here would be fine, because cached in the 'address' object
        //InetAddress address = InetAddress.getByName(ip);

        for(int i=0; i<1000; i++) {
            try {
                //this will take forever, as address object is not reused
                InetAddress address = InetAddress.getByName(ip);
                address.getHostName();
            } catch (Exception e) {
            }
        }
    }


    @Timeout(5)
    @Test
    public void testGetByName() throws Exception{

        String fake = "www.reallyIdonotexistasahostname23423523.com";

        for(int i=0; i<1000; i++) {
            try {
                InetAddress.getByName(fake);
                /*
                    either fast, or cached. so should take only few milliseconds
                 */
            } catch (Exception e) {
            }
        }
    }
}