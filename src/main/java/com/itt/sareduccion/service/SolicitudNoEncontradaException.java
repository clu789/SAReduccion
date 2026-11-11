package com.itt.sareduccion.service;

/**
 * La solicitud no existe o no pertenece al solicitante de la sesion. Ambos
 * casos responden igual (HTTP 404) para no revelar folios ajenos.
 */
public class SolicitudNoEncontradaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SolicitudNoEncontradaException() {
        super("Solicitud no encontrada");
    }
}
