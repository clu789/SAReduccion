package com.itt.sareduccion.repository;

import java.io.StringReader;
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
import java.util.Optional;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.itt.sareduccion.model.ConvocatoriaDisponible;
import com.itt.sareduccion.model.DeduccionPropia;
import com.itt.sareduccion.model.HistorialPropio;
import com.itt.sareduccion.model.IdentidadSolicitante;
import com.itt.sareduccion.model.PerfilSolicitante;
import com.itt.sareduccion.model.SolicitudPropia;

/**
 * API del panel del solicitante (equipo 3) sobre {@code PKG_ESTUDIANTE} y
 * {@code PKG_ASPIRANTE}. El paquete se elige segun la identidad de la sesion y
 * todos los valores se enlazan como parametros. Las escrituras confirman solo
 * si el procedimiento termina bien.
 */
@Repository
public class SolicitanteRepository {

    private static final String ESTUDIANTE = "SA_REDUCCION.PKG_ESTUDIANTE.";
    private static final String ASPIRANTE = "SA_REDUCCION.PKG_ASPIRANTE.";

    private final JdbcTemplate jdbcTemplate;

    public SolicitanteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<PerfilSolicitante> perfil(IdentidadSolicitante identidad) {
        List<PerfilSolicitante> filas = consultar(identidad, "mis_datos", resultSet -> identidad.esEstudiante()
                ? new PerfilSolicitante("ESTUDIANTE", resultSet.getString(1), resultSet.getString(2),
                        resultSet.getString(3), resultSet.getString(4), resultSet.getString(5),
                        intOrNull(resultSet.getObject(6)), null, null, null)
                : new PerfilSolicitante("ASPIRANTE", resultSet.getString(1), resultSet.getString(2),
                        resultSet.getString(3), resultSet.getString(4), null,
                        intOrNull(resultSet.getObject(5)), intOrNull(resultSet.getObject(6)),
                        resultSet.getString(7), intOrNull(resultSet.getObject(8))));
        return filas.isEmpty() ? Optional.empty() : Optional.of(filas.get(0));
    }

    public List<SolicitudPropia> solicitudes(IdentidadSolicitante identidad) {
        return consultar(identidad, "mis_solicitudes", resultSet -> new SolicitudPropia(
                longOrNull(resultSet.getObject(1)),
                longOrNull(resultSet.getObject(2)),
                intOrNull(resultSet.getObject(3)),
                intOrNull(resultSet.getObject(4)),
                dateTimeOrNull(resultSet.getTimestamp(5)),
                dateTimeOrNull(resultSet.getTimestamp(6)),
                resultSet.getString(7),
                resultSet.getString(8),
                longOrNull(resultSet.getObject(9)),
                intOrNull(resultSet.getObject(10)),
                resultSet.getString(11),
                intOrNull(resultSet.getObject(12)),
                dateOrNull(resultSet.getDate(13)),
                dateOrNull(resultSet.getDate(14)),
                "S".equals(resultSet.getString(15))));
    }

    public List<HistorialPropio> historial(IdentidadSolicitante identidad) {
        return consultar(identidad, "mi_historial", resultSet -> new HistorialPropio(
                longOrNull(resultSet.getObject(1)),
                longOrNull(resultSet.getObject(2)),
                intOrNull(resultSet.getObject(3)),
                resultSet.getString(4),
                resultSet.getString(5),
                dateTimeOrNull(resultSet.getTimestamp(6)),
                "S".equals(resultSet.getString(7))));
    }

    public List<DeduccionPropia> deducciones(IdentidadSolicitante identidad) {
        return consultar(identidad, "mis_deducciones", resultSet -> new DeduccionPropia(
                longOrNull(resultSet.getObject(1)),
                longOrNull(resultSet.getObject(2)),
                intOrNull(resultSet.getObject(3)),
                decimalOrNull(resultSet.getObject(4)),
                decimalOrNull(resultSet.getObject(5)),
                decimalOrNull(resultSet.getObject(6))));
    }

    public List<ConvocatoriaDisponible> convocatorias(IdentidadSolicitante identidad) {
        return consultar(identidad, "convocatorias_disponibles", resultSet -> new ConvocatoriaDisponible(
                longOrNull(resultSet.getObject(1)),
                intOrNull(resultSet.getObject(2)),
                resultSet.getString(3),
                intOrNull(resultSet.getObject(4)),
                dateOrNull(resultSet.getDate(5)),
                dateOrNull(resultSet.getDate(6)),
                intOrNull(resultSet.getObject(7)),
                resultSet.getString(8),
                resultSet.getString(9),
                longOrNull(resultSet.getObject(10)),
                resultSet.getString(11),
                decimalOrNull(resultSet.getObject(12)),
                longOrNull(resultSet.getObject(13))));
    }

