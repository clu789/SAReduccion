package com.itt.sareduccion.model;

import java.math.BigDecimal;

public final class MontoAdmin {

    private final Long idMonto;
    private final BigDecimal montoPago;
    private final String tipoMonto;

    public MontoAdmin(Long idMonto, BigDecimal montoPago, String tipoMonto) {
        this.idMonto = idMonto;
        this.montoPago = montoPago;
        this.tipoMonto = tipoMonto;
    }

    public Long getIdMonto() {
        return idMonto;
    }

    public BigDecimal getMontoPago() {
        return montoPago;
    }

    /** "INSCRIPCION" o "REINSCRIPCION". */
    public String getTipoMonto() {
        return tipoMonto;
    }
}
