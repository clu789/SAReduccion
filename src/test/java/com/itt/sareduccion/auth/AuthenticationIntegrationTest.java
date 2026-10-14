package com.itt.sareduccion.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationIntegrationTest {

    private static final String CSRF_HEADER = "X-CSRF-TOKEN";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void studentCanLoginInspectSessionAndLogout() throws Exception {
        String password = requiredTestPassword("SAR_AUTH_STUDENT_PASSWORD");
        exerciseSession("ALU_21281181", password, "21281181", "STUDENT");
    }

    @Test
    void administratorCanLoginInspectSessionAndLogout() throws Exception {
        String password = requiredTestPassword("SAR_AUTH_ADMIN_PASSWORD");
        exerciseSession("SA01", password, "SA01", "ADMIN");
    }

    private void exerciseSession(String username, String password, String identity, String role) throws Exception {
        MvcResult meBeforeLogin = mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andReturn();

        MockHttpSession anonymousSession = (MockHttpSession) meBeforeLogin.getRequest().getSession(false);
        assertNotNull(anonymousSession);
        String originalSessionId = anonymousSession.getId();
        String csrfToken = readCsrfToken(meBeforeLogin);
        ObjectNode loginBody = objectMapper.createObjectNode();
        loginBody.put("username", username);
        loginBody.put("password", password);

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                .session(anonymousSession)
                .header(CSRF_HEADER, csrfToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsBytes(loginBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.identity").value(identity))
                .andExpect(jsonPath("$.role").value(role))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        MockHttpSession authenticatedSession = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(authenticatedSession);
        assertNotEquals(originalSessionId, authenticatedSession.getId());
        for (String attributeName : Collections.list(authenticatedSession.getAttributeNames())) {
            assertFalse(attributeName.toLowerCase().contains("password"));
        }

        String authenticatedCsrf = readCsrfToken(login);
        mockMvc.perform(get("/api/auth/me").session(authenticatedSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.identity").value(identity))
                .andExpect(jsonPath("$.role").value(role));

        mockMvc.perform(post("/api/auth/logout")
                .session(authenticatedSession)
                .header(CSRF_HEADER, authenticatedCsrf))
                .andExpect(status().isNoContent());
        assertTrue(authenticatedSession.isInvalid());
    }

    private String readCsrfToken(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return body.get("csrfToken").asText();
    }

    private String requiredTestPassword(String variableName) {
        String password = System.getenv(variableName);
        Assumptions.assumeTrue(password != null && !password.isEmpty(),
                "La prueba requiere la credencial configurada externamente");
        return password;
    }

}