package com.itt.sareduccion.model;

public final class RespuestaSolicitud {

    private final Long idRespuesta;
    private final Long idSolicitud;
    private final Long idPregunta;
    private final Long idOpcion;
    private final String textoRespuesta;

    public RespuestaSolicitud(Long idRespuesta, Long idSolicitud, Long idPregunta,
            Long idOpcion, String textoRespuesta) {
        this.idRespuesta = idRespuesta;
        this.idSolicitud = idSolicitud;
        this.idPregunta = idPregunta;
        this.idOpcion = idOpcion;
        this.textoRespuesta = textoRespuesta;
    }

    public Long getIdRespuesta() {
        return idRespuesta;
    }

    public Long getIdSolicitud() {
        return idSolicitud;
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
