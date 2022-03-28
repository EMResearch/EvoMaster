package com.foo.rpc.examples.spring.hypermutation;

import com.foo.rpc.examples.spring.SpringController;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.THttpClient;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.evomaster.client.java.controller.problem.ProblemInfo;
import org.evomaster.client.java.controller.problem.RPCProblem;

import java.util.HashMap;

public class HypermutationController extends SpringController {

    private HypermutationService.Client client;

    public HypermutationController(){
        super(HypermutationApp.class);
    }

    @Override
    public ProblemInfo getProblemInfo() {
        return new RPCProblem(new HashMap<String, Object>() {{
            put(HypermutationService.Iface.class.getName(), client);
        }});
    }

    @Override
    public String startClient() {
        String url = "http://localhost:"+getSutPort()+"/hypermutation";
        try {
            // init client
            TTransport transport = new THttpClient(url);
            TProtocol protocol = new TBinaryProtocol(transport);
            client = new HypermutationService.Client(protocol);
        } catch (TTransportException e) {
            e.printStackTrace();
        }

        return url;
    }
}

