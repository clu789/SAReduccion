package com.itt.sareduccion.model;

import java.time.LocalDateTime;

public final class HistorialSolicitudAdmin {

    private final Long idHistorial;
    private final Long idSolicitud;
    private final Integer idEstatus;
    private final String estatusNombre;
    private final String idAdministrador;
    private final String administradorNombre;
    private final String comentario;
    private final LocalDateTime fechaRegistro;

    public HistorialSolicitudAdmin(Long idHistorial, Long idSolicitud, Integer idEstatus, String estatusNombre,
            String idAdministrador, String administradorNombre, String comentario, LocalDateTime fechaRegistro) {
        this.idHistorial = idHistorial;
        this.idSolicitud = idSolicitud;
        this.idEstatus = idEstatus;
        this.estatusNombre = estatusNombre;
        this.idAdministrador = idAdministrador;
        this.administradorNombre = administradorNombre;
        this.comentario = comentario;
        this.fechaRegistro = fechaRegistro;
    }

    public Long getIdHistorial() {
        return idHistorial;
    }

    public Long getIdSolicitud() {
        return idSolicitud;
    }

    public Integer getIdEstatus() {
        return idEstatus;
    }

    public String getEstatusNombre() {
        return estatusNombre;
    }

    /** Nulo cuando el movimiento lo hizo el alumno (estatus PENDIENTE/RETIRADO). */
    public String getIdAdministrador() {
        return idAdministrador;
    }

    public String getAdministradorNombre() {
        return administradorNombre;
    }

    public String getComentario() {
        return comentario;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }
}
