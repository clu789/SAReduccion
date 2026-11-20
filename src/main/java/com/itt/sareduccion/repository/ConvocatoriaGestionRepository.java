
package com.itt.sareduccion.repository;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ConvocatoriaGestionRepository {

    private static final String CREAR_CONVOCATORIA_CALL =
            "{call SA_REDUCCION.PKG_ADMINISTRADOR.crear_convocatoria(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";
    private static final String LISTAR_CONVOCATORIAS_CALL =
        "{call SA_REDUCCION.PKG_ADMINISTRADOR.listar_convocatorias(?, ?)}";
    private static final String CONSULTAR_MONTOS_CONVOCATORIA_CALL =
        "{call SA_REDUCCION.PKG_ADMINISTRADOR.consultar_montos_convocatoria(?, ?, ?)}";

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

    public List<Map<String, Object>> listarConvocatorias(
        String idAdministrador) {

        return jdbcTemplate.execute(
                (ConnectionCallback<List<Map<String, Object>>>) connection -> {

            List<Map<String, Object>> convocatorias =
                    new ArrayList<>();

            try (CallableStatement statement =
                    connection.prepareCall(LISTAR_CONVOCATORIAS_CALL)) {

                statement.setString(1, idAdministrador);
                statement.registerOutParameter(2, Types.REF_CURSOR);

                statement.execute();

                try (ResultSet resultado =
                        (ResultSet) statement.getObject(2)) {

                    while (resultado.next()) {

                        Map<String, Object> convocatoria =
                                new LinkedHashMap<>();

                        convocatoria.put(
                                "idConvocatoria",
                                resultado.getLong("ID_CONVOCATORIA"));

                        convocatoria.put(
                                "periodo",
                                resultado.getInt("ID_PERIODO"));

                        convocatoria.put(
                                "anio",
                                resultado.getInt("ANIO"));

                        convocatoria.put(
                                "fechaInicio",
                                obtenerFecha(resultado,
                                        "FECHA_INICIO_SOLICITUDES"));

                        convocatoria.put(
                                "fechaFin",
                                obtenerFecha(resultado,
                                        "FECHA_FIN_SOLICITUDES"));

                        convocatoria.put(
                                "fechaLimiteAutorizacion",
                                obtenerFecha(resultado,
                                        "FECHA_LIMITE_AUTORIZACION"));

                        convocatoria.put(
                                "fechaLimiteAplicacion",
                                obtenerFecha(resultado,
                                        "FECHA_LIMITE_APLICACION"));

                        convocatorias.add(convocatoria);
                    }
                }
            }

            return convocatorias;
        });
    }

    public List<Map<String, Object>> consultarMontosConvocatoria(
        String idAdministrador,
        Long idConvocatoria) {

        return jdbcTemplate.execute(
                (ConnectionCallback<List<Map<String, Object>>>) connection -> {

            List<Map<String, Object>> montos = new ArrayList<>();

            try (CallableStatement statement =
                    connection.prepareCall(CONSULTAR_MONTOS_CONVOCATORIA_CALL)) {

                statement.setString(1, idAdministrador);
                statement.setLong(2, idConvocatoria);
                statement.registerOutParameter(3, Types.REF_CURSOR);

                statement.execute();

                try (ResultSet resultado =
                        (ResultSet) statement.getObject(3)) {

                    while (resultado.next()) {

                        Map<String, Object> monto =
                                new LinkedHashMap<>();

                        monto.put(
                                "idConvocatoria",
                                resultado.getLong("ID_CONVOCATORIA"));

                        monto.put(
                                "idTipoSolicitud",
                                resultado.getInt("ID_TIPO_SOLICITUD"));

                        monto.put(
                                "nombreTipoSolicitud",
                                resultado.getString("NOMBRE"));

                        monto.put(
                                "montoDeduccion",
                                resultado.getBigDecimal("MONTO_DEDUCCION"));

                        monto.put(
                                "fechaAsignacionMontos",
                                obtenerFecha(resultado,
                                        "FECHA_ASIGNACION_MONTOS"));

                        montos.add(monto);
                    }
                }
            }

            return montos;
        });
    }

    private LocalDate obtenerFecha(
            ResultSet resultado,
            String columna) throws SQLException {

        Date fecha = resultado.getDate(columna);

        return fecha != null ? fecha.toLocalDate() : null;
    }
}
