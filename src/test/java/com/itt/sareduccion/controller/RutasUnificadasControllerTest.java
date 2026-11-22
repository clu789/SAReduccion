package com.itt.sareduccion.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
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
import com.itt.sareduccion.model.ConvocatoriaDisponible;
import com.itt.sareduccion.model.IdentidadSolicitante;
import com.itt.sareduccion.model.PerfilSolicitante;
import com.itt.sareduccion.security.GlobalExceptionHandler;
import com.itt.sareduccion.security.SecurityAuditService;
import com.itt.sareduccion.security.WebSecurityConfig;
import com.itt.sareduccion.service.FormularioSolicitudService;
import com.itt.sareduccion.service.SolicitanteService;
import com.itt.sareduccion.service.SolicitudAdminService;

class RutasUnificadasControllerTest {

    private SolicitanteService solicitanteService;
    private FormularioSolicitudService formularioService;
    private SolicitudAdminService adminService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        solicitanteService = mock(SolicitanteService.class);
        formularioService = mock(FormularioSolicitudService.class);
        adminService = mock(SolicitudAdminService.class);
        WebSecurityConfig config = new WebSecurityConfig(mock(SecurityAuditService.class), "",
                "/solicitante/login.html", "/administrativo/login.html");
        mockMvc = MockMvcBuilders.standaloneSetup(
                new SolicitanteController(solicitanteService),
                new FormularioSolicitudController(formularioService),
                new SolicitudAdminController(adminService))
                .addInterceptors(config.authorizationInterceptor(config.roleRules()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void convocatoriasUnificadasIncluyenEstatusDeSolicitudExistenteYPerfil() throws Exception {
        MockHttpSession estudiante = sesion(new AuthenticatedPrincipal("ALU_21281181", "21281181", AuthRole.STUDENT));
        when(solicitanteService.perfil(any(IdentidadSolicitante.class))).thenReturn(new PerfilSolicitante(
                "ESTUDIANTE", "21281181", "Sofia", "Lopez", "Garcia", "Sistemas", 5, null, null, null));

        ConvocatoriaDisponible pendiente = new ConvocatoriaDisponible(900002L, 2, "Enero-Junio", 2026,
                LocalDate.now().minusDays(2), LocalDate.now().plusDays(10), 1, "Hijo de trabajador", null,
                1L, "Formulario 1", new BigDecimal("500"), 42L);
        ConvocatoriaDisponible abierta = new ConvocatoriaDisponible(900003L, 1, "Agosto-Diciembre", 2026,
                LocalDate.now().plusDays(20), LocalDate.now().plusDays(30), 2, "Hijo de homologado", null,
                2L, "Formulario 2", new BigDecimal("650"), null);

        when(solicitanteService.convocatorias(any(IdentidadSolicitante.class)))
                .thenReturn(Arrays.asList(pendiente, abierta));
        Map<Long, Integer> estatus = Collections.singletonMap(42L, 3);
        when(solicitanteService.estatusPorSolicitud(any(IdentidadSolicitante.class), anyList())).thenReturn(estatus);

        mockMvc.perform(get("/api/solicitante/perfil").session(estudiante))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("ESTUDIANTE"))
                .andExpect(jsonPath("$.nombreCompleto").value("Sofia Lopez Garcia"));

        mockMvc.perform(get("/api/solicitante/convocatorias").session(estudiante))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idSolicitudExistente").value(42))
                .andExpect(jsonPath("$[0].idEstatusSolicitud").value(3))
                .andExpect(jsonPath("$[0].puedeIniciar").value(false))
                .andExpect(jsonPath("$[1].idEstatusSolicitud").doesNotExist())
                .andExpect(jsonPath("$[1].puedeIniciar").value(true));
    }

    @Test
    void endpointsDuplicadosYCorreccionFueronEliminadosYReenvioUnificadoFunciona() throws Exception {
        MockHttpSession estudiante = sesion(new AuthenticatedPrincipal("ALU_21281181", "21281181", AuthRole.STUDENT));
        String csrfEst = SessionAttributes.csrfToken(estudiante);

        mockMvc.perform(get("/api/solicitante/formulario/perfil").session(estudiante))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/solicitante/convocatorias-abiertas").session(estudiante))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/solicitante/solicitudes/42/reenviar").session(estudiante)
                .header(SessionAttributes.CSRF_HEADER, csrfEst)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/solicitante/solicitudes/42/reenvio").session(estudiante)
                .header(SessionAttributes.CSRF_HEADER, csrfEst)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idTipoSolicitud\":1,\"respuestas\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSolicitud").value(42))
                .andExpect(jsonPath("$.estatus").value("PENDIENTE"));

        MockHttpSession admin = sesion(new AuthenticatedPrincipal("SA01", "SA01", AuthRole.ADMIN));
        mockMvc.perform(get("/api/convocatorias").session(admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/solicitudes/10/correccion").session(admin)
                .header(SessionAttributes.CSRF_HEADER, SessionAttributes.csrfToken(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comentario\":\"Motivo\"}"))
                .andExpect(status().isNotFound());
    }

    private MockHttpSession sesion(AuthenticatedPrincipal principal) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionAttributes.PRINCIPAL_ATTRIBUTE, principal);
        SessionAttributes.csrfToken(session);
        return session;
    }
}
