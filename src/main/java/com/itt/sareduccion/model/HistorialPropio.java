package com.itt.sareduccion.model;

import java.time.LocalDateTime;

/**
 * Movimiento del historial de una solicitud propia. Indica si lo registro un
 * administrador, pero no expone su identidad.
 */
public final class HistorialPropio {

    private final Long idHistorial;
    private final Long idSolicitud;
    private final Integer idEstatus;
    private final String estatusNombre;
    private final String comentario;
    private final LocalDateTime fecha;
    private final boolean administrativo;

    public HistorialPropio(Long idHistorial, Long idSolicitud, Integer idEstatus, String estatusNombre,
            String comentario, LocalDateTime fecha, boolean administrativo) {
        this.idHistorial = idHistorial;
        this.idSolicitud = idSolicitud;
        this.idEstatus = idEstatus;
        this.estatusNombre = estatusNombre;
        this.comentario = comentario;
        this.fecha = fecha;
        this.administrativo = administrativo;
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

    public String getComentario() {
        return comentario;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public boolean isAdministrativo() {
        return administrativo;
    }
}
