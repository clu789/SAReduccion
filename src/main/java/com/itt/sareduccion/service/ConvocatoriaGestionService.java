
package com.itt.sareduccion.service;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.itt.sareduccion.repository.ConvocatoriaGestionRepository;

@Service
public class ConvocatoriaGestionService {

    private static final Logger LOG = LoggerFactory.getLogger(ConvocatoriaGestionService.class);

    private final ConvocatoriaGestionRepository repository;

    public ConvocatoriaGestionService(
            ConvocatoriaGestionRepository repository) {
        this.repository = repository;
    }

    public List<Map<String, Object>> listarConvocatorias(
        String idAdministrador) {

        if (idAdministrador == null
                || idAdministrador.trim().isEmpty()) {
            throw new ReglaNegocioException(
                    "No se proporciono un administrador valido.");
        }

        try {
            List<Map<String, Object>> convocatorias =
                    repository.listarConvocatorias(idAdministrador);

            LocalDate hoy = LocalDate.now();
            for (Map<String, Object> convocatoria : convocatorias) {
                LocalDate inicio = (LocalDate) convocatoria.get("fechaInicio");
                LocalDate fin = (LocalDate) convocatoria.get("fechaFin");
                String estado = calcularEstado(inicio, fin, hoy);
                convocatoria.put("estado", estado);
                convocatoria.put("activa", "ACTIVA".equals(estado));
            }

            return convocatorias;

        } catch (DataAccessException exception) {
            throw traducirErrorOracle(exception);
        }
    }

    private String calcularEstado(LocalDate inicio, LocalDate fin, LocalDate hoy) {
        if (inicio == null || fin == null || inicio.isAfter(fin)) {
            return "SIN_DEFINIR";
        }
        if (hoy.isBefore(inicio)) {
            return "PROXIMA";
        }
        if (hoy.isAfter(fin)) {
            return "CERRADA";
        }
        return "ACTIVA";
    }

    public Map<String, Object> consultarConvocatoria(
        String idAdministrador,
        Long idConvocatoria) {

        if (idAdministrador == null
                || idAdministrador.trim().isEmpty()) {
            throw new ReglaNegocioException(
                    "No se proporciono un administrador valido.");
        }

        if (idConvocatoria == null || idConvocatoria <= 0) {
            throw new ReglaNegocioException(
                    "El identificador de la convocatoria no es valido.");
        }

        List<Map<String, Object>> convocatorias =
                listarConvocatorias(idAdministrador);

        Map<String, Object> convocatoriaEncontrada = null;

        for (Map<String, Object> convocatoria : convocatorias) {

            Number id = (Number) convocatoria.get("idConvocatoria");

            if (id != null
                    && id.longValue() == idConvocatoria.longValue()) {

                convocatoriaEncontrada =
                        new LinkedHashMap<String, Object>(convocatoria);

                break;
            }
        }

        if (convocatoriaEncontrada == null) {
            return null;
        }

        try {
            List<Map<String, Object>> montos =
                    repository.consultarMontosConvocatoria(
                            idAdministrador,
                            idConvocatoria);

            convocatoriaEncontrada.put("montos", montos);

            LocalDate hoy = LocalDate.now();
            LocalDate limiteAplicacion = (LocalDate)
                    convocatoriaEncontrada.get("fechaLimiteAplicacion");
            LocalDate limiteAutorizacion = (LocalDate)
                    convocatoriaEncontrada.get("fechaLimiteAutorizacion");
            convocatoriaEncontrada.put("puedeEditarFechas",
                    limiteAplicacion != null && !hoy.isAfter(limiteAplicacion));
            convocatoriaEncontrada.put("puedeEditarMontos",
                    limiteAutorizacion != null && !hoy.isAfter(limiteAutorizacion));

            return convocatoriaEncontrada;

        } catch (DataAccessException exception) {
            throw traducirErrorOracle(exception);
        }
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
            Long idCreada = repository.crearConvocatoria(
                    idAdministrador,
                    periodo,
                    anio,
                    fechaInicio,
                    fechaFin,
                    fechaLimiteAutorizacion,
                    fechaLimiteAplicacion,
                    montoTrabajador,
                    montoHomologado);
            LOG.info("CONVOCATORIA_CREADA idConvocatoria={} periodo={} anio={}", idCreada, periodo, anio);
            return idCreada;

        } catch (DataAccessException exception) {
            throw traducirErrorOracle(exception);
        }
    }

    public Map<String, Object> actualizarConvocatoria(
            String idAdministrador,
            Long idConvocatoria,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            LocalDate fechaLimiteAutorizacion,
            LocalDate fechaLimiteAplicacion,
            BigDecimal montoTrabajador,
            BigDecimal montoHomologado) {

        if (idAdministrador == null || idAdministrador.trim().isEmpty()) {
            throw new ReglaNegocioException(
                    "No se proporciono un administrador valido.");
        }
        if (idConvocatoria == null || idConvocatoria <= 0) {
            throw new ReglaNegocioException(
                    "El identificador de la convocatoria no es valido.");
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

        validarMonto(montoTrabajador, "trabajadores");
        validarMonto(montoHomologado, "homologados");

        try {
            Map<String, Object> resultado = repository.actualizarConvocatoria(
                    idAdministrador,
                    idConvocatoria,
                    fechaInicio,
                    fechaFin,
                    fechaLimiteAutorizacion,
                    fechaLimiteAplicacion,
                    montoTrabajador,
                    montoHomologado);
            LOG.info("CONVOCATORIA_ACTUALIZADA idConvocatoria={} huboCambios={}",
                    idConvocatoria, resultado.get("huboCambios"));
            return resultado;
        } catch (DataAccessException exception) {
            throw traducirErrorOracle(exception);
        }
    }

    public List<Map<String, Object>> listarHistorialConvocatoria(
            String idAdministrador, Long idConvocatoria) {
        if (idAdministrador == null || idAdministrador.trim().isEmpty()) {
            throw new ReglaNegocioException("No se proporciono un administrador valido.");
        }
        if (idConvocatoria == null || idConvocatoria <= 0) {
            throw new ReglaNegocioException("Debe seleccionar una convocatoria valida.");
        }
        try {
            return repository.listarHistorialConvocatoria(idAdministrador, idConvocatoria);
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

            case 20915:
                return new ConvocatoriaNoEncontradaException(
                        "No se encontro la convocatoria solicitada.");

            case 20916:
                return new ReglaNegocioException(
                        "Ya existe una convocatoria para ese periodo y anio.");

            case 20917:
                return new ReglaNegocioException(
                        "Las fechas se superponen con otra convocatoria.");

            case 20870:
            case 20073:
                return new ReglaNegocioException(
                        "El plazo de autorizacion para modificar montos ya vencio.");
            case 20882:
            case 20074:
                return new ReglaNegocioException(
                        "La convocatoria supero su fecha limite de aplicacion.");
            case 20883:
                return new ReglaNegocioException(
                        "El orden de las fechas no es valido.");
            case 20884:
                return new ReglaNegocioException(
                        "Las nuevas fechas excluirian solicitudes ya registradas.");
            case 20885:
                return new ReglaNegocioException(
                        "Las nuevas fechas excluirian movimientos historicos.");
            case 20886:
                return new ReglaNegocioException(
                        "El monto proporcionado no es valido.");
            case 20887:
                return new ReglaNegocioException(
                        "El tipo de solicitud no esta configurado en la convocatoria.");
            case 20075:
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



