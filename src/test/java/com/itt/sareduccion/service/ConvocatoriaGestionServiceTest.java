package com.itt.sareduccion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.itt.sareduccion.repository.ConvocatoriaGestionRepository;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class ConvocatoriaGestionServiceTest {

    private ConvocatoriaGestionRepository repository;
    private ConvocatoriaGestionService service;

    @BeforeEach
    void setUp() {
        repository = mock(ConvocatoriaGestionRepository.class);
        service = new ConvocatoriaGestionService(repository);
    }

    @Test
    void listarCalculaEstadoDeCadaConvocatoriaYConsultarAgregaMontosYBanderas() {
        LocalDate hoy = LocalDate.now();
        when(repository.listarConvocatorias("SA01")).thenReturn(Arrays.asList(
                fila(1L, hoy.minusDays(5), hoy.plusDays(5), hoy.plusDays(10), hoy.plusDays(15)),
                fila(2L, hoy.plusDays(2), hoy.plusDays(10), hoy.plusDays(15), hoy.plusDays(20)),
                fila(3L, hoy.minusDays(20), hoy.minusDays(5), hoy.minusDays(1), hoy.plusDays(5))));

        List<Map<String, Object>> lista = service.listarConvocatorias("SA01");
        assertEquals("ACTIVA", lista.get(0).get("estado"));
        assertEquals(Boolean.TRUE, lista.get(0).get("activa"));
        assertEquals("PROXIMA", lista.get(1).get("estado"));
        assertEquals(Boolean.FALSE, lista.get(1).get("activa"));
        assertEquals("CERRADA", lista.get(2).get("estado"));
        assertEquals(Boolean.FALSE, lista.get(2).get("activa"));

        when(repository.consultarMontosConvocatoria("SA01", 3L)).thenReturn(Collections.emptyList());
        Map<String, Object> detalle = service.consultarConvocatoria("SA01", 3L);
        assertEquals(true, detalle.get("puedeEditarFechas"));
        assertEquals(false, detalle.get("puedeEditarMontos"));
        assertNull(service.consultarConvocatoria("SA01", 999L));
    }

    @Test
    void crearYActualizarValidanFechasMontosYTraduccionOracle() {
        LocalDate hoy = LocalDate.now();
        // Orden de fechas invalido
        assertThrows(ReglaNegocioException.class, () -> service.crearConvocatoria(
                "SA01", 1, 2026, hoy.plusDays(5), hoy.plusDays(1), hoy.plusDays(10), hoy.plusDays(15),
                new BigDecimal("500"), new BigDecimal("600")));
        // Mas de 2 decimales
        assertThrows(ReglaNegocioException.class, () -> service.crearConvocatoria(
                "SA01", 1, 2026, hoy, hoy.plusDays(5), hoy.plusDays(10), hoy.plusDays(15),
                new BigDecimal("500.123"), new BigDecimal("600")));

        when(repository.crearConvocatoria("SA01", 1, 2026, hoy, hoy.plusDays(5), hoy.plusDays(10),
                hoy.plusDays(15), new BigDecimal("500.00"), new BigDecimal("600.00")))
                .thenReturn(900010L);

        Long id = service.crearConvocatoria("SA01", 1, 2026, hoy, hoy.plusDays(5), hoy.plusDays(10),
                hoy.plusDays(15), new BigDecimal("500.00"), new BigDecimal("600.00"));
        assertEquals(900010L, id);

        when(repository.crearConvocatoria("SA01", 2, 2026, hoy, hoy.plusDays(5), hoy.plusDays(10),
                hoy.plusDays(15), new BigDecimal("500"), new BigDecimal("600")))
                .thenThrow(new DataIntegrityViolationException("ORA-20916",
                        new SQLException("duplicada", "72000", 20916)));
        ReglaNegocioException dup = assertThrows(ReglaNegocioException.class, () -> service.crearConvocatoria(
                "SA01", 2, 2026, hoy, hoy.plusDays(5), hoy.plusDays(10), hoy.plusDays(15),
                new BigDecimal("500"), new BigDecimal("600")));
        assertTrue(dup.getMessage().contains("Ya existe una convocatoria"));

        Map<String, Object> cambio = new LinkedHashMap<String, Object>();
        cambio.put("huboCambios", true);
        when(repository.actualizarConvocatoria("SA01", 900010L, hoy, hoy.plusDays(6), hoy.plusDays(11),
                hoy.plusDays(16), new BigDecimal("550"), new BigDecimal("650")))
                .thenReturn(cambio);
        Map<String, Object> resultado = service.actualizarConvocatoria("SA01", 900010L, hoy, hoy.plusDays(6),
                hoy.plusDays(11), hoy.plusDays(16), new BigDecimal("550"), new BigDecimal("650"));
        assertFalse(resultado.isEmpty());
        verify(repository).actualizarConvocatoria("SA01", 900010L, hoy, hoy.plusDays(6), hoy.plusDays(11),
                hoy.plusDays(16), new BigDecimal("550"), new BigDecimal("650"));

        when(repository.listarHistorialConvocatoria("SA01", 999L))
                .thenThrow(new DataIntegrityViolationException("ORA-20915",
                        new SQLException("No registrada", "72000", 20915)));
        assertThrows(ConvocatoriaNoEncontradaException.class,
                () -> service.listarHistorialConvocatoria("SA01", 999L));

        when(repository.actualizarConvocatoria("SA01", 999L, hoy, hoy.plusDays(6), hoy.plusDays(11),
                hoy.plusDays(16), new BigDecimal("550"), new BigDecimal("650")))
                .thenThrow(new DataIntegrityViolationException("ORA-20915",
                        new SQLException("No registrada", "72000", 20915)));
        assertThrows(ConvocatoriaNoEncontradaException.class,
                () -> service.actualizarConvocatoria("SA01", 999L, hoy, hoy.plusDays(6),
                        hoy.plusDays(11), hoy.plusDays(16), new BigDecimal("550"), new BigDecimal("650")));
    }

    @Test
    void registraUnaAdvertenciaPorErrorOracleTraducidoYPropagaElTecnicoSinLogLocal() {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(ConvocatoriaGestionService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        LocalDate hoy = LocalDate.now();
        DataIntegrityViolationException duplicada = new DataIntegrityViolationException(
                "ORA-20916 detalle interno",
                new SQLException("detalle interno", "72000", 20916));
        try {
            when(repository.crearConvocatoria("SA01", 1, 2026, hoy, hoy.plusDays(5), hoy.plusDays(10),
                    hoy.plusDays(15), new BigDecimal("500"), new BigDecimal("600")))
                    .thenThrow(duplicada);

            assertThrows(ReglaNegocioException.class, () -> service.crearConvocatoria(
                    "SA01", 1, 2026, hoy, hoy.plusDays(5), hoy.plusDays(10), hoy.plusDays(15),
                    new BigDecimal("500"), new BigDecimal("600")));

            assertEquals(1, appender.list.size());
            ILoggingEvent evento = appender.list.get(0);
            assertEquals(Level.WARN, evento.getLevel());
            assertTrue(evento.getFormattedMessage().contains("operacion=crear_convocatoria"));
            assertTrue(evento.getFormattedMessage().contains("codigoOracle=20916"));
            assertFalse(evento.getFormattedMessage().contains("detalle interno"));

            DataIntegrityViolationException inesperada = new DataIntegrityViolationException(
                    "ORA-942", new SQLException("detalle interno", "72000", 942));
            when(repository.listarHistorialConvocatoria("SA01", 2L)).thenThrow(inesperada);
            assertSame(inesperada, assertThrows(DataIntegrityViolationException.class,
                    () -> service.listarHistorialConvocatoria("SA01", 2L)));
            assertEquals(1, appender.list.size());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private Map<String, Object> fila(long id, LocalDate ini, LocalDate fin, LocalDate aut, LocalDate apl) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("idConvocatoria", id);
        m.put("fechaInicio", ini);
        m.put("fechaFin", fin);
        m.put("fechaLimiteAutorizacion", aut);
        m.put("fechaLimiteAplicacion", apl);
        return m;
    }
}
