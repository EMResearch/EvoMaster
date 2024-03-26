package com.foo.rest.examples.spring.authenticatedswaggeraccessnoauth;


import com.foo.rest.examples.spring.SwaggerConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@EnableSwagger2
@SpringBootApplication
public class AuthenticatedSwaggerAccessNoAuthApplication extends SwaggerConfiguration {

    public static void main(String[] args) {

        SpringApplication.run(AuthenticatedSwaggerAccessNoAuthApplication.class, args);
    }
}
