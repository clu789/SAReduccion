package com.itt.sareduccion.repository;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.itt.sareduccion.model.ConvocatoriaAdmin;
import com.itt.sareduccion.model.DeduccionAdmin;
import com.itt.sareduccion.model.RespuestaSolicitud;
import com.itt.sareduccion.model.SolicitudAdmin;

/**
 * Ejecuta la API de administracion mediante {@code SA_REDUCCION.PKG_ADMINISTRADOR}
 * con la cuenta tecnica del pool. Solo parametros enlazados: nunca concatena
 * valores. El cliente (este repositorio) controla COMMIT/ROLLBACK de las
 * operaciones de escritura, tal como exige el paquete.
 */
@Repository
public class AdministradorRepository {

    private static final String VALIDATE_ADMIN_CALL =
            "{call SA_REDUCCION.PKG_ADMINISTRADOR.listar_estudiantes(?, ?)}";
    private static final String LISTAR_SOLICITUDES_CALL =
            "{call SA_REDUCCION.PKG_ADMINISTRADOR.listar_solicitudes(?, ?)}";
    private static final String LISTAR_CONVOCATORIAS_CALL =
            "{call SA_REDUCCION.PKG_ADMINISTRADOR.listar_convocatorias(?, ?)}";
    private static final String LISTAR_RESPUESTAS_CALL =
            "{call SA_REDUCCION.PKG_ADMINISTRADOR.listar_respuestas(?, ?)}";
    private static final String LISTAR_DEDUCCIONES_CALL =
            "{call SA_REDUCCION.PKG_ADMINISTRADOR.listar_deducciones(?, ?)}";
    private static final String RECHAZAR_SOLICITUD_CALL =
            "{call SA_REDUCCION.PKG_ADMINISTRADOR.rechazar_solicitud(?, ?, ?)}";
    private static final String AUTORIZAR_DEDUCCION_CALL =
            "{call SA_REDUCCION.PKG_ADMINISTRADOR.autorizar_deduccion(?, ?, ?, ?, ?, ?)}";
    private static final String CANCELAR_DEDUCCION_CALL =
            "{call SA_REDUCCION.PKG_ADMINISTRADOR.cancelar_deduccion(?, ?, ?)}";
    private static final String APLICAR_DEDUCCION_CALL =
            "{call SA_REDUCCION.PKG_ADMINISTRADOR.aplicar_deduccion(?, ?)}";

    private final JdbcTemplate jdbcTemplate;