    /** Retira una solicitud PENDIENTE propia (pasa a RETIRADO). */
    public void retirar(IdentidadSolicitante identidad, long idSolicitud) {
        ejecutarEscritura(connection -> {
            String llamada = identidad.esEstudiante()
                    ? "{call " + ESTUDIANTE + "retirar_solicitud(?, ?)}"
                    : "{call " + ASPIRANTE + "retirar_solicitud(?, ?, ?, ?)}";
            try (CallableStatement statement = connection.prepareCall(llamada)) {
                int siguiente = enlazarIdentidad(statement, identidad);
                statement.setLong(siguiente, idSolicitud);
                statement.execute();
            }
        });
    }

    /**
     * Reenvia una solicitud RETIRADO, RECHAZADO o CANCELADO propia: vuelve a
     * PENDIENTE y sustituye tipo, formulario y respuestas en la misma operacion.
     */
    public void reenviar(IdentidadSolicitante identidad, long idSolicitud, int idTipoSolicitud,
            long idFormulario, String respuestasJson) {
        ejecutarEscritura(connection -> {
            String llamada = identidad.esEstudiante()
                    ? "{call " + ESTUDIANTE + "reenviar_solicitud(?, ?, ?, ?, ?)}"
                    : "{call " + ASPIRANTE + "reenviar_solicitud(?, ?, ?, ?, ?, ?, ?)}";
            try (CallableStatement statement = connection.prepareCall(llamada)) {
                int siguiente = enlazarIdentidad(statement, identidad);
                statement.setLong(siguiente, idSolicitud);
                statement.setInt(siguiente + 1, idTipoSolicitud);
                statement.setLong(siguiente + 2, idFormulario);
                statement.setClob(siguiente + 3, new StringReader(respuestasJson));
                statement.execute();
            }
        });
    }

    private <T> List<T> consultar(IdentidadSolicitante identidad, String procedimiento, FilaMapper<T> mapper) {
        return jdbcTemplate.execute((ConnectionCallback<List<T>>) connection -> {
            String llamada = identidad.esEstudiante()
                    ? "{call " + ESTUDIANTE + procedimiento + "(?, ?)}"
                    : "{call " + ASPIRANTE + procedimiento + "(?, ?, ?, ?)}";
            try (CallableStatement statement = connection.prepareCall(llamada)) {
                int posicionCursor = enlazarIdentidad(statement, identidad);
                statement.registerOutParameter(posicionCursor, Types.REF_CURSOR);
                statement.execute();

                Object cursor = statement.getObject(posicionCursor);
                if (!(cursor instanceof ResultSet)) {
                    throw new SQLException(procedimiento + " no devolvio un cursor JDBC");
                }
                List<T> filas = new ArrayList<T>();
                try (ResultSet resultSet = (ResultSet) cursor) {
                    while (resultSet.next()) {
                        filas.add(mapper.mapear(resultSet));
                    }
                }
                return filas;
            }
        });
    }

    private void ejecutarEscritura(Escritura escritura) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            boolean autoCommitAnterior = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                escritura.ejecutar(connection);
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

    /** Enlaza la identidad y devuelve la posicion del siguiente parametro. */
    private int enlazarIdentidad(CallableStatement statement, IdentidadSolicitante identidad) throws SQLException {
        if (identidad.esEstudiante()) {
            statement.setString(1, identidad.getNumeroControl());
            return 2;
        }
        statement.setString(1, identidad.getFicha());
        statement.setInt(2, identidad.getPeriodo());
        statement.setInt(3, identidad.getAnio());
        return 4;
    }

    private Long longOrNull(Object value) {
        return value instanceof Number ? Long.valueOf(((Number) value).longValue()) : null;
    }

    private Integer intOrNull(Object value) {
        return value instanceof Number ? Integer.valueOf(((Number) value).intValue()) : null;
    }

    private BigDecimal decimalOrNull(Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        return null;
    }

    private LocalDateTime dateTimeOrNull(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private LocalDate dateOrNull(java.sql.Date value) {
        return value == null ? null : value.toLocalDate();
    }

    @FunctionalInterface
    private interface FilaMapper<T> {
        T mapear(ResultSet resultSet) throws SQLException;
    }

    @FunctionalInterface
    private interface Escritura {
        void ejecutar(Connection connection) throws SQLException;
    }
}
