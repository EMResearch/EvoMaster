package com.foo.rest.examples.spring.authenticatedswaggeraccess;


import com.foo.rest.examples.spring.SwaggerConfiguration;
import com.foo.rest.examples.spring.bodytypes.BodyTypesApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@EnableSwagger2
@SpringBootApplication
public class AuthenticatedSwaggerAccessApplication extends SwaggerConfiguration {

    public static void main(String[] args) {

        SpringApplication.run(AuthenticatedSwaggerAccessApplication.class, args);
    }
}
