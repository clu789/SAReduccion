package com.itt.sareduccion.repository;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdministradorRepository {

    private static final String VALIDATE_ADMIN_CALL =
            "{call SA_REDUCCION.PKG_ADMINISTRADOR.listar_estudiantes(?, ?)}";

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
}