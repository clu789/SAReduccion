package com.itt.sareduccion.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Solicitud del propio estudiante o aspirante, con el plazo de recepcion de su convocatoria. */
public final class SolicitudPropia {

    private final Long idSolicitud;
    private final Long idConvocatoria;
    private final Integer idTipoSolicitud;
    private final Integer idEstatus;
    private final LocalDateTime fechaSolicitud;
    private final LocalDateTime fechaActualizacion;
    private final String estatusNombre;
    private final String tipoNombre;
    private final Long idFormulario;
    private final Integer periodo;
    private final String periodoDescripcion;
    private final Integer anio;
    private final LocalDate fechaInicioRecepcion;
    private final LocalDate fechaFinRecepcion;
    private final boolean recepcionAbierta;

    public SolicitudPropia(Long idSolicitud, Long idConvocatoria, Integer idTipoSolicitud, Integer idEstatus,
            LocalDateTime fechaSolicitud, LocalDateTime fechaActualizacion, String estatusNombre,
            String tipoNombre, Long idFormulario, Integer periodo, String periodoDescripcion, Integer anio,
            LocalDate fechaInicioRecepcion, LocalDate fechaFinRecepcion, boolean recepcionAbierta) {
        this.idSolicitud = idSolicitud;
        this.idConvocatoria = idConvocatoria;
        this.idTipoSolicitud = idTipoSolicitud;
        this.idEstatus = idEstatus;
        this.fechaSolicitud = fechaSolicitud;
        this.fechaActualizacion = fechaActualizacion;
        this.estatusNombre = estatusNombre;
        this.tipoNombre = tipoNombre;
        this.idFormulario = idFormulario;
        this.periodo = periodo;
        this.periodoDescripcion = periodoDescripcion;
        this.anio = anio;
        this.fechaInicioRecepcion = fechaInicioRecepcion;
        this.fechaFinRecepcion = fechaFinRecepcion;
        this.recepcionAbierta = recepcionAbierta;
    }

    public Long getIdSolicitud() {
        return idSolicitud;
    }

    public Long getIdConvocatoria() {
        return idConvocatoria;
    }

    public Integer getIdTipoSolicitud() {
        return idTipoSolicitud;
    }

    public Integer getIdEstatus() {
        return idEstatus;
    }

    public LocalDateTime getFechaSolicitud() {
        return fechaSolicitud;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public String getEstatusNombre() {
        return estatusNombre;
    }

    public String getTipoNombre() {
        return tipoNombre;
    }

    public Long getIdFormulario() {
        return idFormulario;
    }

    public Integer getPeriodo() {
        return periodo;
    }

    public String getPeriodoDescripcion() {
        return periodoDescripcion;
    }

    public Integer getAnio() {
        return anio;
    }

    public LocalDate getFechaInicioRecepcion() {
        return fechaInicioRecepcion;
    }

    public LocalDate getFechaFinRecepcion() {
        return fechaFinRecepcion;
    }

    public boolean isRecepcionAbierta() {
        return recepcionAbierta;
    }
}
