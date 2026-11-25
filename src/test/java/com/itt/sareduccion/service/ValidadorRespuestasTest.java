package com.itt.sareduccion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.itt.sareduccion.model.EnvioSolicitudRequest.RespuestaEnviada;
import com.itt.sareduccion.model.FormularioDetalle;
import com.itt.sareduccion.model.FormularioOpcion;
import com.itt.sareduccion.model.FormularioPregunta;
import com.itt.sareduccion.service.ValidacionRespuestasException.ErrorCampo;

/**
 * Pruebas del validador de respuestas del formulario (Equipo 5). No requieren
 * base de datos: reproducen el formulario 1 del seed (Clave, Nombre,
 * Parentesco MIXTA y Observaciones opcional) mas una pregunta OPCION_UNICA.
 */
class ValidadorRespuestasTest {

    private static FormularioDetalle formulario() {
        FormularioDetalle f = new FormularioDetalle(1L, "Hijos de trabajadores", "desc", "instrucciones", 1);
        f.agregarPregunta(new FormularioPregunta(1L, "Clave del trabajador", true,
                FormularioPregunta.TIPO_ABIERTA, 1));
        f.agregarPregunta(new FormularioPregunta(2L, "Nombre completo del trabajador", true,
                FormularioPregunta.TIPO_ABIERTA, 2));
        FormularioPregunta parentesco = new FormularioPregunta(3L, "Parentesco", true,
                FormularioPregunta.TIPO_MIXTA, 3);
        parentesco.agregarOpcion(new FormularioOpcion(1L, "Hijo o hija", 1, false));
        parentesco.agregarOpcion(new FormularioOpcion(2L, "Otro (especifique)", 2, true));
        f.agregarPregunta(parentesco);
        f.agregarPregunta(new FormularioPregunta(4L, "Observaciones (opcional)", false,
                FormularioPregunta.TIPO_ABIERTA, 4));
        FormularioPregunta comprobante = new FormularioPregunta(8L, "Cuenta con comprobante", true,
                FormularioPregunta.TIPO_OPCION_UNICA, 5);
        comprobante.agregarOpcion(new FormularioOpcion(10L, "Si", 1, false));
        comprobante.agregarOpcion(new FormularioOpcion(11L, "No", 2, false));
        f.agregarPregunta(comprobante);
        return f;
    }

    private static RespuestaEnviada r(Long pregunta, Long opcion, String texto) {
        RespuestaEnviada respuesta = new RespuestaEnviada();
        respuesta.setIdPregunta(pregunta);
        respuesta.setIdOpcion(opcion);
        respuesta.setTexto(texto);
        return respuesta;
    }

    private static List<RespuestaEnviada> base() {
        List<RespuestaEnviada> lista = new ArrayList<RespuestaEnviada>();
        lista.add(r(1L, null, "CLAVE-1"));
        lista.add(r(2L, null, "Nombre Prueba"));
        lista.add(r(3L, 2L, "Tutor legal"));
        lista.add(r(8L, 10L, null));
        return lista;
    }

    private static ValidacionRespuestasException falla(List<RespuestaEnviada> lista) {
        return assertThrows(ValidacionRespuestasException.class,
                () -> ValidadorRespuestas.validarYConstruirJson(formulario(), lista));
    }

    private static boolean hayError(ValidacionRespuestasException e, Long idPregunta) {
        for (ErrorCampo error : e.getErrores()) {
            if (idPregunta == null ? error.getIdPregunta() == null : idPregunta.equals(error.getIdPregunta())) {
                return true;
            }
        }
        return false;
    }

    @Test
    void respuestasValidasGeneranElJsonQueEsperaOracle() {
        String json = ValidadorRespuestas.validarYConstruirJson(formulario(), base());
        assertEquals("[{\"id_pregunta\":1,\"id_opcion\":null,\"texto_respuesta\":\"CLAVE-1\"},"
                + "{\"id_pregunta\":2,\"id_opcion\":null,\"texto_respuesta\":\"Nombre Prueba\"},"
                + "{\"id_pregunta\":3,\"id_opcion\":2,\"texto_respuesta\":\"Tutor legal\"},"
                + "{\"id_pregunta\":8,\"id_opcion\":10,\"texto_respuesta\":null}]", json);
    }

    @Test
    void laPreguntaOpcionalSinRespuestaNoSeEnvia() {
        String json = ValidadorRespuestas.validarYConstruirJson(formulario(), base());
        assertFalse(json.contains("\"id_pregunta\":4"));
    }

    @Test
    void laPreguntaOpcionalRespondidaSiSeEnvia() {
        List<RespuestaEnviada> lista = base();
        lista.add(r(4L, null, "Ninguna"));
        assertTrue(ValidadorRespuestas.validarYConstruirJson(formulario(), lista).contains("\"id_pregunta\":4"));
    }

    @Test
    void faltaUnaPreguntaObligatoria() {
        List<RespuestaEnviada> lista = base();
        lista.remove(0);
        ValidacionRespuestasException e = falla(lista);
        assertTrue(hayError(e, 1L));
        assertEquals(1, e.getErrores().size());
    }

