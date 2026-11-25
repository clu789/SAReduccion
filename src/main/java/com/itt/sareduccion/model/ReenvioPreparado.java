package com.itt.sareduccion.model;

/**
 * Datos para precargar el reenvio: la solicitud con sus respuestas actuales,
 * la convocatoria (con los tipos disponibles) y el formulario del tipo actual.
 */
public final class ReenvioPreparado {

    private final SolicitudConRespuestas solicitud;
    private final ConvocatoriaFormulario convocatoria;
    private final FormularioDetalle formulario;

    public ReenvioPreparado(SolicitudConRespuestas solicitud, ConvocatoriaFormulario convocatoria,
            FormularioDetalle formulario) {
        this.solicitud = solicitud;
        this.convocatoria = convocatoria;
        this.formulario = formulario;
    }

    public SolicitudConRespuestas getSolicitud() {
        return solicitud;
    }

    public ConvocatoriaFormulario getConvocatoria() {
        return convocatoria;
    }

    public FormularioDetalle getFormulario() {
        return formulario;
    }
}
