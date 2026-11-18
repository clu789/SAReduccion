# Pruebas del formulario (Equipo 5)

Verifican el comportamiento de `formulario.html` y `js/formulario.js` sin necesitar Oracle, Docker ni un navegador:
las respuestas del backend se simulan. Requieren Node.js 16 o superior.

```
cd modulo-solicitante/pruebas
npm install
npm test
```

Cubren: preguntas ABIERTA, OPCION_UNICA y MIXTA; validaciones; envio con token CSRF; errores 400, 401, 403, 409 y 500;
solicitud existente; reenvio (precarga, cambio de tipo, estados no permitidos); aspirantes; roles; y proteccion contra XSS.