    @Test
    void textoSoloConEspaciosCuentaComoSinRespuesta() {
        List<RespuestaEnviada> lista = base();
        lista.set(0, r(1L, null, "    "));
        assertTrue(hayError(falla(lista), 1L));
    }

    @Test
    void sinNingunaRespuestaMarcaTodasLasObligatorias() {
        ValidacionRespuestasException e = falla(new ArrayList<RespuestaEnviada>());
        assertTrue(hayError(e, 1L) && hayError(e, 2L) && hayError(e, 3L) && hayError(e, 8L));
        assertFalse(hayError(e, 4L));
    }

    @Test
    void elTextoSeRecortaAntesDeEnviarse() {
        List<RespuestaEnviada> lista = base();
        lista.set(0, r(1L, null, "  CLAVE-1  "));
        assertTrue(ValidadorRespuestas.validarYConstruirJson(formulario(), lista)
                .contains("\"texto_respuesta\":\"CLAVE-1\""));
    }

    @Test
    void mixtaConOpcionQueRequiereEspecificacionExigeTexto() {
        List<RespuestaEnviada> lista = base();
        lista.set(2, r(3L, 2L, null));
        assertTrue(hayError(falla(lista), 3L));
    }

    @Test
    void mixtaConOpcionQueNoRequiereEspecificacionNoAdmiteTexto() {
        List<RespuestaEnviada> lista = base();
        lista.set(2, r(3L, 1L, "texto de mas"));
        assertTrue(hayError(falla(lista), 3L));
    }

    @Test
    void mixtaConOpcionSimpleSeAcepta() {
        List<RespuestaEnviada> lista = base();
        lista.set(2, r(3L, 1L, null));
        assertTrue(ValidadorRespuestas.validarYConstruirJson(formulario(), lista)
                .contains("{\"id_pregunta\":3,\"id_opcion\":1,\"texto_respuesta\":null}"));
    }

    @Test
    void mixtaSinOpcionEsInvalida() {
        List<RespuestaEnviada> lista = base();
        lista.set(2, r(3L, null, "solo texto"));
        assertTrue(hayError(falla(lista), 3L));
    }

    @Test
    void abiertaNoAdmiteOpcion() {
        List<RespuestaEnviada> lista = base();
        lista.set(0, r(1L, 5L, "CLAVE-1"));
        assertTrue(hayError(falla(lista), 1L));
    }

    @Test
    void opcionUnicaNoAdmiteTextoAdicional() {
        List<RespuestaEnviada> lista = base();
        lista.set(3, r(8L, 10L, "extra"));
        assertTrue(hayError(falla(lista), 8L));
    }

    @Test
    void opcionDeOtraPreguntaSeRechaza() {
        List<RespuestaEnviada> lista = base();
        lista.set(3, r(8L, 1L, null));
        assertTrue(hayError(falla(lista), 8L));
    }

    @Test
    void preguntaQueNoPerteneceAlFormularioSeRechaza() {
        List<RespuestaEnviada> lista = base();
        lista.add(r(999L, null, "intruso"));
        assertTrue(hayError(falla(lista), 999L));
    }

    @Test
    void preguntaRepetidaSeRechaza() {
        List<RespuestaEnviada> lista = base();
        lista.add(r(1L, null, "otra vez"));
        assertTrue(hayError(falla(lista), 1L));
    }

    @Test
    void respuestaSinPreguntaSeRechaza() {
        List<RespuestaEnviada> lista = base();
        lista.add(r(null, null, "huerfana"));
        assertTrue(hayError(falla(lista), null));
    }

    @Test
    void textoMuyLargoSeRechaza() {
        StringBuilder largo = new StringBuilder();
        for (int i = 0; i <= ValidadorRespuestas.MAX_TEXTO; i++) {
            largo.append('a');
        }
        List<RespuestaEnviada> lista = base();
        lista.set(0, r(1L, null, largo.toString()));
        assertTrue(hayError(falla(lista), 1L));
    }

    @Test
    void caracteresDeControlSeRechazan() {
        List<RespuestaEnviada> lista = base();
        lista.set(0, r(1L, null, "clave\u0000x"));
        assertTrue(hayError(falla(lista), 1L));
    }

    @Test
    void saltosDeLineaSePermiten() {
        List<RespuestaEnviada> lista = base();
        lista.set(0, r(1L, null, "linea1\nlinea2"));
        assertTrue(ValidadorRespuestas.validarYConstruirJson(formulario(), lista).contains("linea1\\nlinea2"));
    }

    @Test
    void lasComillasSeEscapanEnElJson() {
        List<RespuestaEnviada> lista = base();
        lista.set(0, r(1L, null, "Dijo \"hola\" y \\ adios"));
        String json = ValidadorRespuestas.validarYConstruirJson(formulario(), lista);
        assertTrue(json.contains("Dijo \\\"hola\\\" y \\\\ adios"));
    }

    @Test
    void listaNulaEquivaleASinRespuestas() {
        assertThrows(ValidacionRespuestasException.class,
                () -> ValidadorRespuestas.validarYConstruirJson(formulario(), null));
    }
}
