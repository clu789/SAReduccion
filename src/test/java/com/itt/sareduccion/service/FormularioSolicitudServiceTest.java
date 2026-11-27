package com.itt.sareduccion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.jdbc.UncategorizedSQLException;

import com.itt.sareduccion.repository.FormularioSolicitudRepository;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class FormularioSolicitudServiceTest {

    @Test
    void registraCodigoOracleConocidoSinGuardarElMensajeInterno() {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(FormularioSolicitudService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        MDC.put("correlationId", "form-test-123");
        try {
            FormularioSolicitudRepository repository = mock(FormularioSolicitudRepository.class);
            when(repository.convocatoriasAbiertas(null)).thenThrow(new UncategorizedSQLException(
                    "consulta", "consulta", new SQLException("ORA-20812 detalle interno", "72000", 20812)));

            FormularioSolicitudService service = new FormularioSolicitudService(repository);
            assertThrows(ReglaNegocioException.class, () -> service.convocatoriasAbiertas(null));

            assertEquals(1, appender.list.size());
            ILoggingEvent evento = appender.list.get(0);
            assertEquals(Level.WARN, evento.getLevel());
            assertTrue(evento.getFormattedMessage().contains("operacion=consultar_convocatorias"));
            assertTrue(evento.getFormattedMessage().contains("codigoOracle=20812"));
            assertFalse(evento.getFormattedMessage().contains("detalle interno"));
            assertEquals("form-test-123", evento.getMDCPropertyMap().get("correlationId"));
        } finally {
            MDC.remove("correlationId");
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
