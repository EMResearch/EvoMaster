package com.foo.rest.examples.spring.authenticatedswaggeraccessnoauth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
@EnableWebSecurity
public class AuthenticatedSwaggerApplicationNoAuthWebSecurityConfig extends WebSecurityConfigurerAdapter {

    // security configuration
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()
                //  .antMatchers("/v2/api-docs").permitAll()
                .antMatchers("/v2/api-docs").hasRole("AUTH_USER")
                .antMatchers(HttpMethod.GET, "/endpoint1").permitAll()
                .antMatchers(HttpMethod.GET, "/endpoint2").hasAnyRole("AUTH_USER")
                .anyRequest().authenticated()
                .and()
                .httpBasic()
                .and()
                .csrf().disable();
    }

    @Bean
    @Override
    public UserDetailsService userDetailsService() {


        UserDetails creator1 =
                User.withDefaultPasswordEncoder()
                        .username("other")
                        .password("other_password")
                        .roles("OTHER_USER")
                        .build();

        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();

        manager.createUser(creator1);

        return manager;
    }
}

