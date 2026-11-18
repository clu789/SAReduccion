package com.itt.sareduccion.repository;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.itt.sareduccion.model.ApplicantIdentity;

/**
 * Ejecuta la validacion provisional de aspirantes mediante el paquete
 * autorizado {@code SA_REDUCCION.PKG_ASPIRANTE} con la cuenta tecnica del
 * pool ({@code SAR_BACKEND}). Usa parametros enlazados: nunca concatena valores.
 * El ano vigente lo determina Oracle ({@code EXTRACT(YEAR FROM SYSDATE)}).
 */
@Repository
public class ApplicantRepository {

    private static final String VALIDAR_ASPIRANTE_CALL =
            "{call SA_REDUCCION.PKG_ASPIRANTE.validar_aspirante(?, ?, ?, ?, ?)}";
    private static final String CORREO_ASPIRANTE_CALL =
            "{? = call SA_REDUCCION.PKG_SEGURIDAD.correo_aspirante(?, ?, ?)}";

    private final JdbcTemplate jdbcTemplate;

    public ApplicantRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ApplicantIdentity> validar(String ficha, String curp) {
        if (ficha == null || ficha.trim().isEmpty() || curp == null || curp.trim().isEmpty()) {
            return Optional.empty();
        }
        return jdbcTemplate.execute((ConnectionCallback<Optional<ApplicantIdentity>>) connection ->
                validar(connection, ficha.trim(), curp.trim()));
    }

    /**
     * Correo registrado del aspirante ya validado; se usa solo para enviar el
     * codigo temporal de acceso. Devuelve {@code null} si no tiene correo.
     */
    public String correoRegistrado(String ficha, int periodo, int anio) {
        return jdbcTemplate.execute((ConnectionCallback<String>) connection -> {
            try (CallableStatement statement = connection.prepareCall(CORREO_ASPIRANTE_CALL)) {
                statement.registerOutParameter(1, Types.VARCHAR);
                statement.setString(2, ficha);
                statement.setInt(3, periodo);
                statement.setInt(4, anio);
                statement.execute();
                return statement.getString(1);
            }
        });
    }

    private Optional<ApplicantIdentity> validar(Connection connection, String ficha, String curp)
            throws SQLException {
        try (CallableStatement statement = connection.prepareCall(VALIDAR_ASPIRANTE_CALL)) {
            statement.setString(1, ficha);
            statement.setString(2, curp);
            statement.registerOutParameter(3, Types.VARCHAR);
            statement.registerOutParameter(4, Types.NUMERIC);
            statement.registerOutParameter(5, Types.NUMERIC);
            statement.execute();

            String fichaOut = statement.getString(3);
            int periodo = statement.getInt(4);
            int anio = statement.getInt(5);
            if (fichaOut == null || statement.wasNull()) {
                return Optional.empty();
            }
            return Optional.of(new ApplicantIdentity(fichaOut, periodo, anio));
        }
    }
}
