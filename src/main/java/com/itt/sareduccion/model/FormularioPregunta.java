package com.itt.sareduccion.model;

import java.util.ArrayList;
import java.util.List;

/** Pregunta de un formulario: tipo ABIERTA, OPCION_UNICA o MIXTA. */
public final class FormularioPregunta {

    public static final String TIPO_ABIERTA = "ABIERTA";
    public static final String TIPO_OPCION_UNICA = "OPCION_UNICA";
    public static final String TIPO_MIXTA = "MIXTA";

    private final Long idPregunta;
    private final String texto;
    private final boolean obligatoria;
    private final String tipo;
    private final Integer orden;
    private final List<FormularioOpcion> opciones = new ArrayList<FormularioOpcion>();

    public FormularioPregunta(Long idPregunta, String texto, boolean obligatoria, String tipo, Integer orden) {
        this.idPregunta = idPregunta;
        this.texto = texto;
        this.obligatoria = obligatoria;
        this.tipo = tipo;
        this.orden = orden;
    }

    public void agregarOpcion(FormularioOpcion opcion) {
        opciones.add(opcion);
    }

    /** Opcion de esta pregunta con ese id, o {@code null} si no le pertenece. */
    public FormularioOpcion buscarOpcion(Long idOpcion) {
        if (idOpcion == null) {
            return null;
        }
        for (FormularioOpcion opcion : opciones) {
            if (idOpcion.equals(opcion.getIdOpcion())) {
                return opcion;
            }
        }
        return null;
    }

    public Long getIdPregunta() {
        return idPregunta;
    }

    public String getTexto() {
        return texto;
    }

    public boolean isObligatoria() {
        return obligatoria;
    }

    public String getTipo() {
        return tipo;
    }

    public Integer getOrden() {
        return orden;
    }

    public List<FormularioOpcion> getOpciones() {
        return opciones;
    }
}
