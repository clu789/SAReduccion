package com.itt.sareduccion.model;

import java.math.BigDecimal;

/** Deduccion asociada a una solicitud del propio estudiante o aspirante. */
public final class DeduccionPropia {

    private final Long idDeduccion;
    private final Long idSolicitud;
    private final Integer idEstatus;
    private final BigDecimal montoPago;
    private final BigDecimal montoDeduccion;
    private final BigDecimal montoFinal;

    public DeduccionPropia(Long idDeduccion, Long idSolicitud, Integer idEstatus, BigDecimal montoPago,
            BigDecimal montoDeduccion, BigDecimal montoFinal) {
        this.idDeduccion = idDeduccion;
        this.idSolicitud = idSolicitud;
        this.idEstatus = idEstatus;
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

    public Integer getIdEstatus() {
        return idEstatus;
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
