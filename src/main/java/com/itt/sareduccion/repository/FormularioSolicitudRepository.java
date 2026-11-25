package com.itt.sareduccion.repository;

import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.itt.sareduccion.auth.AuthRole;
import com.itt.sareduccion.auth.SessionPrincipal;
import com.itt.sareduccion.model.ConvocatoriaFormulario;
import com.itt.sareduccion.model.FormularioDetalle;
import com.itt.sareduccion.model.FormularioOpcion;
import com.itt.sareduccion.model.FormularioPregunta;
import com.itt.sareduccion.model.SolicitudConRespuestas;

/**
 * Acceso a Oracle del formulario de solicitud (Equipo 5). Las lecturas usan
 * {@code PKG_FORMULARIO}; el reenvio usa los procedimientos ya existentes de
 * {@code PKG_ESTUDIANTE} y {@code PKG_ASPIRANTE}. Los paquetes no hacen COMMIT:
 * cada escritura se confirma aqui o se revierte por completo.
 * La identidad siempre proviene del principal de la sesion.
 */
@Repository
public class FormularioSolicitudRepository {

    private static final String CONVOCATORIAS_CALL =
            "{call SA_REDUCCION.PKG_FORMULARIO.convocatorias_abiertas(?, ?, ?, ?, ?)}";
    private static final String FORMULARIO_CALL =
            "{call SA_REDUCCION.PKG_FORMULARIO.obtener_formulario(?, ?, ?, ?, ?)}";
    private static final String RESPUESTAS_CALL =
            "{call SA_REDUCCION.PKG_FORMULARIO.mis_respuestas(?, ?, ?, ?, ?, ?, ?)}";
    private static final String CREAR_CALL =
            "{call SA_REDUCCION.PKG_FORMULARIO.crear_solicitud(?, ?, ?, ?, ?, ?, ?, ?, ?)}";
    private static final String REENVIAR_ESTUDIANTE_CALL =
            "{call SA_REDUCCION.PKG_ESTUDIANTE.reenviar_solicitud(?, ?, ?, ?, ?)}";
    private static final String REENVIAR_ASPIRANTE_CALL =
            "{call SA_REDUCCION.PKG_ASPIRANTE.reenviar_solicitud(?, ?, ?, ?, ?, ?, ?)}";

    private final JdbcTemplate jdbcTemplate;

    public FormularioSolicitudRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Convocatorias con plazo vigente para el solicitante, agrupadas con sus tipos. */
    public List<ConvocatoriaFormulario> convocatoriasAbiertas(SessionPrincipal principal) {
        return jdbcTemplate.execute((ConnectionCallback<List<ConvocatoriaFormulario>>) connection -> {
            try (CallableStatement statement = connection.prepareCall(CONVOCATORIAS_CALL)) {
                vincularOrigen(statement, principal, 1);
                statement.registerOutParameter(5, Types.REF_CURSOR);
                statement.execute();

                Object cursor = statement.getObject(5);
                if (!(cursor instanceof ResultSet)) {
                    throw new SQLException("PKG_FORMULARIO.convocatorias_abiertas no devolvio un cursor JDBC");
                }
                Map<Long, ConvocatoriaFormulario> porId = new LinkedHashMap<Long, ConvocatoriaFormulario>();
                try (ResultSet resultSet = (ResultSet) cursor) {
                    while (resultSet.next()) {
                        Long idConvocatoria = longOrNull(resultSet.getObject(1));
                        ConvocatoriaFormulario convocatoria = porId.get(idConvocatoria);
                        if (convocatoria == null) {
                            java.sql.Date inicio = resultSet.getDate(4);
                            java.sql.Date fin = resultSet.getDate(5);
                            convocatoria = new ConvocatoriaFormulario(
                                    idConvocatoria,
                                    intOrNull(resultSet.getObject(2)),
                                    intOrNull(resultSet.getObject(3)),
                                    inicio == null ? null : inicio.toLocalDate(),
                                    fin == null ? null : fin.toLocalDate(),
                                    longOrNull(resultSet.getObject(9)),
                                    intOrNull(resultSet.getObject(10)));
                            porId.put(idConvocatoria, convocatoria);
                        }
                        convocatoria.agregarTipo(new ConvocatoriaFormulario.Tipo(
                                intOrNull(resultSet.getObject(6)),
                                resultSet.getString(7),
                                longOrNull(resultSet.getObject(8))));
                    }
                }
                return new ArrayList<ConvocatoriaFormulario>(porId.values());
            }
        });
    }

