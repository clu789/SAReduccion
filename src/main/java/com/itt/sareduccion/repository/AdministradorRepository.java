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
import java.util.Collections;
import java.util.List;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.itt.sareduccion.model.ConvocatoriaAdmin;
import com.itt.sareduccion.model.DeduccionAdmin;
import com.itt.sareduccion.model.HistorialDeduccionAdmin;
import com.itt.sareduccion.model.HistorialSolicitudAdmin;
import com.itt.sareduccion.model.MontoAdmin;
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
    private static final String LISTAR_MONTOS_CALL =
            "{call SA_REDUCCION.PKG_ADMINISTRADOR.listar_montos(?, ?)}";
    private static final String LISTAR_HISTORIAL_SOLICITUD_CALL =
            "{call SA_REDUCCION.PKG_ADMINISTRADOR.listar_historial_solicitud(?, ?)}";
    private static final String LISTAR_HISTORIAL_DEDUCCION_CALL =
            "{call SA_REDUCCION.PKG_ADMINISTRADOR.listar_historial_deduccion(?, ?)}";
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

    public String consultarCorreoSolicitud(String idAdministrador, long idSolicitud) {
        return jdbcTemplate.execute((ConnectionCallback<String>) connection -> {
            try (CallableStatement statement = connection.prepareCall(
                    "{call SA_REDUCCION.PKG_ADMINISTRADOR.consultar_correo_solicitud(?, ?, ?)}")) {
                statement.setString(1, idAdministrador);
                statement.setLong(2, idSolicitud);
                statement.registerOutParameter(3, Types.VARCHAR);
                statement.execute();
                return statement.getString(3);
            }
        });
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

    public List<MontoAdmin> listarMontos(String idAdministrador) {
        return jdbcTemplate.execute((ConnectionCallback<List<MontoAdmin>>) connection ->
                listarMontos(connection, idAdministrador));
    }

    public List<HistorialSolicitudAdmin> listarHistorialSolicitud(String idAdministrador) {
        return jdbcTemplate.execute((ConnectionCallback<List<HistorialSolicitudAdmin>>) connection ->
                listarHistorialSolicitud(connection, idAdministrador));
    }

    public List<HistorialDeduccionAdmin> listarHistorialDeduccion(String idAdministrador) {
        return jdbcTemplate.execute((ConnectionCallback<List<HistorialDeduccionAdmin>>) connection ->
                listarHistorialDeduccion(connection, idAdministrador, null));
    }

    /** Busca una solicitud puntual sin materializar el resto de la lista. */
    public SolicitudAdmin buscarSolicitud(String idAdministrador, long idSolicitud) {
        return jdbcTemplate.execute((ConnectionCallback<SolicitudAdmin>) connection ->
                buscarSolicitud(connection, idAdministrador, idSolicitud));
    }

    /** Busca una deduccion puntual por identificador sin materializar la lista completa. */
    public DeduccionAdmin buscarDeduccion(String idAdministrador, long idDeduccion) {
        return jdbcTemplate.execute((ConnectionCallback<DeduccionAdmin>) connection ->
                buscarDeduccionPorId(connection, idAdministrador, idDeduccion));
    }

    /**
     * Consulta todo el detalle de una solicitud reutilizando una sola conexion
     * del pool y filtrando los cursores por id antes de construir modelos.
     * Devuelve {@code null} tras la primera consulta si la solicitud no existe.
     */
    public DetalleSolicitudData consultarDetalle(String idAdministrador, long idSolicitud) {
        return jdbcTemplate.execute((ConnectionCallback<DetalleSolicitudData>) connection -> {
            SolicitudAdmin solicitud = buscarSolicitud(connection, idAdministrador, idSolicitud);
            if (solicitud == null) {
                return null;
            }
            List<RespuestaSolicitud> respuestas = listarRespuestas(connection, idAdministrador, Long.valueOf(idSolicitud));
            DeduccionAdmin deduccion = buscarDeduccionPorSolicitud(connection, idAdministrador, idSolicitud);
            List<HistorialSolicitudAdmin> historialSolicitud =
                    listarHistorialSolicitud(connection, idAdministrador, Long.valueOf(idSolicitud));
            List<HistorialDeduccionAdmin> historialDeduccion = deduccion != null && deduccion.getIdDeduccion() != null
                    ? listarHistorialDeduccion(connection, idAdministrador, deduccion.getIdDeduccion())
                    : Collections.<HistorialDeduccionAdmin>emptyList();
            return new DetalleSolicitudData(solicitud, respuestas, deduccion, historialSolicitud, historialDeduccion);
        });
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

    private SolicitudAdmin buscarSolicitud(Connection connection, String idAdministrador, long idSolicitud)
            throws SQLException {
        return ejecutarCursor(connection, LISTAR_SOLICITUDES_CALL, idAdministrador, "listar_solicitudes", resultSet -> {
            while (resultSet.next()) {
                Long actual = longOrNull(resultSet.getObject(1));
                if (actual != null && actual.longValue() == idSolicitud) {
                    return mapSolicitud(resultSet);
                }
            }
            return null;
        });
    }

    private List<SolicitudAdmin> listarSolicitudes(Connection connection, String idAdministrador)
            throws SQLException {
        return ejecutarCursor(connection, LISTAR_SOLICITUDES_CALL, idAdministrador, "listar_solicitudes", resultSet -> {
            List<SolicitudAdmin> solicitudes = new ArrayList<SolicitudAdmin>();
            while (resultSet.next()) {
                solicitudes.add(mapSolicitud(resultSet));
            }
            return solicitudes;
        });
    }

    private List<ConvocatoriaAdmin> listarConvocatorias(Connection connection, String idAdministrador)
            throws SQLException {
        return ejecutarCursor(connection, LISTAR_CONVOCATORIAS_CALL, idAdministrador, "listar_convocatorias",
                resultSet -> {
                    List<ConvocatoriaAdmin> convocatorias = new ArrayList<ConvocatoriaAdmin>();
                    LocalDate hoy = LocalDate.now();
                    while (resultSet.next()) {
                        java.sql.Date inicio = resultSet.getDate(2);
                        java.sql.Date fin = resultSet.getDate(3);
                        java.sql.Date limiteAutorizacion = resultSet.getDate(4);
                        java.sql.Date limiteAplicacion = resultSet.getDate(5);
                        LocalDate fechaInicio = inicio == null ? null : inicio.toLocalDate();
                        LocalDate fechaFin = fin == null ? null : fin.toLocalDate();
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
                    return convocatorias;
                });
    }

    private List<RespuestaSolicitud> listarRespuestas(Connection connection, String idAdministrador)
            throws SQLException {
        return listarRespuestas(connection, idAdministrador, null);
    }

    private List<RespuestaSolicitud> listarRespuestas(Connection connection, String idAdministrador,
            Long idSolicitudFiltro) throws SQLException {
        return ejecutarCursor(connection, LISTAR_RESPUESTAS_CALL, idAdministrador, "listar_respuestas", resultSet -> {
            List<RespuestaSolicitud> respuestas = new ArrayList<RespuestaSolicitud>();
            while (resultSet.next()) {
                Long idSolicitud = longOrNull(resultSet.getObject(2));
                if (idSolicitudFiltro == null
                        || (idSolicitud != null && idSolicitud.longValue() == idSolicitudFiltro.longValue())) {
                    respuestas.add(new RespuestaSolicitud(
                            longOrNull(resultSet.getObject(1)),
                            idSolicitud,
                            longOrNull(resultSet.getObject(3)),
                            longOrNull(resultSet.getObject(4)),
                            resultSet.getString(5)));
                }
            }
            return respuestas;
        });
    }

    private List<DeduccionAdmin> listarDeducciones(Connection connection, String idAdministrador)
            throws SQLException {
        return ejecutarCursor(connection, LISTAR_DEDUCCIONES_CALL, idAdministrador, "listar_deducciones", resultSet -> {
            List<DeduccionAdmin> deducciones = new ArrayList<DeduccionAdmin>();
            while (resultSet.next()) {
                deducciones.add(mapDeduccion(resultSet));
            }
            return deducciones;
        });
    }

    private DeduccionAdmin buscarDeduccionPorId(Connection connection, String idAdministrador, long idDeduccion)
            throws SQLException {
        return ejecutarCursor(connection, LISTAR_DEDUCCIONES_CALL, idAdministrador, "listar_deducciones", resultSet -> {
            while (resultSet.next()) {
                Long actual = longOrNull(resultSet.getObject(1));
                if (actual != null && actual.longValue() == idDeduccion) {
                    return mapDeduccion(resultSet);
                }
            }
            return null;
        });
    }

    private DeduccionAdmin buscarDeduccionPorSolicitud(Connection connection, String idAdministrador, long idSolicitud)
            throws SQLException {
        return ejecutarCursor(connection, LISTAR_DEDUCCIONES_CALL, idAdministrador, "listar_deducciones", resultSet -> {
            while (resultSet.next()) {
                Long actual = longOrNull(resultSet.getObject(2));
                if (actual != null && actual.longValue() == idSolicitud) {
                    return mapDeduccion(resultSet);
                }
            }
            return null;
        });
    }

    private List<MontoAdmin> listarMontos(Connection connection, String idAdministrador)
            throws SQLException {
        return ejecutarCursor(connection, LISTAR_MONTOS_CALL, idAdministrador, "listar_montos", resultSet -> {
            List<MontoAdmin> montos = new ArrayList<MontoAdmin>();
            while (resultSet.next()) {
                montos.add(new MontoAdmin(
                        longOrNull(resultSet.getObject(1)),
                        decimalOrNull(resultSet.getObject(2)),
                        resultSet.getString(3)));
            }
            return montos;
        });
    }

    private List<HistorialSolicitudAdmin> listarHistorialSolicitud(Connection connection, String idAdministrador)
            throws SQLException {
        return listarHistorialSolicitud(connection, idAdministrador, null);
    }

    private List<HistorialSolicitudAdmin> listarHistorialSolicitud(Connection connection, String idAdministrador,
            Long idSolicitudFiltro) throws SQLException {
        return ejecutarCursor(connection, LISTAR_HISTORIAL_SOLICITUD_CALL, idAdministrador,
                "listar_historial_solicitud", resultSet -> {
                    List<HistorialSolicitudAdmin> historial = new ArrayList<HistorialSolicitudAdmin>();
                    while (resultSet.next()) {
                        Long idSolicitud = longOrNull(resultSet.getObject(2));
                        if (idSolicitudFiltro == null
                                || (idSolicitud != null && idSolicitud.longValue() == idSolicitudFiltro.longValue())) {
                            Timestamp fecha = resultSet.getTimestamp(8);
                            historial.add(new HistorialSolicitudAdmin(
                                    longOrNull(resultSet.getObject(1)),
                                    idSolicitud,
                                    intOrNull(resultSet.getObject(3)),
                                    resultSet.getString(4),
                                    resultSet.getString(5),
                                    resultSet.getString(6),
                                    resultSet.getString(7),
                                    fecha == null ? null : fecha.toLocalDateTime()));
                        }
                    }
                    return historial;
                });
    }

    private List<HistorialDeduccionAdmin> listarHistorialDeduccion(Connection connection, String idAdministrador,
            Long idDeduccionFiltro) throws SQLException {
        return ejecutarCursor(connection, LISTAR_HISTORIAL_DEDUCCION_CALL, idAdministrador,
                "listar_historial_deduccion", resultSet -> {
                    List<HistorialDeduccionAdmin> historial = new ArrayList<HistorialDeduccionAdmin>();
                    while (resultSet.next()) {
                        Long idDeduccion = longOrNull(resultSet.getObject(2));
                        if (idDeduccionFiltro == null
                                || (idDeduccion != null && idDeduccion.longValue() == idDeduccionFiltro.longValue())) {
                            Timestamp fecha = resultSet.getTimestamp(10);
                            historial.add(new HistorialDeduccionAdmin(
                                    longOrNull(resultSet.getObject(1)),
                                    idDeduccion,
                                    intOrNull(resultSet.getObject(3)),
                                    resultSet.getString(4),
                                    resultSet.getString(5),
                                    resultSet.getString(6),
                                    decimalOrNull(resultSet.getObject(7)),
                                    decimalOrNull(resultSet.getObject(8)),
                                    decimalOrNull(resultSet.getObject(9)),
                                    fecha == null ? null : fecha.toLocalDateTime()));
                        }
                    }
                    return historial;
                });
    }

    private <T> T ejecutarCursor(Connection connection, String sql, String idAdministrador, String procedimiento,
            CursorReader<T> reader) throws SQLException {
        try (CallableStatement statement = connection.prepareCall(sql)) {
            statement.setString(1, idAdministrador);
            statement.registerOutParameter(2, Types.REF_CURSOR);
            statement.execute();

            Object cursor = statement.getObject(2);
            if (!(cursor instanceof ResultSet)) {
                throw new SQLException("PKG_ADMINISTRADOR." + procedimiento + " no devolvio un cursor JDBC");
            }
            try (ResultSet resultSet = (ResultSet) cursor) {
                return reader.read(resultSet);
            }
        }
    }

    private SolicitudAdmin mapSolicitud(ResultSet resultSet) throws SQLException {
        Timestamp actualizacion = resultSet.getTimestamp(9);
        LocalDateTime fechaActualizacion = actualizacion == null ? null : actualizacion.toLocalDateTime();
        return new SolicitudAdmin(
                longOrNull(resultSet.getObject(1)),
                resultSet.getString(2),
                resultSet.getString(3),
                intOrNull(resultSet.getObject(4)),
                intOrNull(resultSet.getObject(5)),
                longOrNull(resultSet.getObject(6)),
                intOrNull(resultSet.getObject(7)),
                intOrNull(resultSet.getObject(8)),
                fechaActualizacion,
                resultSet.getString(10),
                resultSet.getString(11));
    }

    private DeduccionAdmin mapDeduccion(ResultSet resultSet) throws SQLException {
        return new DeduccionAdmin(
                longOrNull(resultSet.getObject(1)),
                longOrNull(resultSet.getObject(2)),
                intOrNull(resultSet.getObject(3)),
                decimalOrNull(resultSet.getObject(4)),
                decimalOrNull(resultSet.getObject(5)),
                decimalOrNull(resultSet.getObject(6)));
    }

    @FunctionalInterface
    private interface CursorReader<T> {
        T read(ResultSet resultSet) throws SQLException;
    }

    /** Resultado consolidado de {@link #consultarDetalle(String, long)}. */
    public static final class DetalleSolicitudData {

        private final SolicitudAdmin solicitud;
        private final List<RespuestaSolicitud> respuestas;
        private final DeduccionAdmin deduccion;
        private final List<HistorialSolicitudAdmin> historialSolicitud;
        private final List<HistorialDeduccionAdmin> historialDeduccion;

        public DetalleSolicitudData(SolicitudAdmin solicitud, List<RespuestaSolicitud> respuestas,
                DeduccionAdmin deduccion, List<HistorialSolicitudAdmin> historialSolicitud,
                List<HistorialDeduccionAdmin> historialDeduccion) {
            this.solicitud = solicitud;
            this.respuestas = respuestas;
            this.deduccion = deduccion;
            this.historialSolicitud = historialSolicitud;
            this.historialDeduccion = historialDeduccion;
        }

        public SolicitudAdmin getSolicitud() {
            return solicitud;
        }

        public List<RespuestaSolicitud> getRespuestas() {
            return respuestas;
        }

        public DeduccionAdmin getDeduccion() {
            return deduccion;
        }

        public List<HistorialSolicitudAdmin> getHistorialSolicitud() {
            return historialSolicitud;
        }

        public List<HistorialDeduccionAdmin> getHistorialDeduccion() {
            return historialDeduccion;
        }
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
