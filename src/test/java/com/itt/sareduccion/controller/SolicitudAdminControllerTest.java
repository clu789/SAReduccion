package com.itt.sareduccion.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.itt.sareduccion.auth.ApplicantPrincipal;
import com.itt.sareduccion.auth.AuthRole;
import com.itt.sareduccion.auth.AuthenticatedPrincipal;
import com.itt.sareduccion.auth.SessionAttributes;
import com.itt.sareduccion.auth.SessionPrincipal;
import com.itt.sareduccion.model.DeduccionAdmin;
import com.itt.sareduccion.model.HistorialSolicitudAdmin;
import com.itt.sareduccion.model.RespuestaSolicitud;
import com.itt.sareduccion.model.SolicitudAdmin;
import com.itt.sareduccion.security.GlobalExceptionHandler;
import com.itt.sareduccion.security.SecurityAuditService;
import com.itt.sareduccion.security.WebSecurityConfig;
import com.itt.sareduccion.service.ReglaNegocioException;
import com.itt.sareduccion.service.SolicitudAdminService;

/**
 * Pruebas de los endpoints del panel administrativo (Equipo 2): operaciones
 * autorizadas y no autorizadas. Se prueban dos montajes:
 * <ul>
 * <li>{@code mvc}: con el interceptor comun de autorizacion del Equipo 1, igual
 * que en produccion;</li>
 * <li>{@code soloControlador}: sin interceptor, para comprobar que el propio
 * controlador valida rol ADMIN y CSRF (defensa en profundidad).</li>
 * </ul>
 */
class SolicitudAdminControllerTest {

    private static final String ADMIN_ID = "SA01";

    private SolicitudAdminService servicio;
    private MockMvc mvc;
    private MockMvc soloControlador;