    /** Formulario del tipo de solicitud con sus preguntas y opciones (valida convocatoria y plazo). */
    public FormularioDetalle obtenerFormulario(long idConvocatoria, int idTipoSolicitud) {
        return jdbcTemplate.execute((ConnectionCallback<FormularioDetalle>) connection -> {
            try (CallableStatement statement = connection.prepareCall(FORMULARIO_CALL)) {
                statement.setLong(1, idConvocatoria);
                statement.setInt(2, idTipoSolicitud);
                statement.registerOutParameter(3, Types.REF_CURSOR);
                statement.registerOutParameter(4, Types.REF_CURSOR);
                statement.registerOutParameter(5, Types.REF_CURSOR);
                statement.execute();

                Object cursorFormulario = statement.getObject(3);
                Object cursorPreguntas = statement.getObject(4);
                Object cursorOpciones = statement.getObject(5);
                if (!(cursorFormulario instanceof ResultSet) || !(cursorPreguntas instanceof ResultSet)
                        || !(cursorOpciones instanceof ResultSet)) {
                    throw new SQLException("PKG_FORMULARIO.obtener_formulario no devolvio cursores JDBC");
                }

                FormularioDetalle detalle = null;
                try (ResultSet resultSet = (ResultSet) cursorFormulario) {
                    if (resultSet.next()) {
                        detalle = new FormularioDetalle(
                                longOrNull(resultSet.getObject(1)),
                                resultSet.getString(2),
                                resultSet.getString(3),
                                resultSet.getString(4),
                                intOrNull(resultSet.getObject(5)));
                    }
                }
                if (detalle == null) {
                    throw new SQLException("PKG_FORMULARIO.obtener_formulario no devolvio el formulario");
                }

                Map<Long, FormularioPregunta> preguntas = new LinkedHashMap<Long, FormularioPregunta>();
                try (ResultSet resultSet = (ResultSet) cursorPreguntas) {
                    while (resultSet.next()) {
                        FormularioPregunta pregunta = new FormularioPregunta(
                                longOrNull(resultSet.getObject(1)),
                                resultSet.getString(2),
                                "S".equalsIgnoreCase(resultSet.getString(3)),
                                resultSet.getString(5),
                                intOrNull(resultSet.getObject(6)));
                        detalle.agregarPregunta(pregunta);
                        preguntas.put(pregunta.getIdPregunta(), pregunta);
                    }
                }
                try (ResultSet resultSet = (ResultSet) cursorOpciones) {
                    while (resultSet.next()) {
                        FormularioPregunta pregunta = preguntas.get(longOrNull(resultSet.getObject(2)));
                        if (pregunta != null) {
                            Integer requiere = intOrNull(resultSet.getObject(5));
                            pregunta.agregarOpcion(new FormularioOpcion(
                                    longOrNull(resultSet.getObject(1)),
                                    resultSet.getString(3),
                                    intOrNull(resultSet.getObject(4)),
                                    requiere != null && requiere.intValue() == 1));
                        }
                    }
                }
                return detalle;
            }
        });
    }

    /** Datos y respuestas de una solicitud propia; Oracle rechaza (ORA-20941) las ajenas o inexistentes. */
    public SolicitudConRespuestas misRespuestas(SessionPrincipal principal, long idSolicitud) {
        return jdbcTemplate.execute((ConnectionCallback<SolicitudConRespuestas>) connection -> {
            try (CallableStatement statement = connection.prepareCall(RESPUESTAS_CALL)) {
                vincularOrigen(statement, principal, 1);
                statement.setLong(5, idSolicitud);
                statement.registerOutParameter(6, Types.REF_CURSOR);
                statement.registerOutParameter(7, Types.REF_CURSOR);
                statement.execute();

                Object cursorDatos = statement.getObject(6);
                Object cursorRespuestas = statement.getObject(7);
                if (!(cursorDatos instanceof ResultSet) || !(cursorRespuestas instanceof ResultSet)) {
                    throw new SQLException("PKG_FORMULARIO.mis_respuestas no devolvio cursores JDBC");
                }

                SolicitudConRespuestas solicitud = null;
                try (ResultSet resultSet = (ResultSet) cursorDatos) {
                    if (resultSet.next()) {
                        Timestamp fecha = resultSet.getTimestamp(6);
                        Timestamp actualizacion = resultSet.getTimestamp(7);
                        solicitud = new SolicitudConRespuestas(
                                longOrNull(resultSet.getObject(1)),
                                longOrNull(resultSet.getObject(2)),
                                intOrNull(resultSet.getObject(3)),
                                longOrNull(resultSet.getObject(4)),
                                intOrNull(resultSet.getObject(5)),
                                fecha == null ? null : fecha.toLocalDateTime(),
                                actualizacion == null ? null : actualizacion.toLocalDateTime(),
                                resultSet.getString(8));
                    }
                }
                if (solicitud == null) {
                    throw new SQLException("PKG_FORMULARIO.mis_respuestas no devolvio la solicitud");
                }
                try (ResultSet resultSet = (ResultSet) cursorRespuestas) {
                    while (resultSet.next()) {
                        solicitud.agregarRespuesta(new SolicitudConRespuestas.RespuestaGuardada(
                                longOrNull(resultSet.getObject(1)),
                                longOrNull(resultSet.getObject(2)),
                                resultSet.getString(3)));
                    }
                }
                return solicitud;
            }
        });
    }

