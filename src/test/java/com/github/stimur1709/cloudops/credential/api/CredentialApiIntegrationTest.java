package com.github.stimur1709.cloudops.credential.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.stimur1709.cloudops.TestAuthentication;
import com.github.stimur1709.cloudops.TestcontainersConfiguration;
import com.github.stimur1709.cloudops.SqlStatementRecorder;
import com.github.stimur1709.cloudops.credential.application.CredentialResolver;
import com.github.stimur1709.cloudops.credential.application.ResolvedSshPrivateKey;
import com.github.stimur1709.cloudops.credential.CredentialPurpose;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CredentialApiIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired CredentialResolver resolver;
    @Autowired SqlStatementRecorder statementRecorder;
    private MockMvc mockMvc;
    private long organizationId;
    private long resourceId;

    @BeforeEach
    void setUp() {
        mockMvc = TestAuthentication.authenticatedMockMvc(context);
        jdbc.execute("TRUNCATE TABLE resource_credentials, credentials, resource_probe_settings, organization_probe_settings, monitoring_results, monitors, resource_health_events, resource_health, outbox_messages, tasks, organization_memberships, resources, users, organizations RESTART IDENTITY");
        jdbc.update("INSERT INTO users (id,email,display_name,password_hash,created_at,updated_at) VALUES (?, 'owner@example.com','Owner','x',now(),now())", TestAuthentication.USER_ID);
        organizationId = jdbc.queryForObject("INSERT INTO organizations (name,created_at,updated_at) VALUES ('Org',now(),now()) RETURNING id", Long.class);
        jdbc.update("INSERT INTO organization_memberships (organization_id,user_id,role,created_at,updated_at) VALUES (?,?,'OWNER',now(),now())", organizationId, TestAuthentication.USER_ID);
        resourceId = jdbc.queryForObject("INSERT INTO resources (name,type,status,organization_id,config,created_at,updated_at) VALUES ('server','SERVER','ACTIVE',?,'{}',now(),now()) RETURNING id", Long.class, organizationId);
    }

    @AfterEach
    void cleanCredentials() {
        jdbc.execute("TRUNCATE TABLE resource_credentials, credentials RESTART IDENTITY");
    }

    @Test
    void crudNeverReturnsSecretAndStoresOnlyCiphertext() throws Exception {
        long id = create("db-login", "USERNAME_PASSWORD", "password", "initial-secret");
        String stored = jdbc.queryForObject("select secret_encrypted from credentials where id=?", String.class, id);
        assertThat(stored).doesNotContain("initial-secret");

        mockMvc.perform(get("/api/credentials/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.privateKey").doesNotExist())
                .andExpect(jsonPath("$.secretEncrypted").doesNotExist());
        mockMvc.perform(post("/api/credentials/search").contentType(MediaType.APPLICATION_JSON)
                .content("{\"start\":0,\"size\":20,\"getTotal\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].name").value("db-login"))
                .andExpect(jsonPath("$.items[0].secretEncrypted").doesNotExist());

        mockMvc.perform(put("/api/credentials/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(request("db-login", "USERNAME_PASSWORD", "password", "replacement-secret")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.password").doesNotExist());
        assertThat(jdbc.queryForObject("select secret_encrypted from credentials where id=?", String.class, id))
                .isNotEqualTo(stored).doesNotContain("replacement-secret");

        mockMvc.perform(delete("/api/credentials/{id}", id)).andExpect(status().isNoContent());
    }

    @Test
    void bindsCompatibleCredentialAndBlocksDeletingIt() throws Exception {
        long id = create("ssh-key", "SSH_PRIVATE_KEY", "privateKey", "-----BEGIN KEY-----");
        mockMvc.perform(put("/api/resources/{id}/credentials/SSH", resourceId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"credentialId\":" + id + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.purpose").value("SSH"))
                .andExpect(jsonPath("$.credential.id").value(id))
                .andExpect(jsonPath("$.credential.privateKey").doesNotExist());
        mockMvc.perform(delete("/api/credentials/{id}", id)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREDENTIAL_IN_USE"));
        assertThat(resolver.resolve(resourceId, CredentialPurpose.SSH))
                .isEqualTo(new ResolvedSshPrivateKey("cloudops", "-----BEGIN KEY-----"));
        mockMvc.perform(delete("/api/resources/{id}/credentials/SSH", resourceId))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectsPrivateKeyForDatabaseAndDuplicateName() throws Exception {
        long id = create("shared", "SSH_PRIVATE_KEY", "privateKey", "key");
        mockMvc.perform(post("/api/organizations/{id}/credentials", organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("shared", "USERNAME_PASSWORD", "password", "pw")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CREDENTIAL_NAME_CONFLICT"));
        mockMvc.perform(put("/api/resources/{id}/credentials/DATABASE", resourceId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"credentialId\":" + id + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INCOMPATIBLE_CREDENTIAL"));
    }

    @Test
    void getsAllCredentialMetadataWithoutQueriesInLoop() throws Exception {
        long databaseCredentialId = create("database", "USERNAME_PASSWORD", "password", "password");
        long sshCredentialId = create("ssh", "SSH_PRIVATE_KEY", "privateKey", "private-key");
        bind("DATABASE", databaseCredentialId);
        bind("SSH", sshCredentialId);
        statementRecorder.clear();

        mockMvc.perform(get("/api/resources/{id}/credentials", resourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        var relevantSelects = statementRecorder.statements().stream()
                .map(String::toLowerCase)
                .filter(sql -> sql.startsWith("select"))
                .filter(sql -> sql.contains(" from resources ")
                        || sql.contains(" from organization_memberships ")
                        || sql.contains(" from resource_credentials "))
                .toList();
        assertThat(relevantSelects).hasSize(3);
        assertThat(relevantSelects.stream().filter(sql -> sql.contains(" join credentials "))).hasSize(1);
        assertThat(relevantSelects.stream().filter(sql -> sql.contains(" from credentials "))).isEmpty();
    }

    private long create(String name, String type, String secretField, String secret) throws Exception {
        String response = mockMvc.perform(post("/api/organizations/{id}/credentials", organizationId)
                        .contentType(MediaType.APPLICATION_JSON).content(request(name, type, secretField, secret)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$." + secretField).doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private void bind(String purpose, long credentialId) throws Exception {
        mockMvc.perform(put("/api/resources/{id}/credentials/{purpose}", resourceId, purpose)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credentialId\":" + credentialId + "}"))
                .andExpect(status().isOk());
    }

    private String request(String name, String type, String secretField, String secret) {
        return "{\"name\":\"" + name + "\",\"type\":\"" + type + "\",\"username\":\"cloudops\",\"" + secretField + "\":\"" + secret + "\"}";
    }
}