    @BeforeEach
    void setUp() {
        servicio = mock(SolicitudAdminService.class);
        when(servicio.nombreEstatus(any())).thenReturn("PENDIENTE");
        WebSecurityConfig config = new WebSecurityConfig(mock(SecurityAuditService.class), "",
                "/solicitante/login.html", "/administrativo/login.html");
        mvc = MockMvcBuilders.standaloneSetup(new SolicitudAdminController(servicio))
                .addInterceptors(config.authorizationInterceptor(config.roleRules()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        soloControlador = MockMvcBuilders.standaloneSetup(new SolicitudAdminController(servicio))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /* ── No autorizadas ─────────────────────────────────────────────── */

    @Test
    void sinSesionTodasLasRutasResponden401() throws Exception {
        mvc.perform(get("/api/solicitudes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/solicitudes/estadisticas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/solicitudes/10")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/deducciones")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/montos")).andExpect(status().isUnauthorized());
        mvc.perform(json(put("/api/solicitudes/10/aprobar"))).andExpect(status().isUnauthorized());
        mvc.perform(json(put("/api/solicitudes/10/rechazar"))).andExpect(status().isUnauthorized());
        mvc.perform(json(put("/api/deducciones/7/cancelar"))).andExpect(status().isUnauthorized());
        mvc.perform(json(put("/api/deducciones/7/aplicar"))).andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }

    @Test
    void estudianteYAspiranteNoAccedenAlPanel() throws Exception {
        MockHttpSession[] sesiones = {
                sesion(new AuthenticatedPrincipal("ALU_21281181", "21281181", AuthRole.STUDENT)),
                sesion(new ApplicantPrincipal("123456", 2, 2026)) };
        for (MockHttpSession sesion : sesiones) {
            mvc.perform(get("/api/solicitudes").session(sesion)).andExpect(status().isForbidden());
            mvc.perform(get("/api/solicitudes/10").session(sesion)).andExpect(status().isForbidden());
            mvc.perform(get("/api/deducciones").session(sesion)).andExpect(status().isForbidden());
            mvc.perform(conCsrf(put("/api/solicitudes/10/aprobar"), sesion)).andExpect(status().isForbidden());
            mvc.perform(conCsrf(put("/api/solicitudes/10/rechazar"), sesion)).andExpect(status().isForbidden());
            mvc.perform(conCsrf(put("/api/deducciones/7/cancelar"), sesion)).andExpect(status().isForbidden());
            mvc.perform(conCsrf(put("/api/deducciones/7/aplicar"), sesion)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(servicio);
    }

    @Test
    void administradorSinCsrfOConCsrfIncorrectoNoModifica() throws Exception {
        MockHttpSession admin = admin();
        String[] rutas = { "/api/solicitudes/10/aprobar", "/api/solicitudes/10/rechazar",
                "/api/deducciones/7/cancelar", "/api/deducciones/7/aplicar" };
        for (String ruta : rutas) {
            mvc.perform(json(put(ruta)).session(admin)).andExpect(status().isForbidden());
            mvc.perform(json(put(ruta)).session(admin).header(SessionAttributes.CSRF_HEADER, "token-ajeno"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(servicio);
    }

    @Test
    void elControladorValidaRolYCsrfAunSinInterceptor() throws Exception {
        MockHttpSession estudiante = sesion(new AuthenticatedPrincipal("ALU_21281181", "21281181", AuthRole.STUDENT));
        soloControlador.perform(get("/api/solicitudes").session(estudiante)).andExpect(status().isUnauthorized());
        soloControlador.perform(conCsrf(put("/api/solicitudes/10/aprobar"), estudiante))
                .andExpect(status().isUnauthorized());
        soloControlador.perform(json(put("/api/solicitudes/10/aprobar")).session(admin()))
                .andExpect(status().isForbidden());
        soloControlador.perform(json(put("/api/deducciones/7/aplicar")).session(admin()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }

    /* ── Autorizadas ────────────────────────────────────────────────── */

    @Test
    void listarPasaLosFiltrosYUsaLaIdentidadDeLaSesion() throws Exception {
        SolicitudAdmin solicitud = new SolicitudAdmin(10L, "21281181", null, 2, 2026, 900002L, 1, 1,
                LocalDateTime.of(2026, 11, 20, 9, 30), "Hijo de trabajador", "Sofia Lopez Garcia");
        when(servicio.listar(ADMIN_ID, 1, 900002L, "sofia")).thenReturn(Collections.singletonList(solicitud));

        mvc.perform(get("/api/solicitudes").session(admin())
                .param("estatus", "1").param("convocatoria", "900002").param("solicitante", "sofia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idSolicitud").value(10))
                .andExpect(jsonPath("$[0].solicitante").value("21281181"))
                .andExpect(jsonPath("$[0].tipoSolicitante").value("ESTUDIANTE"))
                .andExpect(jsonPath("$[0].estatus").value("PENDIENTE"));
        verify(servicio).listar(ADMIN_ID, 1, 900002L, "sofia");
    }

    @Test
    void detalleIncluyeRespuestasDeduccionEHistorial() throws Exception {
        SolicitudAdmin solicitud = new SolicitudAdmin(10L, null, "123456", 2, 2026, 900002L, 2, 2,
                LocalDateTime.of(2026, 11, 20, 9, 30), "Hijo de homologado", "Ana Ruiz");
        RespuestaSolicitud respuesta = new RespuestaSolicitud(1L, 10L, 5L, "Nombre del trabajador", "S",
                "ABIERTA", 1, null, null, "Juan Ruiz");
        DeduccionAdmin deduccion = new DeduccionAdmin(7L, 10L, 2, new BigDecimal("3000"),
                new BigDecimal("1500"), new BigDecimal("1500"));
        HistorialSolicitudAdmin historial = new HistorialSolicitudAdmin(1L, 10L, 2, "AUTORIZADO", ADMIN_ID,
                "Administrador", "Documentacion completa", LocalDateTime.of(2026, 11, 21, 10, 0));
        when(servicio.detalle(ADMIN_ID, 10L)).thenReturn(new SolicitudAdminService.DetalleSolicitud(solicitud,
                Collections.singletonList(respuesta), deduccion, Collections.singletonList(historial),
                Collections.emptyList()));

        mvc.perform(get("/api/solicitudes/10").session(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSolicitud").value(10))
                .andExpect(jsonPath("$.tipoSolicitante").value("ASPIRANTE"))
                .andExpect(jsonPath("$.respuestas[0].pregunta").value("Nombre del trabajador"))
                .andExpect(jsonPath("$.respuestas[0].respuesta").value("Juan Ruiz"))
                .andExpect(jsonPath("$.deduccion.idDeduccion").value(7))
                .andExpect(jsonPath("$.deduccion.montoFinal").value(1500))
                .andExpect(jsonPath("$.historial[0].estatus").value("AUTORIZADO"))
                .andExpect(jsonPath("$.historial[0].comentario").value("Documentacion completa"));
    }

    @Test
    void aprobarUsaElAdministradorDeLaSesionYNoElDelCuerpo() throws Exception {
        mvc.perform(conCsrf(put("/api/solicitudes/10/aprobar"), admin())
                .content("{\"comentario\":\"Documentacion completa\",\"idAdministrador\":\"OTRO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Solicitud aprobada correctamente."));
        verify(servicio).aprobar(ADMIN_ID, 10L, null, null, "Documentacion completa");
    }

    @Test
    void rechazarCancelarYAplicarDelegaEnElServicio() throws Exception {
        MockHttpSession admin = admin();
        mvc.perform(conCsrf(put("/api/solicitudes/10/rechazar"), admin).content("{\"comentario\":\"Falta acta\"}"))
                .andExpect(status().isOk());
        mvc.perform(conCsrf(put("/api/deducciones/7/cancelar"), admin).content("{\"comentario\":\"Baja\"}"))
                .andExpect(status().isOk());
        mvc.perform(conCsrf(put("/api/deducciones/7/aplicar"), admin).content("{}"))
                .andExpect(status().isOk());

        verify(servicio).rechazar(ADMIN_ID, 10L, "Falta acta");
        verify(servicio).cancelarDeduccion(ADMIN_ID, 7L, "Baja");
        verify(servicio).aplicarDeduccion(eq(ADMIN_ID), eq(7L), any());
    }

    @Test
    void transicionNoPermitidaResponde409ConMensaje() throws Exception {
        doThrow(new ReglaNegocioException("El comentario es obligatorio para rechazar o solicitar correccion"))
                .when(servicio).rechazar(anyString(), anyLong(), any());
        doThrow(new ReglaNegocioException("Solo se pueden aplicar deducciones autorizadas"))
                .when(servicio).aplicarDeduccion(anyString(), anyLong(), any());

        mvc.perform(conCsrf(put("/api/solicitudes/10/rechazar"), admin()).content("{\"comentario\":\"\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("El comentario es obligatorio para rechazar o solicitar correccion"));
        mvc.perform(conCsrf(put("/api/deducciones/7/aplicar"), admin()).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Solo se pueden aplicar deducciones autorizadas"));
    }

    /* ── Utilidades ─────────────────────────────────────────────────── */

    private MockHttpSession admin() {
        return sesion(new AuthenticatedPrincipal(ADMIN_ID, ADMIN_ID, AuthRole.ADMIN));
    }

    private MockHttpSession sesion(SessionPrincipal principal) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionAttributes.PRINCIPAL_ATTRIBUTE, principal);
        SessionAttributes.csrfToken(session);
        return session;
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder) {
        return builder.contentType(MediaType.APPLICATION_JSON).content("{}");
    }

    private MockHttpServletRequestBuilder conCsrf(MockHttpServletRequestBuilder builder, MockHttpSession session) {
        return json(builder).session(session)
                .header(SessionAttributes.CSRF_HEADER, SessionAttributes.csrfToken(session));
    }
}
