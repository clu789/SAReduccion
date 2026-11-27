package com.itt.sareduccion.service;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.itt.sareduccion.auth.SessionPrincipal;
import com.itt.sareduccion.model.ConvocatoriaFormulario;
import com.itt.sareduccion.model.EnvioSolicitudRequest;
import com.itt.sareduccion.model.FormularioDetalle;
import com.itt.sareduccion.model.ReenvioPreparado;
import com.itt.sareduccion.model.SolicitudConRespuestas;
import com.itt.sareduccion.repository.FormularioSolicitudRepository;

/**
 * Reglas del formulario de solicitud (Equipo 5): formulario del tipo permitido
 * por la convocatoria, envio inicial completo y atomico (sin borradores) y
 * reenvio de solicitudes RETIRADO, RECHAZADO o CANCELADO dentro del plazo.
 * La identidad llega siempre del principal de la sesion. Los logs registran
 * eventos e identificadores de solicitud, nunca respuestas ni datos personales.
 */
@Service
public class FormularioSolicitudService {

    private static final Logger LOG = LoggerFactory.getLogger(FormularioSolicitudService.class);

    static final int ESTATUS_RECHAZADO = 3;
    static final int ESTATUS_CANCELADO = 4;
    static final int ESTATUS_RETIRADO = 6;

    private final FormularioSolicitudRepository repository;

    public FormularioSolicitudService(FormularioSolicitudRepository repository) {
        this.repository = repository;
    }

    public List<ConvocatoriaFormulario> convocatoriasAbiertas(SessionPrincipal principal) {
        try {
            return repository.convocatoriasAbiertas(principal);
        } catch (DataAccessException exception) {
            throw traducir(exception, "consultar_convocatorias");
        }
    }

    /** Formulario de un tipo solo si la convocatoria esta abierta para este solicitante. */
    public FormularioDetalle obtenerFormulario(SessionPrincipal principal, long idConvocatoria, int idTipoSolicitud) {
        ConvocatoriaFormulario.Tipo tipo = tipoDisponible(principal, idConvocatoria, Integer.valueOf(idTipoSolicitud));
        return cargarFormulario(idConvocatoria, tipo.getIdTipoSolicitud().intValue());
    }

    public SolicitudConRespuestas misRespuestas(SessionPrincipal principal, long idSolicitud) {
        try {
            return repository.misRespuestas(principal, idSolicitud);
        } catch (DataAccessException exception) {
            throw traducir(exception, "consultar_respuestas");
        }
    }

    /** Envio inicial. Devuelve el id de la solicitud creada en estado PENDIENTE. */
    public long enviar(SessionPrincipal principal, EnvioSolicitudRequest request) {
        if (request == null || request.getIdConvocatoria() == null || request.getIdTipoSolicitud() == null) {
            throw new ValidacionRespuestasException("Indique la convocatoria y el tipo de solicitud.",
                    Collections.<ValidacionRespuestasException.ErrorCampo>emptyList());
        }
        ConvocatoriaFormulario convocatoria = convocatoriaAbierta(principal, request.getIdConvocatoria());
        ConvocatoriaFormulario.Tipo tipo = convocatoria.buscarTipo(request.getIdTipoSolicitud());
        if (tipo == null) {
            throw new ReglaNegocioException("El tipo de solicitud no está disponible en esta convocatoria.");
        }
        if (convocatoria.getIdSolicitud() != null) {
            throw new ReglaNegocioException("Ya existe una solicitud para esta convocatoria. "
                    + "Si fue retirada, rechazada o cancelada, puede reenviarla.");
        }

        FormularioDetalle formulario = cargarFormulario(convocatoria.getIdConvocatoria().longValue(),
                tipo.getIdTipoSolicitud().intValue());
        String json = validar(formulario, request);
        try {
            long id = repository.crearSolicitud(principal, convocatoria.getIdConvocatoria().longValue(),
                    tipo.getIdTipoSolicitud().intValue(), tipo.getIdFormulario().longValue(), json);
            LOG.info("SOLICITUD_ENVIADA idSolicitud={} idConvocatoria={} rol={}", id,
                    convocatoria.getIdConvocatoria(), principal.getRole());
            return id;
        } catch (DataAccessException exception) {
            throw traducir(exception, "crear_solicitud");
        }
    }

    /** Prepara el reenvio: valida pertenencia, estado y plazo y devuelve datos para precargar. */
    public ReenvioPreparado prepararReenvio(SessionPrincipal principal, long idSolicitud) {
        SolicitudConRespuestas actual = reenviable(principal, idSolicitud);
        ConvocatoriaFormulario convocatoria = convocatoriaAbierta(principal, actual.getIdConvocatoria());
        if (convocatoria.buscarTipo(actual.getIdTipoSolicitud()) == null) {
            throw new ReglaNegocioException("El tipo de solicitud ya no está disponible en esta convocatoria.");
        }
        FormularioDetalle formulario = cargarFormulario(convocatoria.getIdConvocatoria().longValue(),
                actual.getIdTipoSolicitud().intValue());
        LOG.info("REENVIO_PREPARADO idSolicitud={} rol={}", idSolicitud, principal.getRole());
        return new ReenvioPreparado(actual, convocatoria, formulario);
    }

