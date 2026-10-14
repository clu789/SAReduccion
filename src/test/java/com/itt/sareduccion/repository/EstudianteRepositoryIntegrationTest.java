package com.itt.sareduccion.repository;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.itt.sareduccion.model.EstudianteDatos;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SAR_JDBC_TEST_NUMERO_CONTROL", matches = ".+")
class EstudianteRepositoryIntegrationTest {

	@Autowired
	private EstudianteRepository estudianteRepository;

	@Test
	void invokesMisDatosForConfiguredStudent() {
		String numeroControl = System.getenv("SAR_JDBC_TEST_NUMERO_CONTROL").trim();
		EstudianteDatos estudiante = estudianteRepository.misDatos(numeroControl)
				.orElseThrow(() -> new AssertionError("PKG_ESTUDIANTE.mis_datos no devolvio un estudiante registrado"));

		assertAll(
				() -> assertEquals(numeroControl, estudiante.getNumeroControl()),
				() -> assertEquals("ESTUDIANTE DE PRUEBA", estudiante.getNombre()),
				() -> assertEquals("PRUEBA JDBC", estudiante.getPrimerApellido()),
				() -> assertEquals("INTEGRACION JDBC", estudiante.getCarrera()),
				() -> assertEquals(Integer.valueOf(1), estudiante.getSemestre()));
	}
}