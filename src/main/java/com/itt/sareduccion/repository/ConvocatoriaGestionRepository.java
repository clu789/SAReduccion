
package com.itt.sareduccion.repository;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ConvocatoriaGestionRepository {

    private static final String CREAR_CONVOCATORIA_CALL =
            "{call SA_REDUCCION.PKG_ADMINISTRADOR.crear_convocatoria(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";

    private final JdbcTemplate jdbcTemplate;

    public ConvocatoriaGestionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long crearConvocatoria(
            String idAdministrador,
            Integer periodo,
            Integer anio,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            LocalDate fechaLimiteAutorizacion,
            LocalDate fechaLimiteAplicacion,
            BigDecimal montoTrabajador,
            BigDecimal montoHomologado) {

        return jdbcTemplate.execute((ConnectionCallback<Long>) connection -> {

            boolean autoCommitAnterior = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try (CallableStatement statement =
                    connection.prepareCall(CREAR_CONVOCATORIA_CALL)) {

                statement.setString(1, idAdministrador);
                statement.setInt(2, periodo);
                statement.setInt(3, anio);

                statement.setDate(4, Date.valueOf(fechaInicio));
                statement.setDate(5, Date.valueOf(fechaFin));
                statement.setDate(6, Date.valueOf(fechaLimiteAutorizacion));
                statement.setDate(7, Date.valueOf(fechaLimiteAplicacion));

                statement.setBigDecimal(8, montoTrabajador);
                statement.setBigDecimal(9, montoHomologado);

                statement.registerOutParameter(10, Types.NUMERIC);

                statement.execute();

                Number idGenerado = (Number) statement.getObject(10);

                if (idGenerado == null) {
                    throw new SQLException(
                            "El procedimiento no devolvio el ID de la convocatoria");
                }

                connection.commit();

                return idGenerado.longValue();

            } catch (SQLException | RuntimeException exception) {

                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }

                throw exception;

            } finally {
                connection.setAutoCommit(autoCommitAnterior);
            }
        });
    }
}
