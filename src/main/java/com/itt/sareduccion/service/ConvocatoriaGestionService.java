
package com.itt.sareduccion.service;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.itt.sareduccion.repository.ConvocatoriaGestionRepository;

@Service
public class ConvocatoriaGestionService {

    private final ConvocatoriaGestionRepository repository;

    public ConvocatoriaGestionService(
            ConvocatoriaGestionRepository repository) {
        this.repository = repository;
    }

    public Long crearConvocatoria(
            String idAdministrador,
            Integer periodo,
            Integer anio,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            LocalDate fechaLimiteAutorizacion,
            LocalDate fechaLimiteAplicacion,
            BigDecimal montoTrabajador,
            BigDecimal montoHomologado) {

        if (periodo == null || periodo <= 0) {
            throw new ReglaNegocioException(
                    "Debe seleccionar un periodo valido.");
        }

        if (anio == null || anio < 2000 || anio > 2200) {
            throw new ReglaNegocioException(
                    "El anio debe estar entre 2000 y 2200.");
        }

        if (fechaInicio == null || fechaFin == null
                || fechaLimiteAutorizacion == null
                || fechaLimiteAplicacion == null) {
            throw new ReglaNegocioException(
                    "Debe completar todas las fechas.");
        }

        if (fechaInicio.isAfter(fechaFin)
                || fechaFin.isAfter(fechaLimiteAutorizacion)
                || fechaLimiteAutorizacion.isAfter(fechaLimiteAplicacion)) {
            throw new ReglaNegocioException(
                    "Las fechas deben mantener el orden: "
                    + "inicio, fin, autorizacion y aplicacion.");
        }

        if (fechaLimiteAutorizacion.isBefore(LocalDate.now())) {
            throw new ReglaNegocioException(
                    "No se puede crear una convocatoria "
                    + "con el plazo de autorizacion vencido.");
        }

        validarMonto(montoTrabajador, "trabajadores");
        validarMonto(montoHomologado, "homologados");

        try {
            return repository.crearConvocatoria(
                    idAdministrador,
                    periodo,
                    anio,
                    fechaInicio,
                    fechaFin,
                    fechaLimiteAutorizacion,
                    fechaLimiteAplicacion,
                    montoTrabajador,
                    montoHomologado);

        } catch (DataAccessException exception) {
            throw traducirErrorOracle(exception);
        }
    }

    private void validarMonto(BigDecimal monto, String tipo) {
        if (monto == null
                || monto.signum() < 0
                || monto.compareTo(
                        new BigDecimal("100000000")) >= 0
                || monto.stripTrailingZeros().scale() > 2) {
            throw new ReglaNegocioException(
                    "El monto para " + tipo
                    + " debe ser positivo o cero, "
                    + "menor a 100000000 y tener "
                    + "maximo dos decimales.");
        }
    }

    private RuntimeException traducirErrorOracle(
            DataAccessException exception) {

        Integer codigo = obtenerCodigoOracle(exception);

        if (codigo == null) {
            return exception;
        }

        // JDBC puede devolver el codigo Oracle sin signo.
        int error = Math.abs(codigo);

        switch (error) {
            case 20910:
                return new ReglaNegocioException(
                        "El periodo o anio seleccionado no es valido.");

            case 20911:
                return new ReglaNegocioException(
                        "Las fechas de la convocatoria no son validas.");

            case 20912:
                return new ReglaNegocioException(
                        "El plazo de autorizacion ya vencio.");

            case 20913:
                return new ReglaNegocioException(
                        "Los montos de deduccion no son validos.");

            case 20914:
                return new ReglaNegocioException(
                        "El catalogo de tipos de solicitud esta incompleto.");

            case 20916:
                return new ReglaNegocioException(
                        "Ya existe una convocatoria para ese periodo y anio.");

            case 20917:
                return new ReglaNegocioException(
                        "Las fechas se superponen con otra convocatoria.");

            default:
                return exception;
        }
    }

    private Integer obtenerCodigoOracle(
            DataAccessException exception) {

        for (Throwable causa = exception;
                causa != null;
                causa = causa.getCause()) {

            if (causa instanceof SQLException) {
                return ((SQLException) causa).getErrorCode();
            }
        }

        return null;
    }
}
