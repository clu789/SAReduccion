package com.itt.sareduccion.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itt.sareduccion.model.ConvocatoriaDisponible;
import com.itt.sareduccion.model.DeduccionPropia;
import com.itt.sareduccion.model.HistorialPropio;
import com.itt.sareduccion.model.IdentidadSolicitante;
import com.itt.sareduccion.model.PerfilSolicitante;
import com.itt.sareduccion.model.SolicitudPropia;
import com.itt.sareduccion.repository.SolicitanteRepository;

/**
 * Panel del estudiante y aspirante (equipo 3): perfil, convocatorias
 * disponibles, seguimiento, retiro y reenvio. La identidad llega siempre de la
 * sesion; antes de cada escritura se comprueba en el backend que la solicitud
 * sea propia, su estatus y el periodo de recepcion, y Oracle lo vuelve a
 * validar en {@code PKG_SOLICITUDES_CORE}.
 */
@Service
public class SolicitanteService {

    private static final Logger log = LoggerFactory.getLogger(SolicitanteService.class);

    static final int ESTATUS_PENDIENTE = 1;
    static final List<Integer> ESTATUS_REENVIABLES = Arrays.asList(3, 4, 6);

    private static final Map<Integer, String> ESTATUS_DEDUCCION;

    static {
        Map<Integer, String> estatus = new LinkedHashMap<Integer, String>();
        estatus.put(2, "AUTORIZADO");
        estatus.put(4, "CANCELADO");
        estatus.put(5, "APLICADO");
        ESTATUS_DEDUCCION = estatus;
    }

    private final SolicitanteRepository solicitanteRepository;
    private final ObjectMapper objectMapper;

    public SolicitanteService(SolicitanteRepository solicitanteRepository, ObjectMapper objectMapper) {
        this.solicitanteRepository = solicitanteRepository;
        this.objectMapper = objectMapper;
    }

    public PerfilSolicitante perfil(IdentidadSolicitante identidad) {
        try {
            return solicitanteRepository.perfil(identidad)
                    .orElseThrow(() -> new ReglaNegocioException("No se encontro el perfil del solicitante"));
        } catch (DataAccessException exception) {
            throw traducirError(identidad, "perfil", exception, "No se pudo consultar el perfil");
        }
    }

    public List<ConvocatoriaDisponible> convocatorias(IdentidadSolicitante identidad) {
        try {
            return solicitanteRepository.convocatorias(identidad);
        } catch (DataAccessException exception) {
            throw traducirError(identidad, "convocatorias", exception, "No se pudieron consultar las convocatorias");
        }
    }

    /** Solicitudes propias con su historial, su deduccion y las acciones permitidas. */
    public List<Seguimiento> seguimiento(IdentidadSolicitante identidad) {
        try {
            List<SolicitudPropia> solicitudes = solicitanteRepository.solicitudes(identidad);
            List<HistorialPropio> historial = solicitanteRepository.historial(identidad);
            List<DeduccionPropia> deducciones = solicitanteRepository.deducciones(identidad);

            List<Seguimiento> resultado = new ArrayList<Seguimiento>();
            for (SolicitudPropia solicitud : solicitudes) {
                List<HistorialPropio> movimientos = new ArrayList<HistorialPropio>();
                for (HistorialPropio item : historial) {
                    if (solicitud.getIdSolicitud().equals(item.getIdSolicitud())) {
                        movimientos.add(item);
                    }
                }
                DeduccionPropia deduccion = null;
                for (DeduccionPropia candidata : deducciones) {
                    if (solicitud.getIdSolicitud().equals(candidata.getIdSolicitud())) {
                        deduccion = candidata;
                        break;
                    }
                }
                resultado.add(new Seguimiento(solicitud, movimientos, deduccion));
            }
            log.info("solicitante.seguimiento tipo={} id={} solicitudes={}", identidad.getTipo(),
                    identidad.enmascarado(), resultado.size());
            return resultado;
        } catch (DataAccessException exception) {
            throw traducirError(identidad, "seguimiento", exception, "No se pudo consultar el seguimiento");
        }
    }

