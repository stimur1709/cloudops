package com.github.stimur1709.cloudops.openapi;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.stimur1709.cloudops.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "springdoc.api-docs.enabled=false")
class OpenApiDisabledIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    void aSinglePropertyDisablesJsonAndSwaggerUi() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        for (String path : new String[] {
            "/v3/api-docs",
            "/v3/api-docs.yaml",
            "/v3/api-docs/swagger-config",
            "/swagger-ui.html",
            "/swagger-ui/index.html"
        }) {
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/resources/1")).andExpect(status().isUnauthorized());
    }
}
