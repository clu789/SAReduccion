package com.itt.sareduccion.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.itt.sareduccion.auth.AuthRole;
import com.itt.sareduccion.auth.AuthenticatedPrincipal;
import com.itt.sareduccion.auth.SessionAttributes;
import com.itt.sareduccion.repository.AdministradorRepository;
import com.itt.sareduccion.service.NotificacionService;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NotificacionControllerTest {

    private final AdministradorRepository repository = mock(AdministradorRepository.class);
    private final NotificacionService servicio = mock(NotificacionService.class);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new NotificacionController(repository, servicio)).build();

    @Test
    void rechazaSinCsrfOSinRolDeAdministrador() throws Exception {
        mvc.perform(aviso(AuthRole.ADMIN)).andExpect(status().isForbidden());
        mvc.perform(aviso(AuthRole.STUDENT).header(SessionAttributes.CSRF_HEADER, "token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repository, servicio);
    }

    @Test
    void enviaAlCorreoObtenidoDeLaSolicitud() throws Exception {
        when(repository.consultarCorreoSolicitud("SA01", 42L)).thenReturn("aspirante@example.test");
        when(servicio.enviar(42L, "aspirante@example.test", "Aviso de prueba")).thenReturn(true);

        mvc.perform(aviso(AuthRole.ADMIN).header(SessionAttributes.CSRF_HEADER, "token"))
                .andExpect(status().isNoContent());
    }

    @Test
    void informaSiLaSolicitudCareceDeCorreo() throws Exception {
        mvc.perform(aviso(AuthRole.ADMIN).header(SessionAttributes.CSRF_HEADER, "token"))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(servicio);
    }

    @Test
    void informaSiFallaElEnvio() throws Exception {
        when(repository.consultarCorreoSolicitud("SA01", 42L)).thenReturn("aspirante@example.test");

        mvc.perform(aviso(AuthRole.ADMIN).header(SessionAttributes.CSRF_HEADER, "token"))
                .andExpect(status().isServiceUnavailable());
    }

    private MockHttpServletRequestBuilder aviso(AuthRole rol) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionAttributes.PRINCIPAL_ATTRIBUTE, new AuthenticatedPrincipal("ADMIN", "SA01", rol));
        session.setAttribute(SessionAttributes.CSRF_ATTRIBUTE, "token");
        return post("/api/notificaciones").session(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idSolicitud\":42,\"mensaje\":\"Aviso de prueba\"}");
    }
}