    /** Envio inicial atomico: crea la solicitud, sus respuestas y el historial inicial. */
    public long crearSolicitud(SessionPrincipal principal, long idConvocatoria, int idTipoSolicitud,
            long idFormulario, String respuestasJson) {
        Long id = jdbcTemplate.execute((ConnectionCallback<Long>) connection -> {
            boolean autoCommitAnterior = connection.getAutoCommit();
            connection.setAutoCommit(false);
            Clob clob = null;
            try (CallableStatement statement = connection.prepareCall(CREAR_CALL)) {
                vincularOrigen(statement, principal, 1);
                statement.setLong(5, idConvocatoria);
                statement.setInt(6, idTipoSolicitud);
                statement.setLong(7, idFormulario);
                clob = crearClob(connection, respuestasJson);
                statement.setClob(8, clob);
                statement.registerOutParameter(9, Types.NUMERIC);
                statement.execute();

                Number nuevo = (Number) statement.getObject(9);
                if (nuevo == null) {
                    throw new SQLException("PKG_FORMULARIO.crear_solicitud no devolvio el id");
                }
                connection.commit();
                return Long.valueOf(nuevo.longValue());
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                liberar(clob);
                connection.setAutoCommit(autoCommitAnterior);
            }
        });
        return id.longValue();
    }

    /** Reenvio atomico: actualiza tipo y respuestas y deja la solicitud en PENDIENTE. */
    public void reenviarSolicitud(SessionPrincipal principal, long idSolicitud, int idTipoSolicitud,
            long idFormulario, String respuestasJson) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            boolean autoCommitAnterior = connection.getAutoCommit();
            connection.setAutoCommit(false);
            Clob clob = null;
            try {
                clob = crearClob(connection, respuestasJson);
                if (principal.getRole() == AuthRole.STUDENT) {
                    try (CallableStatement statement = connection.prepareCall(REENVIAR_ESTUDIANTE_CALL)) {
                        statement.setString(1, principal.getIdentity());
                        statement.setLong(2, idSolicitud);
                        statement.setInt(3, idTipoSolicitud);
                        statement.setLong(4, idFormulario);
                        statement.setClob(5, clob);
                        statement.execute();
                    }
                } else if (principal.getRole() == AuthRole.APPLICANT) {
                    try (CallableStatement statement = connection.prepareCall(REENVIAR_ASPIRANTE_CALL)) {
                        statement.setString(1, principal.getIdentity());
                        statement.setInt(2, principal.getPeriodo().intValue());
                        statement.setInt(3, principal.getAnio().intValue());
                        statement.setLong(4, idSolicitud);
                        statement.setInt(5, idTipoSolicitud);
                        statement.setLong(6, idFormulario);
                        statement.setClob(7, clob);
                        statement.execute();
                    }
                } else {
                    throw new IllegalArgumentException("Rol no permitido para reenviar solicitudes");
                }
                connection.commit();
                return null;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                liberar(clob);
                connection.setAutoCommit(autoCommitAnterior);
            }
        });
    }

    /**
     * Enlaza los cuatro parametros de origen (numero de control, ficha, periodo,
     * anio) a partir de la sesion: estudiante o aspirante, nunca ambos.
     */
    private static void vincularOrigen(CallableStatement statement, SessionPrincipal principal, int primero)
            throws SQLException {
        if (principal.getRole() == AuthRole.STUDENT) {
            statement.setString(primero, principal.getIdentity());
            statement.setNull(primero + 1, Types.VARCHAR);
            statement.setNull(primero + 2, Types.NUMERIC);
            statement.setNull(primero + 3, Types.NUMERIC);
        } else if (principal.getRole() == AuthRole.APPLICANT) {
            statement.setNull(primero, Types.VARCHAR);
            statement.setString(primero + 1, principal.getIdentity());
            statement.setInt(primero + 2, principal.getPeriodo().intValue());
            statement.setInt(primero + 3, principal.getAnio().intValue());
        } else {
            throw new IllegalArgumentException("Rol no permitido para el formulario de solicitud");
        }
    }

    private static Clob crearClob(Connection connection, String texto) throws SQLException {
        Clob clob = connection.createClob();
        clob.setString(1, texto);
        return clob;
    }

    private static void liberar(Clob clob) {
        if (clob != null) {
            try {
                clob.free();
            } catch (SQLException ignorada) {
                // liberar el CLOB temporal es solo limpieza
            }
        }
    }

    private static Long longOrNull(Object value) {
        return value instanceof Number ? Long.valueOf(((Number) value).longValue()) : null;
    }

    private static Integer intOrNull(Object value) {
        return value instanceof Number ? Integer.valueOf(((Number) value).intValue()) : null;
    }
}
