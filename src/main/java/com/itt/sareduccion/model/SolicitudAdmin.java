package com.itt.sareduccion.model;

import java.time.LocalDateTime;

public final class SolicitudAdmin {

    private final Long idSolicitud;
    private final String numControl;
    private final String ficha;
    private final Integer periodo;
    private final Integer anio;
    private final Long idConvocatoria;
    private final Integer idEstatusSolicitud;
    private final Integer idTipoSolicitud;
    private final LocalDateTime fechaActualizacion;
    private final String tipoSolicitudNombre;
    private final String nombreCompleto;

    public SolicitudAdmin(Long idSolicitud, String numControl, String ficha, Integer periodo,
            Integer anio, Long idConvocatoria, Integer idEstatusSolicitud, Integer idTipoSolicitud,
            LocalDateTime fechaActualizacion, String tipoSolicitudNombre, String nombreCompleto) {
        this.idSolicitud = idSolicitud;
        this.numControl = numControl;
        this.ficha = ficha;
        this.periodo = periodo;
        this.anio = anio;
        this.idConvocatoria = idConvocatoria;
        this.idEstatusSolicitud = idEstatusSolicitud;
        this.idTipoSolicitud = idTipoSolicitud;
        this.fechaActualizacion = fechaActualizacion;
        this.tipoSolicitudNombre = tipoSolicitudNombre;
        this.nombreCompleto = nombreCompleto;
    }

    public Long getIdSolicitud() {
        return idSolicitud;
    }

    public String getNumControl() {
        return numControl;
    }

    public String getFicha() {
        return ficha;
    }

    public Integer getPeriodo() {
        return periodo;
    }

    public Integer getAnio() {
        return anio;
    }

    public Long getIdConvocatoria() {
        return idConvocatoria;
    }

    public Integer getIdEstatusSolicitud() {
        return idEstatusSolicitud;
    }

    public Integer getIdTipoSolicitud() {
        return idTipoSolicitud;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public String getTipoSolicitudNombre() {
        return tipoSolicitudNombre;
    }

    /** Nombre completo del estudiante o aspirante, resuelto por la BD; puede ser nulo. */
    public String getNombreCompleto() {
        return nombreCompleto;
    }

    /** Identificador visible del solicitante: control de estudiante o ficha de aspirante. */
    public String getSolicitante() {
        if (numControl != null && !numControl.trim().isEmpty()) {
            return numControl.trim();
        }
        return ficha == null ? null : ficha.trim();
    }

    /** {@code true} si la solicitud pertenece a un estudiante (num_control presente). */
    public boolean esEstudiante() {
        return numControl != null && !numControl.trim().isEmpty();
    }

    /** Tipo de solicitante legible: "ESTUDIANTE" o "ASPIRANTE". */
    public String getTipoSolicitante() {
        return esEstudiante() ? "ESTUDIANTE" : "ASPIRANTE";
    }
}