    public Seguimiento seguimiento(IdentidadSolicitante identidad, long idSolicitud) {
        for (Seguimiento item : seguimiento(identidad)) {
            if (item.getSolicitud().getIdSolicitud().longValue() == idSolicitud) {
                return item;
            }
        }
        log.warn("solicitante.consulta tipo={} id={} solicitud={} resultado=NO_ENCONTRADA", identidad.getTipo(),
                identidad.enmascarado(), idSolicitud);
        throw new SolicitudNoEncontradaException();
    }

    public void retirar(IdentidadSolicitante identidad, long idSolicitud) {
        SolicitudPropia solicitud = propia(identidad, idSolicitud, "retirar");
        if (!puedeRetirar(solicitud)) {
            log.warn("solicitante.retirar tipo={} id={} solicitud={} estatus={} resultado=RECHAZADO",
                    identidad.getTipo(), identidad.enmascarado(), idSolicitud, solicitud.getIdEstatus());
            throw new ReglaNegocioException(solicitud.isRecepcionAbierta()
                    ? "Solo se pueden retirar solicitudes pendientes"
                    : "El periodo de recepcion de la convocatoria ya termino");
        }
        try {
            solicitanteRepository.retirar(identidad, idSolicitud);
        } catch (DataAccessException exception) {
            throw traducirError(identidad, "retirar", exception, "No se pudo retirar la solicitud");
        }
        log.info("solicitante.retirar tipo={} id={} solicitud={} resultado=OK", identidad.getTipo(),
                identidad.enmascarado(), idSolicitud);
    }

    public void reenviar(IdentidadSolicitante identidad, long idSolicitud, Reenvio reenvio) {
        SolicitudPropia solicitud = propia(identidad, idSolicitud, "reenviar");
        if (!puedeReenviar(solicitud)) {
            log.warn("solicitante.reenviar tipo={} id={} solicitud={} estatus={} resultado=RECHAZADO",
                    identidad.getTipo(), identidad.enmascarado(), idSolicitud, solicitud.getIdEstatus());
            throw new ReglaNegocioException(solicitud.isRecepcionAbierta()
                    ? "Solo se pueden reenviar solicitudes retiradas, rechazadas o canceladas"
                    : "El periodo de recepcion de la convocatoria ya termino");
        }
        if (reenvio == null || reenvio.getIdTipoSolicitud() == null || reenvio.getIdFormulario() == null) {
            throw new ReglaNegocioException("Indique el tipo de solicitud y el formulario");
        }
        if (reenvio.getRespuestas() == null || reenvio.getRespuestas().isEmpty()) {
            throw new ReglaNegocioException("Las respuestas del formulario son obligatorias");
        }
        if (!tipoDisponible(identidad, solicitud.getIdConvocatoria(), reenvio)) {
            throw new ReglaNegocioException("El tipo de solicitud no esta disponible en la convocatoria");
        }
        String json = respuestasJson(reenvio.getRespuestas());
        try {
            solicitanteRepository.reenviar(identidad, idSolicitud, reenvio.getIdTipoSolicitud(),
                    reenvio.getIdFormulario(), json);
        } catch (DataAccessException exception) {
            throw traducirError(identidad, "reenviar", exception, "No se pudo reenviar la solicitud");
        }
        log.info("solicitante.reenviar tipo={} id={} solicitud={} tipoSolicitud={} respuestas={} resultado=OK",
                identidad.getTipo(), identidad.enmascarado(), idSolicitud, reenvio.getIdTipoSolicitud(),
                reenvio.getRespuestas().size());
    }

