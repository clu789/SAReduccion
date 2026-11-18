package com.itt.sareduccion.model;

import java.util.ArrayList;
import java.util.List;

/** Formulario completo de un tipo de solicitud: instrucciones, preguntas y opciones. */
public final class FormularioDetalle {

    private final Long idFormulario;
    private final String nombre;
    private final String descripcion;
    private final String instrucciones;
    private final Integer idTipoSolicitud;
    private final List<FormularioPregunta> preguntas = new ArrayList<FormularioPregunta>();

    public FormularioDetalle(Long idFormulario, String nombre, String descripcion, String instrucciones,
            Integer idTipoSolicitud) {
        this.idFormulario = idFormulario;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.instrucciones = instrucciones;
        this.idTipoSolicitud = idTipoSolicitud;
    }

    public void agregarPregunta(FormularioPregunta pregunta) {
        preguntas.add(pregunta);
    }

    /** Pregunta de este formulario con ese id, o {@code null} si no le pertenece. */
    public FormularioPregunta buscarPregunta(Long idPregunta) {
        if (idPregunta == null) {
            return null;
        }
        for (FormularioPregunta pregunta : preguntas) {
            if (idPregunta.equals(pregunta.getIdPregunta())) {
                return pregunta;
            }
        }
        return null;
    }

    public Long getIdFormulario() {
        return idFormulario;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getInstrucciones() {
        return instrucciones;
    }

    public Integer getIdTipoSolicitud() {
        return idTipoSolicitud;
    }

    public List<FormularioPregunta> getPreguntas() {
        return preguntas;
    }
}
