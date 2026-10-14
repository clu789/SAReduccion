package com.itt.sareduccion.auth;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OracleCredentialValidator {

    private static final Pattern ORACLE_USERNAME = Pattern.compile("[A-Za-z][A-Za-z0-9_$#]{0,29}");

    private final String jdbcUrl;

    public OracleCredentialValidator(@Value("${spring.datasource.url}") String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public OracleIdentity authenticate(String username, String password) {
        if (username == null || password == null || password.isEmpty()
                || !ORACLE_USERNAME.matcher(username).matches()) {
            throw new AuthenticationRejectedException();
        }

        String expectedUsername = username.toUpperCase(Locale.ROOT);
        try (Connection connection = DriverManager.getConnection(jdbcUrl, expectedUsername, password)) {
            String sessionUsername = readSessionUsername(connection);
            if (!expectedUsername.equals(sessionUsername)) {
                throw new AuthenticationRejectedException();
            }
            return new OracleIdentity(sessionUsername, readEnabledRoles(connection));
        } catch (SQLException exception) {
            if (isCredentialFailure(exception)) {
                throw new AuthenticationRejectedException();
            }
            throw new AuthenticationUnavailableException();
        }
    }

    private String readSessionUsername(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT SYS_CONTEXT('USERENV', 'SESSION_USER') FROM dual")) {
            if (!resultSet.next()) {
                throw new SQLException("Oracle no devolvio SESSION_USER");
            }
            return resultSet.getString(1);
        }
    }

    private Set<String> readEnabledRoles(Connection connection) throws SQLException {
        Set<String> roles = new HashSet<>();
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT role FROM session_roles")) {
            while (resultSet.next()) {
                roles.add(resultSet.getString(1));
            }
        }
        return roles;
    }

    private boolean isCredentialFailure(SQLException exception) {
        for (SQLException current = exception; current != null; current = current.getNextException()) {
            int code = current.getErrorCode();
            if (code == 1017 || code == 28000 || code == 28001) {
                return true;
            }
        }
        return false;
    }

    public static final class OracleIdentity {

        private final String username;
        private final Set<String> roles;

        private OracleIdentity(String username, Set<String> roles) {
            this.username = username;
            this.roles = roles;
        }

        public String getUsername() {
            return username;
        }

        public boolean hasRole(String role) {
            return roles.contains(role);
        }
    }

    public static final class AuthenticationRejectedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class AuthenticationUnavailableException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}