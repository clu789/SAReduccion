package com.itt.sareduccion.model;

/** Opcion de una pregunta de seleccion (OPCION_UNICA o MIXTA). */
public final class FormularioOpcion {

    private final Long idOpcion;
    private final String texto;
    private final Integer orden;
    private final boolean requiereEspecificacion;

    public FormularioOpcion(Long idOpcion, String texto, Integer orden, boolean requiereEspecificacion) {
        this.idOpcion = idOpcion;
        this.texto = texto;
        this.orden = orden;
        this.requiereEspecificacion = requiereEspecificacion;
    }

    public Long getIdOpcion() {
        return idOpcion;
    }

    public String getTexto() {
        return texto;
    }

    public Integer getOrden() {
        return orden;
    }

    public boolean isRequiereEspecificacion() {
        return requiereEspecificacion;
    }
}
