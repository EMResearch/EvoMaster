package com.foo.rest.examples.spring.unauthenticatedswaggeraccess;


import com.foo.rest.examples.spring.SwaggerConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@EnableSwagger2
@SpringBootApplication
public class UnauthenticatedSwaggerAccessApplication extends SwaggerConfiguration {

    public static void main(String[] args) {

        SpringApplication.run(UnauthenticatedSwaggerAccessApplication.class, args);
    }
}
