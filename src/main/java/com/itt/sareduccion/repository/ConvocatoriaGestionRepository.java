
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

    private static final String ACTUALIZAR_CONVOCATORIA_CALL =
            "{call SA_REDUCCION.PKG_ADMINISTRADOR.actualizar_convocatoria(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";

    private static final String LISTAR_HISTORIAL_CONVOCATORIA_CALL =
            "{call SA_REDUCCION.PKG_ADMINISTRADOR.listar_historial_convocatoria(?, ?, ?)}";

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

    public Map<String, Object> actualizarConvocatoria(
            String idAdministrador,
            Long idConvocatoria,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            LocalDate fechaLimiteAutorizacion,
            LocalDate fechaLimiteAplicacion,
            BigDecimal montoTrabajador,
            BigDecimal montoHomologado) {

        return jdbcTemplate.execute((ConnectionCallback<Map<String, Object>>) connection -> {
            boolean autoCommitAnterior = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try (CallableStatement statement = connection.prepareCall(ACTUALIZAR_CONVOCATORIA_CALL)) {
                statement.setString(1, idAdministrador);
                statement.setLong(2, idConvocatoria);
                statement.setDate(3, Date.valueOf(fechaInicio));
                statement.setDate(4, Date.valueOf(fechaFin));
                statement.setDate(5, Date.valueOf(fechaLimiteAutorizacion));
                statement.setDate(6, Date.valueOf(fechaLimiteAplicacion));
                statement.setBigDecimal(7, montoTrabajador);
                statement.setBigDecimal(8, montoHomologado);
                statement.registerOutParameter(9, Types.NUMERIC);
                statement.registerOutParameter(10, Types.NUMERIC);
                statement.registerOutParameter(11, Types.NUMERIC);

                statement.execute();
                connection.commit();

                boolean fechasModificadas = banderaOut(statement.getObject(9));
                boolean trabajadorModificado = banderaOut(statement.getObject(10));
                boolean homologadoModificado = banderaOut(statement.getObject(11));

                Map<String, Object> cambios = new LinkedHashMap<>();
                cambios.put("fechasModificadas", fechasModificadas);
                cambios.put("montoTrabajadorModificado", trabajadorModificado);
                cambios.put("montoHomologadoModificado", homologadoModificado);
                cambios.put("huboCambios", fechasModificadas
                        || trabajadorModificado || homologadoModificado);
                return cambios;

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

    private static boolean banderaOut(Object valor) {
        return valor instanceof Number && ((Number) valor).intValue() == 1;
    }

    private LocalDate obtenerFecha(
            ResultSet resultado,
            String columna) throws SQLException {

        Date fecha = resultado.getDate(columna);

        return fecha != null ? fecha.toLocalDate() : null;
    }
    /** Historial ordenado por el procedimiento Oracle (mas reciente primero). */
    public List<Map<String, Object>> listarHistorialConvocatoria(
            String idAdministrador, Long idConvocatoria) {
        return jdbcTemplate.execute(
                (ConnectionCallback<List<Map<String, Object>>>) connection -> {
            List<Map<String, Object>> historial = new ArrayList<>();
            try (CallableStatement statement =
                    connection.prepareCall(LISTAR_HISTORIAL_CONVOCATORIA_CALL)) {
                statement.setString(1, idAdministrador);
                statement.setLong(2, idConvocatoria);
                statement.registerOutParameter(3, Types.REF_CURSOR);
                statement.execute();
                Object cursor = statement.getObject(3);
                if (!(cursor instanceof ResultSet)) {
                    throw new SQLException("El procedimiento de historial no devolvio un cursor JDBC.");
                }
                try (ResultSet rs = (ResultSet) cursor) {
                    while (rs.next()) {
                        Map<String, Object> fila = new LinkedHashMap<>();
                        fila.put("idHistorialConvocatoria", rs.getLong(1));
                        fila.put("idConvocatoria", rs.getLong(2));
                        fila.put("idAdministrador", rs.getString(3));
                        java.sql.Timestamp registro = rs.getTimestamp(4);
                        fila.put("fechaRegistro", registro == null ? null : registro.toLocalDateTime());
                        fila.put("inicioAnterior", fechaHistorial(rs, 5));
                        fila.put("finAnterior", fechaHistorial(rs, 6));
                        fila.put("autorizacionAnterior", fechaHistorial(rs, 7));
                        fila.put("aplicacionAnterior", fechaHistorial(rs, 8));
                        fila.put("inicioNuevo", fechaHistorial(rs, 9));
                        fila.put("finNuevo", fechaHistorial(rs, 10));
                        fila.put("autorizacionNueva", fechaHistorial(rs, 11));
                        fila.put("aplicacionNueva", fechaHistorial(rs, 12));
                        historial.add(fila);
                    }
                }
            }
            return historial;
        });
    }

    private static LocalDate fechaHistorial(ResultSet rs, int indice)
            throws SQLException {
        Date valor = rs.getDate(indice);
        return valor == null ? null : valor.toLocalDate();
    }

}
