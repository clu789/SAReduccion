package com.itt.sareduccion.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.itt.sareduccion.auth.AuthRole;
import com.itt.sareduccion.auth.AuthenticatedPrincipal;
import com.itt.sareduccion.auth.SessionAttributes;
import com.itt.sareduccion.security.GlobalExceptionHandler;
import com.itt.sareduccion.security.SecurityAuditService;
import com.itt.sareduccion.security.WebSecurityConfig;
import com.itt.sareduccion.service.ConvocatoriaGestionService;
import com.itt.sareduccion.service.ReglaNegocioException;

class ConvocatoriaGestionControllerTest {

    private ConvocatoriaGestionService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ConvocatoriaGestionService.class);
        WebSecurityConfig config = new WebSecurityConfig(mock(SecurityAuditService.class), "",
                "/solicitante/login.html", "/administrativo/login.html");
        mockMvc = MockMvcBuilders.standaloneSetup(new ConvocatoriaGestionController(service))
                .addInterceptors(config.authorizationInterceptor(config.roleRules()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void exigeRolAdminYCsrfParaGestionDeConvocatorias() throws Exception {
        mockMvc.perform(get("/api/admin/convocatorias")).andExpect(status().isUnauthorized());

        MockHttpSession estudiante = sesion(new AuthenticatedPrincipal("ALU_21281181", "21281181", AuthRole.STUDENT));
        mockMvc.perform(get("/api/admin/convocatorias").session(estudiante)).andExpect(status().isForbidden());

        MockHttpSession admin = sesion(new AuthenticatedPrincipal("SA01", "SA01", AuthRole.ADMIN));
        mockMvc.perform(post("/api/admin/convocatorias").session(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminConsultaCreaYActualizaConvocatorias() throws Exception {
        MockHttpSession admin = sesion(new AuthenticatedPrincipal("SA01", "SA01", AuthRole.ADMIN));
        String csrf = SessionAttributes.csrfToken(admin);

        Map<String, Object> conv = new LinkedHashMap<String, Object>();
        conv.put("idConvocatoria", 900010L);
        conv.put("estado", "ACTIVA");
        when(service.listarConvocatorias("SA01")).thenReturn(Collections.singletonList(conv));
        when(service.consultarConvocatoria("SA01", 900010L)).thenReturn(conv);
        when(service.listarHistorialConvocatoria("SA01", 900010L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/admin/convocatorias").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convocatorias[0].idConvocatoria").value(900010));
        mockMvc.perform(get("/api/admin/convocatorias/900010").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVA"));
        mockMvc.perform(get("/api/admin/convocatorias/900010/historial").session(admin))
                .andExpect(status().isOk());

        LocalDate hoy = LocalDate.now();
        when(service.crearConvocatoria(eq("SA01"), eq(1), eq(2026), any(), any(), any(), any(),
                eq(new BigDecimal("500")), eq(new BigDecimal("600"))))
                .thenReturn(900011L);

        String cuerpo = "{"
                + "\"periodo\":1,\"anio\":2026,"
                + "\"fechaInicio\":\"" + hoy + "\","
                + "\"fechaFin\":\"" + hoy.plusDays(5) + "\","
                + "\"fechaLimiteAutorizacion\":\"" + hoy.plusDays(10) + "\","
                + "\"fechaLimiteAplicacion\":\"" + hoy.plusDays(15) + "\","
                + "\"montoTrabajador\":500,\"montoHomologado\":600"
                + "}";

        mockMvc.perform(post("/api/admin/convocatorias").session(admin)
                .header(SessionAttributes.CSRF_HEADER, csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idConvocatoria").value(900011));

        when(service.actualizarConvocatoria(eq("SA01"), eq(900010L), any(), any(), any(), any(), any(), any()))
                .thenThrow(new ReglaNegocioException("El plazo ya vencio"));

        mockMvc.perform(put("/api/admin/convocatorias/900010").session(admin)
                .header(SessionAttributes.CSRF_HEADER, csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("El plazo ya vencio"));
    }

    private MockHttpSession sesion(AuthenticatedPrincipal principal) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionAttributes.PRINCIPAL_ATTRIBUTE, principal);
        SessionAttributes.csrfToken(session);
        return session;
    }
}
