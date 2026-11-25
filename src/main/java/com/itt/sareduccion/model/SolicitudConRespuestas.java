package com.itt.sareduccion.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Solicitud propia con sus respuestas y el ultimo comentario del administrador (si existe). */
public final class SolicitudConRespuestas {

    private final Long idSolicitud;
    private final Long idConvocatoria;
    private final Integer idTipoSolicitud;
    private final Long idFormulario;
    private final Integer idEstatusSolicitud;
    private final LocalDateTime fechaSolicitud;
    private final LocalDateTime fechaActualizacion;
    private final String ultimoComentario;
    private final List<RespuestaGuardada> respuestas = new ArrayList<RespuestaGuardada>();

    public SolicitudConRespuestas(Long idSolicitud, Long idConvocatoria, Integer idTipoSolicitud, Long idFormulario,
            Integer idEstatusSolicitud, LocalDateTime fechaSolicitud, LocalDateTime fechaActualizacion,
            String ultimoComentario) {
        this.idSolicitud = idSolicitud;
        this.idConvocatoria = idConvocatoria;
        this.idTipoSolicitud = idTipoSolicitud;
        this.idFormulario = idFormulario;
        this.idEstatusSolicitud = idEstatusSolicitud;
        this.fechaSolicitud = fechaSolicitud;
        this.fechaActualizacion = fechaActualizacion;
        this.ultimoComentario = ultimoComentario;
    }

    public void agregarRespuesta(RespuestaGuardada respuesta) {
        respuestas.add(respuesta);
    }

    public Long getIdSolicitud() {
        return idSolicitud;
    }

    public Long getIdConvocatoria() {
        return idConvocatoria;
    }

    public Integer getIdTipoSolicitud() {
        return idTipoSolicitud;
    }

    public Long getIdFormulario() {
        return idFormulario;
    }

    public Integer getIdEstatusSolicitud() {
        return idEstatusSolicitud;
    }

    public LocalDateTime getFechaSolicitud() {
        return fechaSolicitud;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public String getUltimoComentario() {
        return ultimoComentario;
    }

    public List<RespuestaGuardada> getRespuestas() {
        return respuestas;
    }

    /** Respuesta ya guardada en Oracle. */
    public static final class RespuestaGuardada {
        private final Long idPregunta;
        private final Long idOpcion;
        private final String textoRespuesta;

        public RespuestaGuardada(Long idPregunta, Long idOpcion, String textoRespuesta) {
            this.idPregunta = idPregunta;
            this.idOpcion = idOpcion;
            this.textoRespuesta = textoRespuesta;
        }

        public Long getIdPregunta() {
            return idPregunta;
        }

        public Long getIdOpcion() {
            return idOpcion;
        }

        public String getTextoRespuesta() {
            return textoRespuesta;
        }
    }
}
