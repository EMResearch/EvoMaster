package com.foo.rest.examples.spring.authenticatedswaggeraccess;

import com.foo.rest.examples.spring.security.accesscontrol.deleteput.ACDeletePutDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/api")
public class AuthenticatedSwaggerAccessRest {




    @GetMapping(value = "/endpoint1")
    public ResponseEntity getResourceEnd1() {

        return new ResponseEntity<>(HttpStatus.OK);

    }

    @GetMapping(value = "/endpoint2")
    public ResponseEntity getResourceEnd2() {

        return new ResponseEntity<>(HttpStatus.OK);

    }
}