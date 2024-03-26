package com.foo.rest.examples.spring.authenticatedswaggeraccess;

import com.foo.rest.examples.spring.SpringController;

import org.evomaster.client.java.controller.AuthUtils;
import org.evomaster.client.java.controller.InstrumentedSutStarter;
import org.evomaster.client.java.controller.api.dto.auth.AuthenticationDto;

import java.util.Arrays;
import java.util.List;

public class AuthenticatedSwaggerAccessController extends SpringController {

    public AuthenticatedSwaggerAccessController() {
        super(AuthenticatedSwaggerAccessApplication.class);
    }


    public static void main(String[] args){
        AuthenticatedSwaggerAccessController controller = new AuthenticatedSwaggerAccessController();
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
