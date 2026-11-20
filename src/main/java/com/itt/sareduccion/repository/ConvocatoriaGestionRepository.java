
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

    private static final String ACTUALIZAR_FECHAS_CALL =
            "{call SA_REDUCCION.PKG_ADMINISTRADOR.actualizar_fechas_convocatoria(?, ?, ?, ?, ?, ?)}";
    private static final String ACTUALIZAR_MONTO_CALL =
            "{call SA_REDUCCION.PKG_ADMINISTRADOR.actualizar_monto_oficial(?, ?, ?, ?)}";

    // IDs del catalogo oficial Tipo_Solicitudes: 1 trabajador, 2 homologado.
    private static final int TIPO_TRABAJADOR = 1;
    private static final int TIPO_HOMOLOGADO = 2;

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

        // La lectura por cursores no bloquea filas: las ediciones simultaneas
        // requieren una operacion atomica en PL/SQL para proteccion completa.
        return jdbcTemplate.execute((ConnectionCallback<Map<String, Object>>) connection -> {
            boolean autoCommitAnterior = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                // Leer mediante el paquete autorizado: SAR_BACKEND solo tiene
                // EXECUTE sobre PKG_ADMINISTRADOR, no SELECT sobre las tablas.
                LocalDate inicioActual = null;
                LocalDate finActual = null;
                LocalDate autorizacionActual = null;
                LocalDate aplicacionActual = null;
                boolean encontrada = false;

                try (CallableStatement consulta = connection.prepareCall(LISTAR_CONVOCATORIAS_CALL)) {
                    consulta.setString(1, idAdministrador);
                    consulta.registerOutParameter(2, Types.REF_CURSOR);
                    consulta.execute();
                    try (ResultSet resultado = (ResultSet) consulta.getObject(2)) {
                        while (resultado.next()) {
                            if (resultado.getLong("ID_CONVOCATORIA") == idConvocatoria.longValue()) {
                                encontrada = true;
                                inicioActual = obtenerFecha(resultado, "FECHA_INICIO_SOLICITUDES");
                                finActual = obtenerFecha(resultado, "FECHA_FIN_SOLICITUDES");
                                autorizacionActual = obtenerFecha(resultado, "FECHA_LIMITE_AUTORIZACION");
                                aplicacionActual = obtenerFecha(resultado, "FECHA_LIMITE_APLICACION");
                                break;
                            }
                        }
                    }
                }
                if (!encontrada) {
                    throw new SQLException("Convocatoria no encontrada: " + idConvocatoria);
                }

                BigDecimal trabajadorActual = null;
                BigDecimal homologadoActual = null;
                int encontrados = 0;
                try (CallableStatement consulta = connection.prepareCall(CONSULTAR_MONTOS_CONVOCATORIA_CALL)) {
                    consulta.setString(1, idAdministrador);
                    consulta.setLong(2, idConvocatoria);
                    consulta.registerOutParameter(3, Types.REF_CURSOR);
                    consulta.execute();
                    try (ResultSet resultado = (ResultSet) consulta.getObject(3)) {
                        while (resultado.next()) {
                            int tipo = resultado.getInt("ID_TIPO_SOLICITUD");
                            if (tipo == TIPO_TRABAJADOR) {
                                trabajadorActual = resultado.getBigDecimal("MONTO_DEDUCCION");
                                encontrados++;
                            } else if (tipo == TIPO_HOMOLOGADO) {
                                homologadoActual = resultado.getBigDecimal("MONTO_DEDUCCION");
                                encontrados++;
                            }
                        }
                    }
                }
                if (encontrados != 2 || trabajadorActual == null || homologadoActual == null) {
                    throw new SQLException("La convocatoria no tiene configurados ambos montos oficiales");
                }

                boolean fechasModificadas =
                        !fechaInicio.equals(inicioActual)
                        || !fechaFin.equals(finActual)
                        || !fechaLimiteAutorizacion.equals(autorizacionActual)
                        || !fechaLimiteAplicacion.equals(aplicacionActual);
                boolean trabajadorModificado = montoTrabajador.compareTo(trabajadorActual) != 0;
                boolean homologadoModificado = montoHomologado.compareTo(homologadoActual) != 0;

                if (fechasModificadas) {
                    try (CallableStatement statement =
                            connection.prepareCall(ACTUALIZAR_FECHAS_CALL)) {
                        statement.setString(1, idAdministrador);
                        statement.setLong(2, idConvocatoria);
                        statement.setDate(3, Date.valueOf(fechaInicio));
                        statement.setDate(4, Date.valueOf(fechaFin));
                        statement.setDate(5, Date.valueOf(fechaLimiteAutorizacion));
                        statement.setDate(6, Date.valueOf(fechaLimiteAplicacion));
                        statement.execute();
                    }
                }

                if (trabajadorModificado) {
                    actualizarMontoEnConexion(connection, idAdministrador,
                            idConvocatoria, TIPO_TRABAJADOR, montoTrabajador);
                }
                if (homologadoModificado) {
                    actualizarMontoEnConexion(connection, idAdministrador,
                            idConvocatoria, TIPO_HOMOLOGADO, montoHomologado);
                }

                connection.commit();

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

    private void actualizarMontoEnConexion(
            java.sql.Connection connection,
            String idAdministrador,
            Long idConvocatoria,
            int tipo,
            BigDecimal monto) throws SQLException {
        try (CallableStatement statement = connection.prepareCall(ACTUALIZAR_MONTO_CALL)) {
            statement.setString(1, idAdministrador);
            statement.setLong(2, idConvocatoria);
            statement.setInt(3, tipo);
            statement.setBigDecimal(4, monto);
            statement.execute();
        }
    }

    private LocalDate obtenerFecha(
            ResultSet resultado,
            String columna) throws SQLException {

        Date fecha = resultado.getDate(columna);

        return fecha != null ? fecha.toLocalDate() : null;
    }
}
