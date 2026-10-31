package com.itt.sareduccion.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class HistorialDeduccionAdmin {

    private final Long idHistorial;
    private final Long idDeduccion;
    private final Integer idEstatus;
    private final String estatusNombre;
    private final String idAdministrador;
    private final String administradorNombre;
    private final BigDecimal montoPago;
    private final BigDecimal montoDeduccion;
    private final BigDecimal montoFinal;
    private final LocalDateTime fechaRegistro;

    public HistorialDeduccionAdmin(Long idHistorial, Long idDeduccion, Integer idEstatus, String estatusNombre,
            String idAdministrador, String administradorNombre, BigDecimal montoPago, BigDecimal montoDeduccion,
            BigDecimal montoFinal, LocalDateTime fechaRegistro) {
        this.idHistorial = idHistorial;
        this.idDeduccion = idDeduccion;
        this.idEstatus = idEstatus;
        this.estatusNombre = estatusNombre;
        this.idAdministrador = idAdministrador;
        this.administradorNombre = administradorNombre;
        this.montoPago = montoPago;
        this.montoDeduccion = montoDeduccion;
        this.montoFinal = montoFinal;
        this.fechaRegistro = fechaRegistro;
    }

    public Long getIdHistorial() {
        return idHistorial;
    }

    public Long getIdDeduccion() {
        return idDeduccion;
    }

    public Integer getIdEstatus() {
        return idEstatus;
    }

    public String getEstatusNombre() {
        return estatusNombre;
    }

    /** Siempre presente: toda accion sobre una deduccion la hace un administrador. */
    public String getIdAdministrador() {
        return idAdministrador;
    }

    public String getAdministradorNombre() {
        return administradorNombre;
    }

    public BigDecimal getMontoPago() {
        return montoPago;
    }

    public BigDecimal getMontoDeduccion() {
        return montoDeduccion;
    }

    public BigDecimal getMontoFinal() {
        return montoFinal;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }
}
