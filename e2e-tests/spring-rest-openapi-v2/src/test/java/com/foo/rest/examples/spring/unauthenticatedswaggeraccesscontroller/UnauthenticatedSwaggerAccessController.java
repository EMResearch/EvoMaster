package com.foo.rest.examples.spring.unauthenticatedswaggeraccesscontroller;

import com.foo.rest.examples.spring.SpringController;
import com.foo.rest.examples.spring.authenticatedswaggeraccess.AuthenticatedSwaggerAccessApplication;
import com.foo.rest.examples.spring.authenticatedswaggeraccess.AuthenticatedSwaggerAccessController;
import com.foo.rest.examples.spring.unauthenticatedswaggeraccess.UnauthenticatedSwaggerAccessApplication;
import org.evomaster.client.java.controller.AuthUtils;
import org.evomaster.client.java.controller.InstrumentedSutStarter;
import org.evomaster.client.java.controller.api.dto.auth.AuthenticationDto;

import java.util.Arrays;
import java.util.List;

public class UnauthenticatedSwaggerAccessController extends SpringController {

    public UnauthenticatedSwaggerAccessController() {
        super(UnauthenticatedSwaggerAccessApplication.class);
    }


    public static void main(String[] args){
        UnauthenticatedSwaggerAccessController controller = new UnauthenticatedSwaggerAccessController();
        controller.setControllerPort(40100);
        InstrumentedSutStarter starter = new InstrumentedSutStarter(controller);
        starter.start();
    }


    @Override
    public List<AuthenticationDto> getInfoForAuthentication() {
        return Arrays.asList(
                AuthUtils.getForBasic("auth_user", "authenticated", "authenticated_password"),
                AuthUtils.getForBasic("other_user", "other", "other_password")
        );
    }
}
