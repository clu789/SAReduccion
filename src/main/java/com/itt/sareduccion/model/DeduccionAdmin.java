package com.itt.sareduccion.model;

import java.math.BigDecimal;

public final class DeduccionAdmin {

    private final Long idDeduccion;
    private final Long idSolicitud;
    private final Integer estatusDeduccion;
    private final BigDecimal montoPago;
    private final BigDecimal montoDeduccion;
    private final BigDecimal montoFinal;

    public DeduccionAdmin(Long idDeduccion, Long idSolicitud, Integer estatusDeduccion,
            BigDecimal montoPago, BigDecimal montoDeduccion, BigDecimal montoFinal) {
        this.idDeduccion = idDeduccion;
        this.idSolicitud = idSolicitud;
        this.estatusDeduccion = estatusDeduccion;
        this.montoPago = montoPago;
        this.montoDeduccion = montoDeduccion;
        this.montoFinal = montoFinal;
    }

    public Long getIdDeduccion() {
        return idDeduccion;
    }

    public Long getIdSolicitud() {
        return idSolicitud;
    }

    public Integer getEstatusDeduccion() {
        return estatusDeduccion;
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
}
