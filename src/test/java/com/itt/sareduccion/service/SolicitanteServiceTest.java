package com.itt.sareduccion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itt.sareduccion.auth.ApplicantPrincipal;
import com.itt.sareduccion.auth.AuthRole;
import com.itt.sareduccion.auth.AuthenticatedPrincipal;
import com.itt.sareduccion.model.ConvocatoriaDisponible;
import com.itt.sareduccion.model.DeduccionPropia;
import com.itt.sareduccion.model.HistorialPropio;
import com.itt.sareduccion.model.IdentidadSolicitante;
import com.itt.sareduccion.model.SolicitudPropia;
import com.itt.sareduccion.repository.SolicitanteRepository;
import com.itt.sareduccion.service.SolicitanteService.Reenvio;
import com.itt.sareduccion.service.SolicitanteService.Respuesta;
import com.itt.sareduccion.service.SolicitanteService.Seguimiento;

/** Reglas del panel del solicitante sin Oracle: el repositorio se simula. */
class SolicitanteServiceTest {

    private static final IdentidadSolicitante ESTUDIANTE = IdentidadSolicitante.estudiante("21281181");

    private SolicitanteRepository repository;
    private SolicitanteService service;

    @BeforeEach
    void preparar() {
        repository = mock(SolicitanteRepository.class);
        service = new SolicitanteService(repository, new ObjectMapper());
    }

    @Test
    void identidadSoloParaEstudiantesYAspirantes() {
        assertNull(IdentidadSolicitante.desde(new AuthenticatedPrincipal("SA01", "SA01", AuthRole.ADMIN)));
        assertNull(IdentidadSolicitante.desde(null));

        IdentidadSolicitante estudiante = IdentidadSolicitante
                .desde(new AuthenticatedPrincipal("ALU_21281181", "21281181", AuthRole.STUDENT));
        assertTrue(estudiante.esEstudiante());
        assertEquals("21281181", estudiante.getNumeroControl());
        assertEquals("***181", estudiante.enmascarado());

        IdentidadSolicitante aspirante = IdentidadSolicitante.desde(new ApplicantPrincipal("F2026A001", 2, 2026));
        assertFalse(aspirante.esEstudiante());
        assertEquals("F2026A001", aspirante.getFicha());
        assertEquals(Integer.valueOf(2), aspirante.getPeriodo());
        assertEquals(Integer.valueOf(2026), aspirante.getAnio());
    }

    @Test
    void seguimientoAgrupaHistorialDeduccionYAcciones() {
        when(repository.solicitudes(ESTUDIANTE)).thenReturn(Arrays.asList(
                solicitud(10L, 1, true), solicitud(11L, 3, true), solicitud(12L, 2, false)));
        when(repository.historial(ESTUDIANTE)).thenReturn(Arrays.asList(
                historial(11L, 1, null, false), historial(11L, 3, "Falta documento", true),
                historial(10L, 1, null, false)));
        when(repository.deducciones(ESTUDIANTE)).thenReturn(Collections.singletonList(
                new DeduccionPropia(5L, 12L, 2, new BigDecimal("1450"), new BigDecimal("500"),
                        new BigDecimal("950"))));

        List<Seguimiento> seguimiento = service.seguimiento(ESTUDIANTE);

        assertEquals(3, seguimiento.size());
        assertTrue(seguimiento.get(0).isPuedeRetirar());
        assertFalse(seguimiento.get(0).isPuedeReenviar());
        assertEquals(1, seguimiento.get(0).getHistorial().size());

        assertFalse(seguimiento.get(1).isPuedeRetirar());
        assertTrue(seguimiento.get(1).isPuedeReenviar());
        assertEquals("Falta documento", seguimiento.get(1).getHistorial().get(1).getComentario());

        assertFalse(seguimiento.get(2).isPuedeRetirar());
        assertFalse(seguimiento.get(2).isPuedeReenviar());
        assertEquals(Long.valueOf(5L), seguimiento.get(2).getDeduccion().getIdDeduccion());
    }

    @Test
    void consultarSolicitudAjenaResponde404() {
        when(repository.solicitudes(ESTUDIANTE)).thenReturn(Collections.singletonList(solicitud(10L, 1, true)));
        assertThrows(SolicitudNoEncontradaException.class, () -> service.seguimiento(ESTUDIANTE, 99L));
    }

    @Test
    void retiraSoloPendientesConRecepcionAbierta() {
        when(repository.solicitudes(ESTUDIANTE)).thenReturn(Arrays.asList(
                solicitud(10L, 1, true), solicitud(11L, 2, true), solicitud(12L, 1, false)));

        service.retirar(ESTUDIANTE, 10L);
        verify(repository).retirar(ESTUDIANTE, 10L);

        assertThrows(ReglaNegocioException.class, () -> service.retirar(ESTUDIANTE, 11L));
        assertThrows(ReglaNegocioException.class, () -> service.retirar(ESTUDIANTE, 12L));
        assertThrows(SolicitudNoEncontradaException.class, () -> service.retirar(ESTUDIANTE, 99L));
        verify(repository, never()).retirar(ESTUDIANTE, 11L);
        verify(repository, never()).retirar(ESTUDIANTE, 12L);
        verify(repository, never()).retirar(ESTUDIANTE, 99L);
    }

    @Test
    void solicitudAutorizadaNoSePuedeRetirarNiReenviar() {
        when(repository.solicitudes(ESTUDIANTE)).thenReturn(Collections.singletonList(solicitud(10L, 2, true)));
        assertThrows(ReglaNegocioException.class, () -> service.retirar(ESTUDIANTE, 10L));
        assertThrows(ReglaNegocioException.class, () -> service.reenviar(ESTUDIANTE, 10L, reenvio(1, 1L)));
    }

