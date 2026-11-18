package com.itt.sareduccion.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuerpo de las peticiones de envio inicial y de reenvio. El servidor nunca
 * toma del cliente la identidad del solicitante ni el id del formulario.
 */
public final class EnvioSolicitudRequest {

    private Long idConvocatoria;
    private Integer idTipoSolicitud;
    private List<RespuestaEnviada> respuestas = new ArrayList<RespuestaEnviada>();

    public Long getIdConvocatoria() {
        return idConvocatoria;
    }

    public void setIdConvocatoria(Long idConvocatoria) {
        this.idConvocatoria = idConvocatoria;
    }

    public Integer getIdTipoSolicitud() {
        return idTipoSolicitud;
    }

    public void setIdTipoSolicitud(Integer idTipoSolicitud) {
        this.idTipoSolicitud = idTipoSolicitud;
    }

    public List<RespuestaEnviada> getRespuestas() {
        return respuestas;
    }

    public void setRespuestas(List<RespuestaEnviada> respuestas) {
        this.respuestas = respuestas;
    }

    /** Respuesta capturada en pantalla para una pregunta. */
    public static final class RespuestaEnviada {
        private Long idPregunta;
        private Long idOpcion;
        private String texto;

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

        public String getTexto() {
            return texto;
        }

        public void setTexto(String texto) {
            this.texto = texto;
        }
    }
}
