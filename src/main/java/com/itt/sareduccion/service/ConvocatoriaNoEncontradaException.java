package com.itt.sareduccion.service;

/** Se lanza cuando la convocatoria solicitada no existe o no esta registrada en Oracle. */
public class ConvocatoriaNoEncontradaException extends RuntimeException {

    public ConvocatoriaNoEncontradaException(String message) {
        super(message);
    }
}
