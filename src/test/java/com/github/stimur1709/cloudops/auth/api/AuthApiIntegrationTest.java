package com.github.stimur1709.cloudops.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.github.stimur1709.cloudops.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AuthApiIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery-staple";

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        jdbcTemplate.execute("""
                TRUNCATE TABLE monitoring_results, monitors, outbox_messages, tasks, organization_memberships, resources, users, organizations RESTART IDENTITY
                """);
    }

    @Test
    void registrationNormalizesEmailHashesPasswordAndNeverReturnsCredentials() throws Exception {
        register("  Alice@Example.COM  ", "Alice", " 12345678901")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/users/1"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String hash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE id = 1", String.class
        );
        assertThat(hash).isNotBlank().isNotEqualTo(" 12345678901").startsWith("{bcrypt}");

        login("alice@example.com", " 12345678901").andExpect(status().isOk());
        login("alice@example.com", "12345678901").andExpect(status().isUnauthorized());
    }

    @Test
    void registrationValidatesPasswordAndRejectsDuplicateNormalizedEmail() throws Exception {
        register("alice@example.com", "Alice", PASSWORD).andExpect(status().isCreated());
        register(" ALICE@EXAMPLE.COM ", "Other", PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_EMAIL_CONFLICT"));
        register("short@example.com", "Short", "too-short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
        register("long@example.com", "Long", "x".repeat(73))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginReturnsVerifiableJwtWithRequiredClaimsAndNoRoles() throws Exception {
        long userId = registerAndGetId("user@example.com", "User");
        String body = login(" USER@EXAMPLE.COM ", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse().getContentAsString();

        Jwt jwt = jwtDecoder.decode(JsonPath.read(body, "$.accessToken"));
        assertThat(jwt.getSubject()).isEqualTo(Long.toString(userId));
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("cloudops-test");
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getClaims()).doesNotContainKeys("role", "roles", "memberships", "passwordHash");
    }

    @Test
    void unknownEmailAndWrongPasswordReturnSameSafeUnauthorizedError() throws Exception {
        registerAndGetId("user@example.com", "User");
        String unknown = login("unknown@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String wrong = login("user@example.com", "wrong-password-value")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.read(unknown, "$.code").toString())
                .isEqualTo(JsonPath.read(wrong, "$.code").toString())
                .isEqualTo("UNAUTHORIZED");
        assertThat(JsonPath.read(unknown, "$.message").toString())
                .isEqualTo(JsonPath.read(wrong, "$.message").toString());
    }

    @Test
    void meUsesBearerTokenAndProtectedRequestsDoNotCreateSession() throws Exception {
        registerAndGetId("user@example.com", "User");
        String token = loginToken("user@example.com", PASSWORD);

        MvcResult result = mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void missingMalformedExpiredAndWrongIssuerTokensReturnCommonUnauthorizedError() throws Exception {
        registerAndGetId("user@example.com", "User");
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer damaged"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors").isEmpty());

        Instant now = Instant.now();
        String expired = token("cloudops-test", now.minusSeconds(120), now.minusSeconds(60));
        String wrongIssuer = token("another-issuer", now, now.plusSeconds(300));
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(expired)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(wrongIssuer)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyRegisterAndLoginArePublicAndPublicUserCreationIsRemoved() throws Exception {
        register("public@example.com", "Public", PASSWORD).andExpect(status().isCreated());
        login("public@example.com", PASSWORD).andExpect(status().isOk());
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"other@example.com\",\"displayName\":\"Other\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/organizations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Secret\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userApiAllowsOnlySelfAndSearchRequiresOrganizationManagementRole() throws Exception {
        long ownerId = registerAndGetId("owner@example.com", "Owner");
        long otherId = registerAndGetId("other@example.com", "Other");
        String ownerToken = loginToken("owner@example.com", PASSWORD);
        String otherToken = loginToken("other@example.com", PASSWORD);
        String originalHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, ownerId
        );

        mockMvc.perform(get("/api/users/{id}", otherId).header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/users/{id}", ownerId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"OWNER2@example.com\",\"displayName\":\"Owner 2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("owner2@example.com"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, ownerId
        )).isEqualTo(originalHash);

        String search = "{\"start\":0,\"size\":10,\"getTotal\":true}";
        mockMvc.perform(post("/api/users/search").header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON).content(search))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/organizations").header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Platform\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/users/search").header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(search))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void creatingOrganizationAtomicallyAddsCreatorAsOwner() throws Exception {
        long userId = registerAndGetId("owner@example.com", "Owner");
        String token = loginToken("owner@example.com", PASSWORD);
        String body = mockMvc.perform(post("/api/organizations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Platform\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long organizationId = ((Number) JsonPath.read(body, "$.id")).longValue();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT role FROM organization_memberships
                WHERE organization_id = ? AND user_id = ?
                """, String.class, organizationId, userId)).isEqualTo("OWNER");
    }

    private ResultActions register(String email, String displayName, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","displayName":"%s","password":"%s"}
                        """.formatted(email, displayName, password)));
    }

    private long registerAndGetId(String email, String displayName) throws Exception {
        String body = register(email, displayName, PASSWORD)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private String loginToken(String email, String password) throws Exception {
        String body = login(email, password).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private String token(String issuer, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("1")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id("test-token")
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims
        )).getTokenValue();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
