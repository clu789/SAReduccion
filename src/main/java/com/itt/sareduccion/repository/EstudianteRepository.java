package com.itt.sareduccion.repository;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.itt.sareduccion.model.EstudianteDatos;

@Repository
public class EstudianteRepository {

	private static final String MIS_DATOS_CALL =
			"{call SA_REDUCCION.PKG_ESTUDIANTE.mis_datos(?, ?)}";

	private final JdbcTemplate jdbcTemplate;

	public EstudianteRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Optional<EstudianteDatos> misDatos(String numeroControl) {
		if (numeroControl == null || numeroControl.trim().isEmpty()) {
			throw new IllegalArgumentException("El numero de control es obligatorio");
		}

		return jdbcTemplate.execute((ConnectionCallback<Optional<EstudianteDatos>>) connection ->
			consultarDatos(connection, numeroControl.trim()));
	}

	private Optional<EstudianteDatos> consultarDatos(Connection connection, String numeroControl)
			throws SQLException {
		try (CallableStatement statement = connection.prepareCall(MIS_DATOS_CALL)) {
			statement.setString(1, numeroControl);
			statement.registerOutParameter(2, Types.REF_CURSOR);
			statement.execute();

			Object cursor = statement.getObject(2);
			if (!(cursor instanceof ResultSet)) {
				throw new SQLException("PKG_ESTUDIANTE.mis_datos no devolvio un cursor JDBC");
			}

			try (ResultSet resultSet = (ResultSet) cursor) {
				if (!resultSet.next()) {
					return Optional.empty();
				}

				Number semestreValue = (Number) resultSet.getObject(6);
				Integer semestre = semestreValue == null ? null : semestreValue.intValue();
				return Optional.of(new EstudianteDatos(
						resultSet.getString(1),
						resultSet.getString(2),
						resultSet.getString(3),
						resultSet.getString(4),
						resultSet.getString(5),
						semestre));
			}
		}
	}
}