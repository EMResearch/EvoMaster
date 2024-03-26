package com.foo.rest.examples.spring.authenticatedswaggeraccessnoauth;

import com.foo.rest.examples.spring.SpringController;
import com.foo.rest.examples.spring.authenticatedswaggeraccess.AuthenticatedSwaggerAccessApplication;
import org.evomaster.client.java.controller.AuthUtils;
import org.evomaster.client.java.controller.InstrumentedSutStarter;
import org.evomaster.client.java.controller.api.dto.auth.AuthenticationDto;

import java.util.Arrays;
import java.util.List;

public class AuthenticatedSwaggerAccessNoAuthController extends SpringController {

    public AuthenticatedSwaggerAccessNoAuthController() {
        super(AuthenticatedSwaggerAccessApplication.class);
    }


    public static void main(String[] args){
        AuthenticatedSwaggerAccessNoAuthController controller = new AuthenticatedSwaggerAccessNoAuthController();
        controller.setControllerPort(40100);
        InstrumentedSutStarter starter = new InstrumentedSutStarter(controller);
        starter.start();
    }


    @Override
    public List<AuthenticationDto> getInfoForAuthentication() {
        return Arrays.asList(
                AuthUtils.getForBasic("other_user", "other", "other_password")
        );
    }
}
