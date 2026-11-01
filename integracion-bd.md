# Integración del panel administrativo con la nueva BD

## A. Bloqueantes — el panel no puede funcionar

| # | Dónde | Problema | Cambio |
|---|---|---|---|
| 1 | AdministradorRepository.java líneas 42-43 | Invoca cambiar_estatus_solicitud y cambiar_estatus_deduccion, que **ya no existen** → PLS-00302 en cada acción | Sustituir por rechazar_solicitud(?,?,?), cancelar_deduccion(?,?,?) y añadir aplicar_deduccion(?,?) |
| 2 | AdministradorRepository.listarConvocatorias() ~línea 245 | **Columnas desalineadas.** El paquete devuelve 7 columnas (id, inicio, fin, limite_autorizacion, limite_aplicacion, id_periodo, anio) pero el código lee la 4 como periodo, la 5 como anio y la 6 como activa | Leer 6→id_periodo, 7→anio; calcular activa con las fechas o ampliar ConvocatoriaAdmin con los dos límites |
| 3 | SolicitudAdminService.aprobar() | Llama primero cambiarEstatusSolicitud(...AUTORIZADO) y luego autorizarDeduccion. En el paquete nuevo **autorizar_deduccion hace la transición 1→2 él mismo** | Eliminar la llamada a cambiarEstatusSolicitud; una sola invocación a autorizar_deduccion |

## B. Errores que impedirán aprobar aunque compile

| # | Problema | Detalle |
|---|---|---|
| 4 | **No hay cómo obtener los ids de monto** | autorizar_deduccion exige exactamente uno de p_id_monto_inscripcion/p_id_monto_reinscripcion (si no, -20871). listar_deducciones no los expone y SAR_BACKEND no tiene SELECT. Hoy el servicio usa solicitud.getIdConvocatoria() como id de monto → NO_DATA_FOUND en obtener_montos |
| 5 | **Comentario obligatorio — resuelto** | El comentario es opcional al autorizar, reautorizar y cancelar; solo el rechazo lo exige (-20874). |
| 6 | **Reautorización bloqueada** | El servicio solo permite aprobar desde PENDIENTE; el paquete acepta NOT IN (1,4), es decir también CANCELADO para reautorizar |
| 7 | **Rechazo sin motivo** | rechazar_solicitud exige p_comentario no vacío (-20874). Hoy el servicio lo valida pero no lo **envía**, porque la firma antigua no lo tenía |
| 8 | **/correccion es idéntico a /rechazar** | La BD solo tiene una transición a RECHAZADO(3). Hay que decidir si se mantiene como alias documentado o se retira |
| 9 | **Aplicar deducción no existe en el backend** | Falta método en repositorio, servicio, endpoint PUT /api/deducciones/{id}/aplicar y botón en el panel. La BD ya lo soporta |

## C. Catálogos desactualizados en el servicio

| # | Dónde | Cambio |
|---|---|---|
| 10 | TIPO_SOLICITUD_NOMBRES | Tiene 900001/900002; ahora son **1 Hijo de trabajador** y **2 Hijo de homologado** |
| 11 | ESTATUS_NOMBRES | Falta **6 RETIRADO** → se muestra "DESCONOCIDO". Además estadisticas() no cuenta el 6 y el panel no tiene chip para ese estado |
| 12 | traducirError() | Traduce códigos viejos (20823-20827, 20019-20027). Los vigentes son **-20870 a -20887** (paquete) y **-20001 a -20075** (triggers). Sin esto, los conflictos de negocio salen como 500 en vez de 409 |

## D. A revisar en la base (no bloquea el panel, sí la coherencia)

| # | Punto | Comentario |
|---|---|---|
| 13 | **Códigos de error duplicados** | -20011 se usa para "Conectarse como SA_REDUCCION" y para "Debe seleccionarse una opcion"; igual -20012. Conviene renumerar uno de los dos |
| 14 | **Sin procedimiento de historial** | El detalle devuelve historial: [] siempre. Falta listar_historial_solicitud / listar_historial_deduccion |
| 15 | **listar_solicitudes no trae nombre del solicitante** | Solo num_control/ficha y t.nombre (tipo de solicitud). El panel muestra el folio como "solicitante" |
| 16 | **Autorizar en convocatoria cerrada** | La convocatoria 900001 del seed no tiene filas en Convocatorias_TipoSolicitudes → NO_DATA_FOUND (error 500). Debe traducirse o impedirse antes |
| 17 | **Pendiente del equipo 4** | No hay crear_convocatoria ni alta de montos; el filtro de convocatorias solo mostrará las del seed |

## E. Paso operativo imprescindible

Los scripts init-db **solo se ejecutan con volumen vacío**. Si la base actual se creó con el 05 anterior, los procedimientos nuevos no existen aunque el archivo esté corregido. Antes de probar el panel:

```bash
docker compose --profile full-stack down -v     # destructivo: borra datos
docker compose --profile full-stack up -d --build
```

O bien reaplicar 05-crear-paquetes-permisos.sql manualmente sobre el esquema actual (ojo: también cambiaron tablas, así que reaplicar solo el 05 puede no ser suficiente).

---

## Orden de trabajo sugerido

1. **#1, #2, #3** (compilación y arranque del panel).
2. **#10, #11, #12** (catálogos; evitan resultados confusos).
3. **#7, #5, #6** (rechazo y aprobación funcionales).
4. **#4** — requiere decisión: ¿añadimos PKG_ADMINISTRADOR.listar_montos o el frontend envía los ids? Recomendación: un listar_montos(p_admin, p_resultado) que devuelva ambos catálogos, y que el servicio elija MIN(id_monto_reinscripcion) para estudiante y MIN(id_monto_inscripcion) para aspirante (igual que hace el seed).
5. **#9** (aplicar) y luego **#14, #15**.