    /**
     * Confirma el reenvio: reemplaza tipo y respuestas en la misma solicitud
     * (no crea otra) y la deja en PENDIENTE. Repite todas las verificaciones.
     */
    public void reenviar(SessionPrincipal principal, long idSolicitud, EnvioSolicitudRequest request) {
        if (request == null || request.getIdTipoSolicitud() == null) {
            throw new ValidacionRespuestasException("Indique el tipo de solicitud.",
                    Collections.<ValidacionRespuestasException.ErrorCampo>emptyList());
        }
        SolicitudConRespuestas actual = reenviable(principal, idSolicitud);
        ConvocatoriaFormulario convocatoria = convocatoriaAbierta(principal, actual.getIdConvocatoria());
        ConvocatoriaFormulario.Tipo tipo = convocatoria.buscarTipo(request.getIdTipoSolicitud());
        if (tipo == null) {
            throw new ReglaNegocioException("El tipo de solicitud no está disponible en esta convocatoria.");
        }

        FormularioDetalle formulario = cargarFormulario(convocatoria.getIdConvocatoria().longValue(),
                tipo.getIdTipoSolicitud().intValue());
        String json = validar(formulario, request);
        try {
            repository.reenviarSolicitud(principal, idSolicitud, tipo.getIdTipoSolicitud().intValue(),
                    tipo.getIdFormulario().longValue(), json);
            LOG.info("REENVIO_CONFIRMADO idSolicitud={} rol={}", idSolicitud, principal.getRole());
        } catch (DataAccessException exception) {
            throw traducir(exception, "reenviar_solicitud");
        }
    }

    /** Solicitud propia en un estado que permite reenviarla (RECHAZADO, CANCELADO o RETIRADO). */
    private SolicitudConRespuestas reenviable(SessionPrincipal principal, long idSolicitud) {
        SolicitudConRespuestas actual = misRespuestas(principal, idSolicitud);
        Integer estatus = actual.getIdEstatusSolicitud();
        if (estatus == null || (estatus.intValue() != ESTATUS_RECHAZADO
                && estatus.intValue() != ESTATUS_CANCELADO && estatus.intValue() != ESTATUS_RETIRADO)) {
            throw new ReglaNegocioException(
                    "Solo se pueden reenviar solicitudes retiradas, rechazadas o canceladas.");
        }
        return actual;
    }

    private ConvocatoriaFormulario convocatoriaAbierta(SessionPrincipal principal, Long idConvocatoria) {
        for (ConvocatoriaFormulario convocatoria : convocatoriasAbiertas(principal)) {
            if (convocatoria.getIdConvocatoria() != null && convocatoria.getIdConvocatoria().equals(idConvocatoria)) {
                return convocatoria;
            }
        }
        throw new ReglaNegocioException("El periodo de recepción de esta convocatoria no está abierto.");
    }

    private ConvocatoriaFormulario.Tipo tipoDisponible(SessionPrincipal principal, long idConvocatoria,
            Integer idTipoSolicitud) {
        ConvocatoriaFormulario convocatoria = convocatoriaAbierta(principal, Long.valueOf(idConvocatoria));
        ConvocatoriaFormulario.Tipo tipo = convocatoria.buscarTipo(idTipoSolicitud);
        if (tipo == null) {
            throw new ReglaNegocioException("El tipo de solicitud no está disponible en esta convocatoria.");
        }
        return tipo;
    }

    private FormularioDetalle cargarFormulario(long idConvocatoria, int idTipoSolicitud) {
        try {
            return repository.obtenerFormulario(idConvocatoria, idTipoSolicitud);
        } catch (DataAccessException exception) {
            throw traducir(exception, "obtener_formulario");
        }
    }

    private String validar(FormularioDetalle formulario, EnvioSolicitudRequest request) {
        try {
            return ValidadorRespuestas.validarYConstruirJson(formulario, request.getRespuestas());
        } catch (ValidacionRespuestasException exception) {
            LOG.warn("VALIDACION_FALLIDA errores={}", exception.getErrores().size());
            throw exception;
        }
    }

    private RuntimeException traducir(DataAccessException exception, String operacion) {
        RuntimeException traducida = TraductorErroresOracle.traducir(exception);
        if (traducida != exception) {
            Integer codigo = TraductorErroresOracle.codigoOracle(exception);
            LOG.warn("ORACLE_RECHAZO formulario operacion={} codigoOracle={} respuesta={}", operacion,
                    Integer.valueOf(Math.abs(codigo.intValue())),
                    traducida.getClass().getSimpleName());
        }
        return traducida;
    }
}
