package com.itt.sareduccion.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.itt.sareduccion.repository.AuditoriaSeguridadRepository;
import com.itt.sareduccion.security.logging.SensitiveDataMasker;

/**
 * Punto unico para auditar accesos y eventos de sesion. Escribe en el log
 * tecnico (identificador enmascarado) y en Oracle (identificador completo).
 * Un fallo al auditar nunca interrumpe el flujo de autenticacion.
 */
@Service
public class SecurityAuditService {

    public static final String PERFIL_DESCONOCIDO = "DESCONOCIDO";

    private static final Logger LOG = LoggerFactory.getLogger("SAR.SEGURIDAD");

    private final AuditoriaSeguridadRepository repository;
    private final boolean databaseEnabled;

    public SecurityAuditService(AuditoriaSeguridadRepository repository,
            @Value("${sar.security.audit.database-enabled:true}") boolean databaseEnabled) {
        this.repository = repository;
        this.databaseEnabled = databaseEnabled;
    }

    public void registrar(SecurityEvent evento, String identificador, String perfil, String ip, String detalle) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        String perfilSeguro = perfil == null ? PERFIL_DESCONOCIDO : perfil;
        LOG.info("evento={} perfil={} usuario={} ip={} detalle={}", evento, perfilSeguro,
                SensitiveDataMasker.identifier(identificador), ip, detalle == null ? "-" : detalle);
        if (!databaseEnabled) {
            return;
        }
        try {
            repository.registrarEvento(identificador, perfilSeguro, ip, evento.name(), detalle, correlationId);
        } catch (RuntimeException exception) {
            LOG.warn("No se pudo registrar la auditoria de {} en Oracle ({})", evento,
                    exception.getClass().getSimpleName());
        }
    }
}
