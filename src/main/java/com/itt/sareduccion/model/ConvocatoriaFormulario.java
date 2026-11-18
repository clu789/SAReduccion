package com.itt.sareduccion.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Convocatoria con plazo de solicitudes vigente para el solicitante, con los
 * tipos de solicitud que admite y, si ya envio una solicitud, su id y estatus.
 */
public final class ConvocatoriaFormulario {

    private final Long idConvocatoria;
    private final Integer idPeriodo;
    private final Integer anio;
    private final LocalDate fechaInicio;
    private final LocalDate fechaFin;
    private final Long idSolicitud;
    private final Integer idEstatusSolicitud;
    private final List<Tipo> tipos = new ArrayList<Tipo>();

    public ConvocatoriaFormulario(Long idConvocatoria, Integer idPeriodo, Integer anio, LocalDate fechaInicio,
            LocalDate fechaFin, Long idSolicitud, Integer idEstatusSolicitud) {
        this.idConvocatoria = idConvocatoria;
        this.idPeriodo = idPeriodo;
        this.anio = anio;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.idSolicitud = idSolicitud;
        this.idEstatusSolicitud = idEstatusSolicitud;
    }

    public void agregarTipo(Tipo tipo) {
        tipos.add(tipo);
    }

    /** Tipo disponible con ese id, o {@code null} si la convocatoria no lo admite. */
    public Tipo buscarTipo(Integer idTipoSolicitud) {
        if (idTipoSolicitud == null) {
            return null;
        }
        for (Tipo tipo : tipos) {
            if (idTipoSolicitud.equals(tipo.getIdTipoSolicitud())) {
                return tipo;
            }
        }
        return null;
    }

    public Long getIdConvocatoria() {
        return idConvocatoria;
    }

    public Integer getIdPeriodo() {
        return idPeriodo;
    }

    public Integer getAnio() {
        return anio;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public Long getIdSolicitud() {
        return idSolicitud;
    }

    public Integer getIdEstatusSolicitud() {
        return idEstatusSolicitud;
    }

    public List<Tipo> getTipos() {
        return tipos;
    }

    /** Tipo de solicitud admitido por la convocatoria y su formulario. */
    public static final class Tipo {
        private final Integer idTipoSolicitud;
        private final String nombre;
        private final Long idFormulario;

        public Tipo(Integer idTipoSolicitud, String nombre, Long idFormulario) {
            this.idTipoSolicitud = idTipoSolicitud;
            this.nombre = nombre;
            this.idFormulario = idFormulario;
        }

        public Integer getIdTipoSolicitud() {
            return idTipoSolicitud;
        }

        public String getNombre() {
            return nombre;
        }

        public Long getIdFormulario() {
            return idFormulario;
        }
    }
}
