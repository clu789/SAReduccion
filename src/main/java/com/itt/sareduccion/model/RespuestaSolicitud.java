package com.itt.sareduccion.model;

public final class RespuestaSolicitud {

    private final Long idRespuesta;
    private final Long idSolicitud;
    private final Long idPregunta;
    private final String textoPregunta;
    private final String obligatoria;
    private final String tipoPregunta;
    private final Integer orden;
    private final Long idOpcion;
    private final String textoOpcion;
    private final String textoRespuesta;

    public RespuestaSolicitud(Long idRespuesta, Long idSolicitud, Long idPregunta,
            Long idOpcion, String textoRespuesta) {
        this(idRespuesta, idSolicitud, idPregunta, null, null, null, null, idOpcion,
                null, textoRespuesta);
    }

    public RespuestaSolicitud(Long idRespuesta, Long idSolicitud, Long idPregunta,
            String textoPregunta, String obligatoria, String tipoPregunta, Integer orden,
            Long idOpcion, String textoOpcion, String textoRespuesta) {
        this.idRespuesta = idRespuesta;
        this.idSolicitud = idSolicitud;
        this.idPregunta = idPregunta;
        this.textoPregunta = textoPregunta;
        this.obligatoria = obligatoria;
        this.tipoPregunta = tipoPregunta;
        this.orden = orden;
        this.idOpcion = idOpcion;
        this.textoOpcion = textoOpcion;
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

    public String getTextoPregunta() {
        return textoPregunta;
    }

    public String getObligatoria() {
        return obligatoria;
    }

    public String getTipoPregunta() {
        return tipoPregunta;
    }

    public Integer getOrden() {
        return orden;
    }

    public Long getIdOpcion() {
        return idOpcion;
    }

    public String getTextoOpcion() {
        return textoOpcion;
    }

    public String getTextoRespuesta() {
        return textoRespuesta;
    }
}