    public String nombreEstatusDeduccion(Integer idEstatus) {
        String nombre = ESTATUS_DEDUCCION.get(idEstatus);
        return nombre == null ? "DESCONOCIDO" : nombre;
    }

    static boolean puedeRetirar(SolicitudPropia solicitud) {
        return solicitud.isRecepcionAbierta()
                && Integer.valueOf(ESTATUS_PENDIENTE).equals(solicitud.getIdEstatus());
    }

    static boolean puedeReenviar(SolicitudPropia solicitud) {
        return solicitud.isRecepcionAbierta() && ESTATUS_REENVIABLES.contains(solicitud.getIdEstatus());
    }

    private SolicitudPropia propia(IdentidadSolicitante identidad, long idSolicitud, String operacion) {
        List<SolicitudPropia> solicitudes;
        try {
            solicitudes = solicitanteRepository.solicitudes(identidad);
        } catch (DataAccessException exception) {
            throw traducirError(identidad, operacion, exception, "No se pudo consultar la solicitud");
        }
        for (SolicitudPropia solicitud : solicitudes) {
            if (solicitud.getIdSolicitud() != null && solicitud.getIdSolicitud().longValue() == idSolicitud) {
                return solicitud;
            }
        }
        log.warn("solicitante.{} tipo={} id={} solicitud={} resultado=NO_ENCONTRADA", operacion,
                identidad.getTipo(), identidad.enmascarado(), idSolicitud);
        throw new SolicitudNoEncontradaException();
    }

    private boolean tipoDisponible(IdentidadSolicitante identidad, Long idConvocatoria, Reenvio reenvio) {
        for (ConvocatoriaDisponible convocatoria : convocatorias(identidad)) {
            if (convocatoria.getIdConvocatoria().equals(idConvocatoria)
                    && reenvio.getIdTipoSolicitud().equals(convocatoria.getIdTipoSolicitud())
                    && reenvio.getIdFormulario().equals(convocatoria.getIdFormulario())) {
                return true;
            }
        }
        return false;
    }

    /** Arreglo JSON con el formato que espera {@code guardar_respuestas_completas}. */
    private String respuestasJson(List<Respuesta> respuestas) {
        List<Map<String, Object>> filas = new ArrayList<Map<String, Object>>();
        for (Respuesta respuesta : respuestas) {
            if (respuesta == null || respuesta.getIdPregunta() == null) {
                throw new ReglaNegocioException("Cada respuesta debe indicar su pregunta");
            }
            String texto = respuesta.getTextoRespuesta();
            texto = texto == null || texto.trim().isEmpty() ? null : texto.trim();
            if (texto == null && respuesta.getIdOpcion() == null) {
                // Pregunta sin contestar: Oracle rechaza filas vacias y valida las obligatorias.
                continue;
            }
            Map<String, Object> fila = new LinkedHashMap<String, Object>();
            fila.put("id_pregunta", respuesta.getIdPregunta());
            fila.put("id_opcion", respuesta.getIdOpcion());
            fila.put("texto_respuesta", texto);
            filas.add(fila);
        }
        if (filas.isEmpty()) {
            throw new ReglaNegocioException("Las respuestas del formulario son obligatorias");
        }
        try {
            return objectMapper.writeValueAsString(filas);
        } catch (JsonProcessingException exception) {
            throw new ReglaNegocioException("Las respuestas no tienen un formato valido");
        }
    }

