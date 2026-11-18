package com.itt.sareduccion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.UncategorizedSQLException;

/** Pruebas de la traduccion de errores de Oracle (el driver entrega el codigo en positivo). */
class TraductorErroresOracleTest {

    private static DataAccessException oracle(int codigo) {
        return new UncategorizedSQLException("call", "sql", new SQLException("ORA-" + codigo, "72000", codigo));
    }

    @Test
    void plazoCerradoEsConflicto() {
        assertTrue(TraductorErroresOracle.traducir(oracle(20812)) instanceof ReglaNegocioException);
    }

    @Test
    void tambienSeEntiendeElCodigoNegativo() {
        assertTrue(TraductorErroresOracle.traducir(oracle(-20812)) instanceof ReglaNegocioException);
    }

    @Test
    void solicitudAjenaOInexistenteEsNoEncontrada() {
        assertTrue(TraductorErroresOracle.traducir(oracle(20941)) instanceof SolicitudNoEncontradaException);
        assertTrue(TraductorErroresOracle.traducir(oracle(20858)) instanceof SolicitudNoEncontradaException);
    }

    @Test
    void perfilInvalidoEsAccesoDenegado() {
        assertTrue(TraductorErroresOracle.traducir(oracle(20810)) instanceof AccesoDenegadoException);
        assertTrue(TraductorErroresOracle.traducir(oracle(20841)) instanceof AccesoDenegadoException);
    }

    @Test
    void erroresDeRespuestasSonValidacion() {
        assertTrue(TraductorErroresOracle.traducir(oracle(20853)) instanceof ValidacionRespuestasException);
        assertTrue(TraductorErroresOracle.traducir(oracle(20015)) instanceof ValidacionRespuestasException);
    }

    @Test
    void solicitudDuplicadaEsConflicto() {
        assertTrue(TraductorErroresOracle.traducir(oracle(1)) instanceof ReglaNegocioException);
    }

    @Test
    void estadoNoReenviableEsConflicto() {
        assertTrue(TraductorErroresOracle.traducir(oracle(20862)) instanceof ReglaNegocioException);
    }

    @Test
    void unErrorDesconocidoSeDevuelveSinCambios() {
        DataAccessException original = oracle(600);
        assertSame(original, TraductorErroresOracle.traducir(original));
    }

    @Test
    void sinCodigoDeOracleSeDevuelveSinCambios() {
        DataAccessException original = new DataAccessException("x", new RuntimeException("sin sql")) { };
        assertSame(original, TraductorErroresOracle.traducir(original));
        assertEquals(null, TraductorErroresOracle.codigoOracle(original));
    }

    @Test
    void elMensajeNoExponeElTextoDeOracle() {
        String mensaje = TraductorErroresOracle.traducir(oracle(20812)).getMessage();
        assertTrue(!mensaje.contains("ORA-"));
    }
}
