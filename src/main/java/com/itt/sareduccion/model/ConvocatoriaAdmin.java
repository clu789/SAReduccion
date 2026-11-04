package com.itt.sareduccion.model;

import java.time.LocalDate;

public final class ConvocatoriaAdmin {

    private final Long idConvocatoria;
    private final LocalDate fechaInicio;
    private final LocalDate fechaFin;
    private final LocalDate fechaLimiteAutorizacion;
    private final LocalDate fechaLimiteAplicacion;
    private final Integer periodo;
    private final Integer anio;
    private final boolean activa;

    public ConvocatoriaAdmin(Long idConvocatoria, LocalDate fechaInicio, LocalDate fechaFin,
            LocalDate fechaLimiteAutorizacion, LocalDate fechaLimiteAplicacion,
            Integer periodo, Integer anio, boolean activa) {
        this.idConvocatoria = idConvocatoria;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.fechaLimiteAutorizacion = fechaLimiteAutorizacion;
        this.fechaLimiteAplicacion = fechaLimiteAplicacion;
        this.periodo = periodo;
        this.anio = anio;
        this.activa = activa;
    }

    public Long getIdConvocatoria() {
        return idConvocatoria;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public LocalDate getFechaLimiteAutorizacion() {
        return fechaLimiteAutorizacion;
    }

    public LocalDate getFechaLimiteAplicacion() {
        return fechaLimiteAplicacion;
    }

    public Integer getPeriodo() {
        return periodo;
    }

    public Integer getAnio() {
        return anio;
    }

    public boolean isActiva() {
        return activa;
    }
}