    @Test
    void traduceErroresDeOracleAlRetirar() {
        when(repository.solicitudes(ESTUDIANTE)).thenReturn(Collections.singletonList(solicitud(10L, 1, true)));
        doThrow(new DataIntegrityViolationException("ORA", new SQLException("Plazo", "72000", 20860)))
                .when(repository).retirar(ESTUDIANTE, 10L);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.retirar(ESTUDIANTE, 10L));
        assertTrue(error.getMessage().contains("recepcion"));
    }

    @Test
    void reenviaConRespuestasEnFormatoDeOracle() {
        when(repository.solicitudes(ESTUDIANTE)).thenReturn(Collections.singletonList(solicitud(11L, 3, true)));
        when(repository.convocatorias(ESTUDIANTE)).thenReturn(Collections.singletonList(convocatoria(2, 2L)));

        Reenvio reenvio = reenvio(2, 2L);
        service.reenviar(ESTUDIANTE, 11L, reenvio);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(repository).reenviar(eq(ESTUDIANTE), eq(11L), eq(2), eq(2L), json.capture());
        assertEquals("[{\"id_pregunta\":5,\"id_opcion\":null,\"texto_respuesta\":\"CLAVE-H001\"},"
                + "{\"id_pregunta\":7,\"id_opcion\":3,\"texto_respuesta\":null}]", json.getValue());
    }

    @Test
    void rechazaReenvioConTipoFueraDeLaConvocatoria() {
        when(repository.solicitudes(ESTUDIANTE)).thenReturn(Collections.singletonList(solicitud(11L, 6, true)));
        when(repository.convocatorias(ESTUDIANTE)).thenReturn(Collections.singletonList(convocatoria(1, 1L)));

        assertThrows(ReglaNegocioException.class, () -> service.reenviar(ESTUDIANTE, 11L, reenvio(2, 2L)));
        verify(repository, never()).reenviar(eq(ESTUDIANTE), anyLong(), anyInt(), anyLong(), anyString());
    }

    @Test
    void rechazaReenvioSinRespuestas() {
        when(repository.solicitudes(ESTUDIANTE)).thenReturn(Collections.singletonList(solicitud(11L, 4, true)));
        Reenvio sinRespuestas = reenvio(1, 1L);
        sinRespuestas.setRespuestas(Collections.<Respuesta>emptyList());

        assertThrows(ReglaNegocioException.class, () -> service.reenviar(ESTUDIANTE, 11L, sinRespuestas));
        assertThrows(ReglaNegocioException.class, () -> service.reenviar(ESTUDIANTE, 11L, null));
    }

    @Test
    void rechazaReenvioConTodasLasRespuestasVacias() {
        when(repository.solicitudes(ESTUDIANTE)).thenReturn(Collections.singletonList(solicitud(11L, 6, true)));
        when(repository.convocatorias(ESTUDIANTE)).thenReturn(Collections.singletonList(convocatoria(1, 1L)));
        Respuesta vacia = new Respuesta();
        vacia.setIdPregunta(4L);
        vacia.setTextoRespuesta(" ");
        Reenvio reenvio = reenvio(1, 1L);
        reenvio.setRespuestas(Collections.singletonList(vacia));

        assertThrows(ReglaNegocioException.class, () -> service.reenviar(ESTUDIANTE, 11L, reenvio));
        verify(repository, never()).reenviar(eq(ESTUDIANTE), anyLong(), anyInt(), anyLong(), anyString());
    }

    private SolicitudPropia solicitud(long id, int estatus, boolean recepcionAbierta) {
        return new SolicitudPropia(id, 900002L, 1, estatus, LocalDateTime.now(), LocalDateTime.now(),
                "ESTATUS_" + estatus, "Hijo de trabajador", 1L, 2, "Enero-Junio", 2026,
                LocalDate.now().minusDays(7), LocalDate.now().plusDays(14), recepcionAbierta);
    }

    private HistorialPropio historial(long idSolicitud, int estatus, String comentario, boolean administrativo) {
        return new HistorialPropio(1L, idSolicitud, estatus, "ESTATUS_" + estatus, comentario,
                LocalDateTime.now(), administrativo);
    }

    private ConvocatoriaDisponible convocatoria(int tipo, long formulario) {
        return new ConvocatoriaDisponible(900002L, 2, "Enero-Junio", 2026, LocalDate.now().minusDays(7),
                LocalDate.now().plusDays(14), tipo, "Tipo " + tipo, null, formulario, "Formulario " + formulario,
                new BigDecimal("500"), 11L);
    }

    private Reenvio reenvio(int tipo, long formulario) {
        Respuesta abierta = new Respuesta();
        abierta.setIdPregunta(5L);
        abierta.setTextoRespuesta(" CLAVE-H001 ");
        Respuesta opcion = new Respuesta();
        opcion.setIdPregunta(7L);
        opcion.setIdOpcion(3L);
        opcion.setTextoRespuesta("  ");
        Respuesta opcionalVacia = new Respuesta();
        opcionalVacia.setIdPregunta(8L);
        opcionalVacia.setTextoRespuesta("   ");

        Reenvio reenvio = new Reenvio();
        reenvio.setIdTipoSolicitud(tipo);
        reenvio.setIdFormulario(formulario);
        reenvio.setRespuestas(Arrays.asList(abierta, opcion, opcionalVacia));
        return reenvio;
    }
}