    /**
     * Traduce los errores esperados de los paquetes (-20801..-20887) y de los
     * triggers (-20001..-20075) a mensajes genericos. El driver de Oracle
     * reporta el codigo ORA en positivo, por eso se compara su valor absoluto.
     */
    private RuntimeException traducirError(IdentidadSolicitante identidad, String operacion,
            DataAccessException exception, String mensajeGenerico) {
        Integer codigo = codigoOracle(exception);
        if (codigo == null) {
            log.error("solicitante.{} tipo={} id={} resultado=ERROR causa={}", operacion, identidad.getTipo(),
                    identidad.enmascarado(), exception.getClass().getSimpleName());
            return exception;
        }
        int ora = Math.abs(codigo.intValue());
        log.warn("solicitante.{} tipo={} id={} resultado=RECHAZADO_ORACLE ora={}", operacion, identidad.getTipo(),
                identidad.enmascarado(), ora);
        switch (ora) {
            case 20858:
            case 20861:
            case 1403:
                return new SolicitudNoEncontradaException();
            case 20859:
                return new ReglaNegocioException("Solo se pueden retirar solicitudes pendientes");
            case 20860:
            case 20812:
                return new ReglaNegocioException("El periodo de recepcion de la convocatoria ya termino");
            case 20862:
                return new ReglaNegocioException(
                        "Solo se pueden reenviar solicitudes retiradas, rechazadas o canceladas");
            case 20852:
            case 20855:
                return new ReglaNegocioException("Faltan respuestas a preguntas obligatorias");
            default:
                break;
        }
        if ((ora >= 20850 && ora <= 20857) || (ora >= 20001 && ora <= 20075)) {
            return new ReglaNegocioException(mensajeGenerico + ": revise las respuestas del formulario");
        }
        if (ora >= 20801 && ora <= 20887) {
            return new ReglaNegocioException(mensajeGenerico);
        }
        return exception;
    }

    private Integer codigoOracle(DataAccessException exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof SQLException) {
                return Integer.valueOf(((SQLException) current).getErrorCode());
            }
        }
        return null;
    }

    /** Solicitud con su historial, su deduccion y las acciones que el solicitante puede realizar. */
    public static final class Seguimiento {
        private final SolicitudPropia solicitud;
        private final List<HistorialPropio> historial;
        private final DeduccionPropia deduccion;

        public Seguimiento(SolicitudPropia solicitud, List<HistorialPropio> historial, DeduccionPropia deduccion) {
            this.solicitud = solicitud;
            this.historial = historial;
            this.deduccion = deduccion;
        }

        public SolicitudPropia getSolicitud() {
            return solicitud;
        }

        public List<HistorialPropio> getHistorial() {
            return historial;
        }

        public DeduccionPropia getDeduccion() {
            return deduccion;
        }

        public boolean isPuedeRetirar() {
            return puedeRetirar(solicitud);
        }

        public boolean isPuedeReenviar() {
            return puedeReenviar(solicitud);
        }
    }

    /** Datos de reenvio: tipo, formulario y respuestas completas confirmadas. */
    public static final class Reenvio {
        private Integer idTipoSolicitud;
        private Long idFormulario;
        private List<Respuesta> respuestas;

        public Integer getIdTipoSolicitud() {
            return idTipoSolicitud;
        }

        public void setIdTipoSolicitud(Integer idTipoSolicitud) {
            this.idTipoSolicitud = idTipoSolicitud;
        }

        public Long getIdFormulario() {
            return idFormulario;
        }

        public void setIdFormulario(Long idFormulario) {
            this.idFormulario = idFormulario;
        }

        public List<Respuesta> getRespuestas() {
            return respuestas;
        }

        public void setRespuestas(List<Respuesta> respuestas) {
            this.respuestas = respuestas;
        }
    }

    public static final class Respuesta {
        private Long idPregunta;
        private Long idOpcion;
        private String textoRespuesta;

        public Long getIdPregunta() {
            return idPregunta;
        }

        public void setIdPregunta(Long idPregunta) {
            this.idPregunta = idPregunta;
        }

        public Long getIdOpcion() {
            return idOpcion;
        }

        public void setIdOpcion(Long idOpcion) {
            this.idOpcion = idOpcion;
        }

        public String getTextoRespuesta() {
            return textoRespuesta;
        }

        public void setTextoRespuesta(String textoRespuesta) {
            this.textoRespuesta = textoRespuesta;
        }
    }
}
