package com.itt.sareduccion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;

import com.itt.sareduccion.model.DeduccionAdmin;
import com.itt.sareduccion.model.HistorialDeduccionAdmin;
import com.itt.sareduccion.model.HistorialSolicitudAdmin;
import com.itt.sareduccion.model.MontoAdmin;
import com.itt.sareduccion.model.RespuestaSolicitud;
import com.itt.sareduccion.model.SolicitudAdmin;
import com.itt.sareduccion.repository.AdministradorRepository;
import com.itt.sareduccion.repository.AdministradorRepository.DetalleSolicitudData;
import com.itt.sareduccion.service.SolicitudAdminService.DetalleSolicitud;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class SolicitudAdminServiceTest {

    private AdministradorRepository repository;
    private NotificacionService notificacionService;
    private SolicitudAdminService service;

    @BeforeEach
    void setUp() {
        repository = mock(AdministradorRepository.class);
        notificacionService = mock(NotificacionService.class);
        service = new SolicitudAdminService(repository, notificacionService);
    }

    @Test
    void detalleUsaConsultaConsolidadaYLanzaExcepcionSiNoExiste() {
        SolicitudAdmin solicitud = solicitudEstudiante(10L, 1);
        RespuestaSolicitud respuesta = new RespuestaSolicitud(1L, 10L, 3L, null, "CLAVE");
        DeduccionAdmin deduccion = deduccion(5L, 10L, 2);
        HistorialSolicitudAdmin hSol = new HistorialSolicitudAdmin(1L, 10L, 1, "PENDIENTE", null, null, null,
                LocalDateTime.now());
        HistorialDeduccionAdmin hDed = new HistorialDeduccionAdmin(2L, 5L, 2, "AUTORIZADO", "SA01", "Admin",
                new BigDecimal("1400"), new BigDecimal("500"), new BigDecimal("900"), LocalDateTime.now());

        when(repository.consultarDetalle("SA01", 10L)).thenReturn(new DetalleSolicitudData(
                solicitud,
                Collections.singletonList(respuesta),
                deduccion,
                Collections.singletonList(hSol),
                Collections.singletonList(hDed)));

        DetalleSolicitud detalle = service.detalle("SA01", 10L);
        assertSame(solicitud, detalle.getSolicitud());
        assertEquals(1, detalle.getRespuestas().size());
        assertSame(deduccion, detalle.getDeduccion());
        assertEquals(1, detalle.getHistorial().size());
        assertEquals(1, detalle.getHistorialDeduccion().size());

        when(repository.consultarDetalle("SA01", 99L)).thenReturn(null);
        ReglaNegocioException noEncontrada = assertThrows(ReglaNegocioException.class,
                () -> service.detalle("SA01", 99L));
        assertTrue(noEncontrada.getMessage().contains("no encontrada"));
    }

    @Test
    void traduceCodigosOraclePositivosYNegativosCorrectamente() {
        when(repository.buscarSolicitud("SA01", 10L)).thenReturn(solicitudEstudiante(10L, 1));
        when(repository.listarMontos("SA01")).thenReturn(Collections.singletonList(
                new MontoAdmin(2L, new BigDecimal("1600"), "REINSCRIPCION")));

        // OJDBC devuelve codigos positivos (e.g. 20870, 20865, 20042, 1403)
        doThrow(errorOracle(20870)).when(repository).autorizarDeduccion("SA01", 10L, null, 2L, null);
        ReglaNegocioException plazo = assertThrows(ReglaNegocioException.class,
                () -> service.aprobar("SA01", 10L, null, 2L, null));
        assertTrue(plazo.getMessage().contains("vencio el plazo"));

        doThrow(errorOracle(-20870)).when(repository).rechazarSolicitud("SA01", 10L, "Motivo");
        ReglaNegocioException plazoNegativo = assertThrows(ReglaNegocioException.class,
                () -> service.rechazar("SA01", 10L, "Motivo"));
        assertTrue(plazoNegativo.getMessage().contains("vencio el plazo"));

        doThrow(errorOracle(20865)).when(repository).rechazarSolicitud("SA01", 11L, "Motivo");
        ReglaNegocioException paquete = assertThrows(ReglaNegocioException.class,
                () -> service.rechazar("SA01", 11L, "Motivo"));
        assertEquals("No se pudo rechazar la solicitud", paquete.getMessage());

        doThrow(errorOracle(20042)).when(repository).rechazarSolicitud("SA01", 12L, "Motivo");
        assertThrows(ReglaNegocioException.class, () -> service.rechazar("SA01", 12L, "Motivo"));

        doThrow(errorOracle(1403)).when(repository).rechazarSolicitud("SA01", 13L, "Motivo");
        assertThrows(ReglaNegocioException.class, () -> service.rechazar("SA01", 13L, "Motivo"));

        // Errores tecnicos de BD fuera de reglas de negocio deben propagarse intactos
        doThrow(errorOracle(942)).when(repository).rechazarSolicitud("SA01", 14L, "Motivo");
        assertThrows(DataIntegrityViolationException.class, () -> service.rechazar("SA01", 14L, "Motivo"));

        doThrow(new DataAccessResourceFailureException("Pool sin conexion"))
                .when(repository).consultarDetalle("SA01", 15L);
        assertThrows(DataAccessResourceFailureException.class, () -> service.detalle("SA01", 15L));
    }

    @Test
    void registraUnaAdvertenciaPorErrorOracleDeNegocioYNoRegistraErroresTecnicos() {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(SolicitudAdminService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            doThrow(errorOracle(20870)).when(repository).rechazarSolicitud("SA01", 10L, "Motivo");
            assertThrows(ReglaNegocioException.class, () -> service.rechazar("SA01", 10L, "Motivo"));

            assertEquals(1, appender.list.size());
            ILoggingEvent evento = appender.list.get(0);
            assertEquals(Level.WARN, evento.getLevel());
            assertTrue(evento.getFormattedMessage().contains("operacion=rechazar_solicitud"));
            assertTrue(evento.getFormattedMessage().contains("codigoOracle=20870"));
            assertFalse(evento.getFormattedMessage().contains("ORA-"));

            DataAccessException inesperado = errorOracle(942);
            doThrow(inesperado).when(repository).consultarDetalle("SA01", 11L);
            assertSame(inesperado,
                    assertThrows(DataAccessException.class, () -> service.detalle("SA01", 11L)));
            assertEquals(1, appender.list.size());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void aprobarCancelarYAplicarUsanBusquedaPuntualYNotifican() {
        when(repository.buscarSolicitud("SA01", 10L)).thenReturn(solicitudEstudiante(10L, 1));
        when(repository.listarMontos("SA01")).thenReturn(Arrays.asList(
                new MontoAdmin(4L, new BigDecimal("1450"), "REINSCRIPCION"),
                new MontoAdmin(6L, new BigDecimal("1600"), "REINSCRIPCION"),
                new MontoAdmin(5L, new BigDecimal("1900"), "INSCRIPCION")));
        service.aprobar("SA01", 10L, null, null, "  Aprobada  ");
        verify(repository).autorizarDeduccion("SA01", 10L, null, 6L, "Aprobada");
        verify(notificacionService).avisarCambio("SA01", 10L, NotificacionService.Evento.APROBACION, "Aprobada");

        when(repository.buscarDeduccion("SA01", 5L)).thenReturn(deduccion(5L, 10L, 2));
        service.cancelarDeduccion("SA01", 5L, "Anulada");
        verify(repository).cancelarDeduccion("SA01", 5L, "Anulada");
        verify(notificacionService).avisarCambio("SA01", 10L, NotificacionService.Evento.CANCELACION, "Anulada");

        service.aplicarDeduccion("SA01", 5L, "Aplicada");
        verify(repository).aplicarDeduccion("SA01", 5L);
        verify(notificacionService).avisarCambio("SA01", 10L, NotificacionService.Evento.APLICACION, "Aplicada");
    }

    @Test
    void aprobacionSeleccionaElMontoMasRecientePorPerfil() {
        when(repository.buscarSolicitud("SA01", 10L)).thenReturn(solicitudEstudiante(10L, 1));
        when(repository.buscarSolicitud("SA01", 11L)).thenReturn(solicitudAspirante(11L, 1));
        when(repository.listarMontos("SA01")).thenReturn(Arrays.asList(
                new MontoAdmin(2L, new BigDecimal("1450"), "REINSCRIPCION"),
                new MontoAdmin(7L, new BigDecimal("1600"), "REINSCRIPCION"),
                new MontoAdmin(3L, new BigDecimal("1750"), "INSCRIPCION"),
                new MontoAdmin(8L, new BigDecimal("1900"), "INSCRIPCION")));

        service.aprobar("SA01", 10L, null, null, null);
        verify(repository).autorizarDeduccion("SA01", 10L, null, 7L, null);

        service.aprobar("SA01", 11L, null, null, null);
        verify(repository).autorizarDeduccion("SA01", 11L, 8L, null, null);
    }

    private SolicitudAdmin solicitudEstudiante(long id, int estatus) {
        return new SolicitudAdmin(id, "21281181", null, 2, 2026, 900002L, estatus, 1,
                LocalDateTime.now(), "Hijo de trabajador", "Sofia Lopez");
    }

    private SolicitudAdmin solicitudAspirante(long id, int estatus) {
        return new SolicitudAdmin(id, null, "F2026A001", 2, 2026, 900002L, estatus, 2,
                LocalDateTime.now(), "Hijo de homologado", "Carlos Perez");
    }

    private DeduccionAdmin deduccion(long idDeduccion, long idSolicitud, int estatus) {
        return new DeduccionAdmin(idDeduccion, idSolicitud, estatus, new BigDecimal("1400"),
                new BigDecimal("500"), new BigDecimal("900"));
    }

    private DataAccessException errorOracle(int oraCode) {
        return new DataIntegrityViolationException("ORA-" + oraCode, new SQLException("ORA", "72000", oraCode));
    }
}
