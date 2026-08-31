package com.github.stimur1709.cloudops;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public final class TestAuthentication {

    public static final long USER_ID = 10_000L;

    private TestAuthentication() {}

    public static MockMvc authenticatedMockMvc(WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").with(jwt().jwt(token -> token.subject(Long.toString(USER_ID)))))
                .build();
    }
}
