package com.itt.sareduccion.service;

import java.sql.SQLException;

import org.springframework.dao.DataAccessException;

/**
 * Traduce los errores de Oracle del formulario a excepciones de negocio. El
 * driver JDBC entrega el codigo en positivo (20812 para ORA-20812), por lo que
 * se compara el valor absoluto. Nunca se expone el texto original de Oracle.
 * Si el error no es conocido se devuelve la excepcion original (HTTP 500).
 */
final class TraductorErroresOracle {

    private static final String MENSAJE_VALIDACION =
            "Las respuestas no son válidas. Revise las preguntas obligatorias y los formatos.";

    private TraductorErroresOracle() {
    }

    static RuntimeException traducir(DataAccessException exception) {
        Integer codigo = codigoOracle(exception);
        if (codigo == null) {
            return exception;
        }
        int ora = Math.abs(codigo.intValue());
        switch (ora) {
        case 1:
            return new ReglaNegocioException("Ya existe una solicitud para esta convocatoria.");
        case 1403:
            return new ReglaNegocioException("No se encontró la información solicitada.");
        case 20812:
            return new ReglaNegocioException(
                    "El periodo de recepción no está abierto o el tipo de solicitud no está disponible.");
        case 20940:
            return new ReglaNegocioException("El formulario no está disponible para el tipo de solicitud elegido.");
        case 20862:
            return new ReglaNegocioException(
                    "Solo se pueden reenviar solicitudes retiradas, rechazadas o canceladas.");
        case 20941:
        case 20858:
        case 20861:
            return new SolicitudNoEncontradaException("Solicitud no encontrada");
        case 20810:
        case 20841:
        case 20842:
        case 20856:
        case 20857:
        case 20942:
            return new AccesoDenegadoException("Acceso no permitido");
        default:
            break;
        }
        if ((ora >= 20850 && ora <= 20855) || (ora >= 20001 && ora <= 20099)) {
            return new ValidacionRespuestasException(MENSAJE_VALIDACION, null);
        }
        return exception;
    }

    /** Primer codigo de error de Oracle de la cadena de causas, o null si no hay ninguno. */
    static Integer codigoOracle(Throwable exception) {
        for (Throwable actual = exception; actual != null; actual = actual.getCause()) {
            if (actual instanceof SQLException) {
                return Integer.valueOf(((SQLException) actual).getErrorCode());
            }
        }
        return null;
    }
}
