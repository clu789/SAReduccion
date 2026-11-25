package com.itt.sareduccion.repository;

import java.sql.Types;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Registra eventos de seguridad en {@code SEG_AUDITORIA_ACCESOS} mediante
 * {@code SA_REDUCCION.PKG_SEGURIDAD.registrar_evento}. El procedimiento usa
 * {@code PRAGMA AUTONOMOUS_TRANSACTION}: confirma por su cuenta y no participa
 * en la transaccion de negocio que este abierta. Usa parametros enlazados.
 */
@Repository
public class AuditoriaSeguridadRepository {

    private static final String REGISTRAR_EVENTO =
            "BEGIN SA_REDUCCION.PKG_SEGURIDAD.registrar_evento(?, ?, ?, ?, ?, ?); END;";

    // Tipos explicitos: varios argumentos pueden ser null.
    private static final int[] ARG_TYPES = {Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
            Types.VARCHAR, Types.VARCHAR};

    private final JdbcTemplate jdbcTemplate;

    public AuditoriaSeguridadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void registrarEvento(String identificadorUsuario, String tipoPerfil, String direccionIp, String evento,
            String detalle, String correlationId) {
        Object[] args = {identificadorUsuario, tipoPerfil, direccionIp, evento, detalle, correlationId};
        jdbcTemplate.update(REGISTRAR_EVENTO, args, ARG_TYPES);
    }
}
