package com.itt.sareduccion.model;

import java.time.LocalDate;

public final class ConvocatoriaAdmin {

    private final Long idConvocatoria;
    private final LocalDate fechaInicio;
    private final LocalDate fechaFin;
    private final Integer periodo;
    private final Integer anio;
    private final boolean activa;

    public ConvocatoriaAdmin(Long idConvocatoria, LocalDate fechaInicio, LocalDate fechaFin,
            Integer periodo, Integer anio, boolean activa) {
        this.idConvocatoria = idConvocatoria;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
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
