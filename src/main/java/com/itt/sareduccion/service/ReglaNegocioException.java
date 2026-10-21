package com.itt.sareduccion.service;

/**
 * Violacion de una regla de negocio esperada (por ejemplo, intentar aprobar
 * una solicitud que no esta pendiente). El controller la traduce a HTTP 409
 * con un mensaje generico: nunca expone el error de Oracle.
 */
public class ReglaNegocioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ReglaNegocioException(String message) {
        super(message);
    }
}
