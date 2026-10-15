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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pruebas de integracion del login provisional de aspirantes (ficha + CURP).
 * Los datos usados son ficticios del seed de desarrollo; no son datos reales.
 *
 * Escenarios de base de datos que no pueden crearse desde el backend (la
 * cuenta tecnica no tiene INSERT) se cubren en
 * {@code database/tests/07-probar-aspirantes.sql}: aspirante de anio anterior
 * y coincidencia ambigua entre periodos.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApplicantAuthenticationIntegrationTest {

    private static final String CSRF_HEADER = "X-CSRF-TOKEN";

    // Aspirante ficticio del seed, periodo 2.
    private static final String FICHA_PERIODO_2 = "F2026A001";
    private static final String CURP_PERIODO_2 = "MOPA080512MDFRNN01";
    // Aspirante ficticio del seed, periodo 1.
    private static final String FICHA_PERIODO_1 = "F2026B004";
    private static final String CURP_PERIODO_1 = "FULD080617HDFRRG04";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void applicantOfPeriodTwoCanLoginInspectSessionAndLogout() throws Exception {
        exerciseLogin(FICHA_PERIODO_2, CURP_PERIODO_2, 2);
    }

    @Test
    void applicantOfPeriodOneCanLogin() throws Exception {
        exerciseLogin(FICHA_PERIODO_1, CURP_PERIODO_1, 1);
    }

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void wrongFichaIsRejected() throws Exception {
        assertLoginRejected("FNOEXISTE9", CURP_PERIODO_2);
    }

    @Test
    void wrongCurpIsRejected() throws Exception {
        assertLoginRejected(FICHA_PERIODO_2, "CURPNOEXISTE000001");
    }

    @Test
    void malformedCurpIsRejected() throws Exception {
        assertLoginRejected(FICHA_PERIODO_2, "CORTA");
    }

    @Test
    void missingCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/api/auth/login/aspirante")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(FICHA_PERIODO_2, CURP_PERIODO_2)))
                .andExpect(status().isForbidden());
    }

    @Test
    void failedLoginDoesNotKeepPreviousIdentity() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String csrf = csrfFor(session);
        mockMvc.perform(post("/api/auth/login/aspirante")
                .session(session)
                .header(CSRF_HEADER, csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(FICHA_PERIODO_2, CURP_PERIODO_2)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login/aspirante")
                .session(session)
                .header(CSRF_HEADER, csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(FICHA_PERIODO_2, "CURPINCORRECTA0001")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void repeatedFailuresAreRateLimited() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String csrf = csrfFor(session);
        String ficha = "RATELIM01";
        byte[] rejected = body(ficha, "CURPINCORRECTA00002");
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/auth/login/aspirante")
                    .session(session)
                    .header(CSRF_HEADER, csrf)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(rejected))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/login/aspirante")
                .session(session)
                .header(CSRF_HEADER, csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(rejected))
                .andExpect(status().isTooManyRequests());
    }

    private void exerciseLogin(String ficha, String curp, int expectedPeriodo) throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult meBefore = mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized())
                .andReturn();
        String originalSessionId = session.getId();
        String csrf = readCsrf(meBefore);

        MvcResult login = mockMvc.perform(post("/api/auth/login/aspirante")
                .session(session)
                .header(CSRF_HEADER, csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(ficha, curp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.role").value("APPLICANT"))
                .andExpect(jsonPath("$.identity").value(ficha))
                .andExpect(jsonPath("$.periodo").value(expectedPeriodo))
                .andExpect(jsonPath("$.anio").isNumber())
                .andExpect(jsonPath("$.curp").doesNotExist())
                .andReturn();

        assertNotNull(login.getRequest().getSession(false));
        assertNotEquals(originalSessionId, login.getRequest().getSession(false).getId());
        for (String attribute : Collections.list(session.getAttributeNames())) {
            String lower = attribute.toLowerCase();
            assertFalse(lower.contains("password"));
            assertFalse(lower.contains("curp"));
            assertFalse(lower.contains("correo"));
        }

        String authenticatedCsrf = readCsrf(login);
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.role").value("APPLICANT"))
                .andExpect(jsonPath("$.identity").value(ficha))
                .andExpect(jsonPath("$.periodo").value(expectedPeriodo));

        mockMvc.perform(post("/api/auth/logout")
                .session(session)
                .header(CSRF_HEADER, authenticatedCsrf))
                .andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
    }

    private void assertLoginRejected(String ficha, String curp) throws Exception {
        MockHttpSession session = new MockHttpSession();
        String csrf = csrfFor(session);
        mockMvc.perform(post("/api/auth/login/aspirante")
                .session(session)
                .header(CSRF_HEADER, csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(ficha, curp)))
                .andExpect(status().isUnauthorized());
    }

    private String csrfFor(MockHttpSession session) throws Exception {
        MvcResult me = mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized())
                .andReturn();
        return readCsrf(me);
    }

    private String readCsrf(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return body.get("csrfToken").asText();
    }

    private byte[] body(String ficha, String curp) throws Exception {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("ficha", ficha);
        node.put("curp", curp);
        return objectMapper.writeValueAsBytes(node);
    }
}
