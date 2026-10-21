package com.itt.sareduccion.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.itt.sareduccion.model.SolicitudAdmin;

/**
 * Prueba de integracion real contra Oracle de la API administrativa.
 * Se ejecuta solo cuando el entorno define {@code SAR_JDBC_TEST_ADMIN_ID} con
 * un administrador registrado (por ejemplo {@code SA01}) y la base de datos
 * de desarrollo esta levantada. Valida los flujos de lectura del panel sin
 * modificar datos.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SAR_JDBC_TEST_ADMIN_ID", matches = ".+")
class SolicitudAdminServiceIntegrationTest {

    @Autowired
    private SolicitudAdminService solicitudAdminService;

    @Test
    void listaYFiltraSolicitudesDeAdministrador() {
        String idAdministrador = System.getenv("SAR_JDBC_TEST_ADMIN_ID").trim();

        List<SolicitudAdmin> todas = solicitudAdminService.listar(idAdministrador, null, null, null);
        assertNotNull(todas);

        List<SolicitudAdmin> pendientes = solicitudAdminService.listar(idAdministrador, 1, null, null);
        for (SolicitudAdmin solicitud : pendientes) {
            assertEquals(Integer.valueOf(1), solicitud.getIdEstatusSolicitud());
        }

        if (!todas.isEmpty()) {
            SolicitudAdmin primera = todas.get(0);
            assertAll(
                    () -> assertNotNull(primera.getIdSolicitud()),
                    () -> assertNotNull(primera.getIdEstatusSolicitud()),
                    () -> assertTrue(primera.getSolicitante() != null || primera.getNumControl() != null));
        }
    }

    @Test
    void estadisticasCuadranConElTotalDeSolicitudes() {
        String idAdministrador = System.getenv("SAR_JDBC_TEST_ADMIN_ID").trim();

        Map<String, Object> estadisticas = solicitudAdminService.estadisticas(idAdministrador);
        assertNotNull(estadisticas.get("total"));

        int suma = ((Number) estadisticas.get("pendiente")).intValue()
                + ((Number) estadisticas.get("autorizado")).intValue()
                + ((Number) estadisticas.get("rechazado")).intValue()
                + ((Number) estadisticas.get("cancelado")).intValue()
                + ((Number) estadisticas.get("aplicado")).intValue();
        assertEquals(((Number) estadisticas.get("total")).intValue(), suma);
    }

    @Test
    void rechazarSinComentarioEsRechazadoPorReglaDeNegocio() {
        String idAdministrador = System.getenv("SAR_JDBC_TEST_ADMIN_ID").trim();
        assertThrows(ReglaNegocioException.class,
                () -> solicitudAdminService.rechazar(idAdministrador, 1L, "  "));
    }
}
