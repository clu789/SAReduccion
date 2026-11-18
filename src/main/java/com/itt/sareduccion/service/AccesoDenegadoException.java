package com.itt.sareduccion.service;

/** El perfil de la sesion no es valido para la operacion (HTTP 403). */
public class AccesoDenegadoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AccesoDenegadoException(String message) {
        super(message);
    }
}