    public AdministradorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void validarPerfil(String idAdministrador) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            validarPerfil(connection, idAdministrador);
            return null;
        });
    }

    public List<SolicitudAdmin> listarSolicitudes(String idAdministrador) {
        return jdbcTemplate.execute((ConnectionCallback<List<SolicitudAdmin>>) connection ->
                listarSolicitudes(connection, idAdministrador));
    }

    public List<ConvocatoriaAdmin> listarConvocatorias(String idAdministrador) {
        return jdbcTemplate.execute((ConnectionCallback<List<ConvocatoriaAdmin>>) connection ->
                listarConvocatorias(connection, idAdministrador));
    }

    public List<RespuestaSolicitud> listarRespuestas(String idAdministrador) {
        return jdbcTemplate.execute((ConnectionCallback<List<RespuestaSolicitud>>) connection ->
                listarRespuestas(connection, idAdministrador));
    }

    public List<DeduccionAdmin> listarDeducciones(String idAdministrador) {
        return jdbcTemplate.execute((ConnectionCallback<List<DeduccionAdmin>>) connection ->
                listarDeducciones(connection, idAdministrador));
    }

    /**
     * Rechaza una solicitud pendiente (la base exige comentario no vacio).
     * Confirma unicamente si el procedimiento termina bien.
     */
    public void rechazarSolicitud(String idAdministrador, long idSolicitud, String comentario) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            boolean autoCommitAnterior = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (CallableStatement statement = connection.prepareCall(RECHAZAR_SOLICITUD_CALL)) {
                statement.setString(1, idAdministrador);
                statement.setLong(2, idSolicitud);
                statement.setString(3, comentario);
                statement.execute();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommitAnterior);
            }
            return null;
        });
    }

    /**
     * Autoriza la deduccion de una solicitud ya AUTORIZADA y devuelve el id
     * generado. Exactamente un parametro de monto debe ser nulo segun el tipo
     * de solicitante (estudiante: reinscripcion; aspirante: inscripcion).
     */
    public Long autorizarDeduccion(String idAdministrador, long idSolicitud,
            Long idMontoInscripcion, Long idMontoReinscripcion, String comentario) {
        return jdbcTemplate.execute((ConnectionCallback<Long>) connection -> {
            boolean autoCommitAnterior = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (CallableStatement statement = connection.prepareCall(AUTORIZAR_DEDUCCION_CALL)) {
                statement.setString(1, idAdministrador);
                statement.setLong(2, idSolicitud);
                if (idMontoInscripcion == null) {
                    statement.setNull(3, Types.NUMERIC);
                } else {
                    statement.setLong(3, idMontoInscripcion);
                }
                if (idMontoReinscripcion == null) {
                    statement.setNull(4, Types.NUMERIC);
                } else {
                    statement.setLong(4, idMontoReinscripcion);
                }
                statement.setString(5, comentario);
                statement.registerOutParameter(6, Types.NUMERIC);
                statement.execute();

                Number id = (Number) statement.getObject(6);
                if (id == null) {
                    throw new SQLException("PKG_ADMINISTRADOR.autorizar_deduccion no devolvio el id");
                }
                connection.commit();
                return id.longValue();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommitAnterior);
            }
        });
    }

    /**
     * Cancela una deduccion autorizada (la base exige motivo no vacio) y
     * sincroniza el estatus de la solicitud a CANCELADO.
     */
    public void cancelarDeduccion(String idAdministrador, long idDeduccion, String comentario) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            boolean autoCommitAnterior = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (CallableStatement statement = connection.prepareCall(CANCELAR_DEDUCCION_CALL)) {
                statement.setString(1, idAdministrador);
                statement.setLong(2, idDeduccion);
                statement.setString(3, comentario);
                statement.execute();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommitAnterior);
            }
            return null;
        });
    }

    /**
     * Aplica una deduccion autorizada (estado definitivo e inmutable). No
     * recibe comentario: el paquete registra uno fijo en el historial.
     */
    public void aplicarDeduccion(String idAdministrador, long idDeduccion) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            boolean autoCommitAnterior = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (CallableStatement statement = connection.prepareCall(APLICAR_DEDUCCION_CALL)) {
                statement.setString(1, idAdministrador);
                statement.setLong(2, idDeduccion);
                statement.execute();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommitAnterior);
            }
            return null;
        });
    }

    private void validarPerfil(Connection connection, String idAdministrador) throws SQLException {
        try (CallableStatement statement = connection.prepareCall(VALIDATE_ADMIN_CALL)) {
            statement.setString(1, idAdministrador);
            statement.registerOutParameter(2, Types.REF_CURSOR);
            statement.execute();

            Object cursor = statement.getObject(2);
            if (!(cursor instanceof ResultSet)) {
                throw new SQLException("El paquete administrador no devolvio un cursor JDBC");
            }
            ((ResultSet) cursor).close();
        }
    }

    private List<SolicitudAdmin> listarSolicitudes(Connection connection, String idAdministrador)
            throws SQLException {
        List<SolicitudAdmin> solicitudes = new ArrayList<SolicitudAdmin>();
        try (CallableStatement statement = connection.prepareCall(LISTAR_SOLICITUDES_CALL)) {
            statement.setString(1, idAdministrador);
            statement.registerOutParameter(2, Types.REF_CURSOR);
            statement.execute();

            Object cursor = statement.getObject(2);
            if (!(cursor instanceof ResultSet)) {
                throw new SQLException("PKG_ADMINISTRADOR.listar_solicitudes no devolvio un cursor JDBC");
            }

            try (ResultSet resultSet = (ResultSet) cursor) {
                while (resultSet.next()) {
                    Timestamp actualizacion = resultSet.getTimestamp(9);
                    LocalDateTime fechaActualizacion = actualizacion == null
                            ? null : actualizacion.toLocalDateTime();
                    solicitudes.add(new SolicitudAdmin(
                            longOrNull(resultSet.getObject(1)),
                            resultSet.getString(2),
                            resultSet.getString(3),
                            intOrNull(resultSet.getObject(4)),
                            intOrNull(resultSet.getObject(5)),
                            longOrNull(resultSet.getObject(6)),
                            intOrNull(resultSet.getObject(7)),
                            intOrNull(resultSet.getObject(8)),
                            fechaActualizacion,
                            resultSet.getString(10)));
                }
            }
        }
        return solicitudes;
    }

    private List<ConvocatoriaAdmin> listarConvocatorias(Connection connection, String idAdministrador)
            throws SQLException {
        List<ConvocatoriaAdmin> convocatorias = new ArrayList<ConvocatoriaAdmin>();
        try (CallableStatement statement = connection.prepareCall(LISTAR_CONVOCATORIAS_CALL)) {
            statement.setString(1, idAdministrador);
            statement.registerOutParameter(2, Types.REF_CURSOR);
            statement.execute();

            Object cursor = statement.getObject(2);
            if (!(cursor instanceof ResultSet)) {
                throw new SQLException("PKG_ADMINISTRADOR.listar_convocatorias no devolvio un cursor JDBC");
            }

            try (ResultSet resultSet = (ResultSet) cursor) {
                while (resultSet.next()) {
                    java.sql.Date inicio = resultSet.getDate(2);
                    java.sql.Date fin = resultSet.getDate(3);
                    java.sql.Date limiteAutorizacion = resultSet.getDate(4);
                    java.sql.Date limiteAplicacion = resultSet.getDate(5);
                    LocalDate fechaInicio = inicio == null ? null : inicio.toLocalDate();
                    LocalDate fechaFin = fin == null ? null : fin.toLocalDate();
                    LocalDate hoy = LocalDate.now();
                    boolean activa = fechaInicio != null && fechaFin != null
                            && !hoy.isBefore(fechaInicio) && !hoy.isAfter(fechaFin);
                    convocatorias.add(new ConvocatoriaAdmin(
                            longOrNull(resultSet.getObject(1)),
                            fechaInicio,
                            fechaFin,
                            limiteAutorizacion == null ? null : limiteAutorizacion.toLocalDate(),
                            limiteAplicacion == null ? null : limiteAplicacion.toLocalDate(),
                            intOrNull(resultSet.getObject(6)),
                            intOrNull(resultSet.getObject(7)),
                            activa));
                }
            }
        }
        return convocatorias;
    }

    private List<RespuestaSolicitud> listarRespuestas(Connection connection, String idAdministrador)
            throws SQLException {
        List<RespuestaSolicitud> respuestas = new ArrayList<RespuestaSolicitud>();
        try (CallableStatement statement = connection.prepareCall(LISTAR_RESPUESTAS_CALL)) {
            statement.setString(1, idAdministrador);
            statement.registerOutParameter(2, Types.REF_CURSOR);
            statement.execute();

            Object cursor = statement.getObject(2);
            if (!(cursor instanceof ResultSet)) {
                throw new SQLException("PKG_ADMINISTRADOR.listar_respuestas no devolvio un cursor JDBC");
            }

            try (ResultSet resultSet = (ResultSet) cursor) {
                while (resultSet.next()) {
                    respuestas.add(new RespuestaSolicitud(
                            longOrNull(resultSet.getObject(1)),
                            longOrNull(resultSet.getObject(2)),
                            longOrNull(resultSet.getObject(3)),
                            longOrNull(resultSet.getObject(4)),
                            resultSet.getString(5)));
                }
            }
        }
        return respuestas;
    }

    private List<DeduccionAdmin> listarDeducciones(Connection connection, String idAdministrador)
            throws SQLException {
        List<DeduccionAdmin> deducciones = new ArrayList<DeduccionAdmin>();
        try (CallableStatement statement = connection.prepareCall(LISTAR_DEDUCCIONES_CALL)) {
            statement.setString(1, idAdministrador);
            statement.registerOutParameter(2, Types.REF_CURSOR);
            statement.execute();

            Object cursor = statement.getObject(2);
            if (!(cursor instanceof ResultSet)) {
                throw new SQLException("PKG_ADMINISTRADOR.listar_deducciones no devolvio un cursor JDBC");
            }

            try (ResultSet resultSet = (ResultSet) cursor) {
                while (resultSet.next()) {
                    deducciones.add(new DeduccionAdmin(
                            longOrNull(resultSet.getObject(1)),
                            longOrNull(resultSet.getObject(2)),
                            intOrNull(resultSet.getObject(3)),
                            decimalOrNull(resultSet.getObject(4)),
                            decimalOrNull(resultSet.getObject(5)),
                            decimalOrNull(resultSet.getObject(6))));
                }
            }
        }
        return deducciones;
    }

    private Long longOrNull(Object value) {
        return value instanceof Number ? Long.valueOf(((Number) value).longValue()) : null;
    }

    private Integer intOrNull(Object value) {
        return value instanceof Number ? Integer.valueOf(((Number) value).intValue()) : null;
    }

    private BigDecimal decimalOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        return null;
    }
}
