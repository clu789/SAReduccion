/**
 * Pruebas del formulario dinamico (Equipo 5), sin base de datos ni navegador:
 * ejecutan formulario.html + js/app.js + js/formulario.js en jsdom y simulan
 * las respuestas del backend (/api/solicitante). Uso:
 *     cd modulo-solicitante/pruebas
 *     npm install
 *     npm test
 */
const { JSDOM, VirtualConsole } = require('jsdom');
const fs = require('fs');
const path = require('path');
const { pathToFileURL } = require('url');
const RAIZ = path.resolve(__dirname, '..') + path.sep;  // carpeta modulo-solicitante
const COMPARTIDO = path.resolve(RAIZ, '..', 'modulo-compartido') + path.sep;

const f = (id, tipo, texto, oblig, ops) => ({ idPregunta: id, texto, obligatoria: oblig, tipo, orden: id, opciones: ops || [] });
const FORM1 = { idFormulario: 1, nombre: 'Hijos de trabajadores', descripcion: 'd', instrucciones: 'Responda todas las preguntas.\nRequisitos:', idTipoSolicitud: 1, preguntas: [
  f(1, 'ABIERTA', 'Clave del trabajador', true), f(2, 'ABIERTA', 'Nombre completo del trabajador', true),
  f(3, 'MIXTA', 'Parentesco con el trabajador', true, [{ idOpcion: 1, texto: 'Hijo o hija', orden: 1, requiereEspecificacion: false }, { idOpcion: 2, texto: 'Otro (especifique)', orden: 2, requiereEspecificacion: true }]),
  f(4, 'ABIERTA', 'Observaciones adicionales (opcional)', false)] };
const FORM2 = { idFormulario: 2, nombre: 'Hijos de homologados', descripcion: 'd', instrucciones: 'Instrucciones 2', idTipoSolicitud: 2, preguntas: [
  f(5, 'ABIERTA', 'Clave homologado', true),
  f(8, 'OPCION_UNICA', 'Cuenta con comprobante', true, [{ idOpcion: 10, texto: 'Si', orden: 1, requiereEspecificacion: false }, { idOpcion: 11, texto: 'No', orden: 2, requiereEspecificacion: false }])] };
const CONV_FILAS = (extra) => [
  Object.assign({
    idConvocatoria: 900002, periodo: 2, periodoDescripcion: 'Enero-Junio', anio: 2026,
    fechaInicio: '2026-11-04', fechaFin: '2026-11-25', idTipoSolicitud: 1,
    tipoSolicitud: 'Hijo de trabajador', idFormulario: 1, idSolicitudExistente: null, idEstatusSolicitud: null, puedeIniciar: true,
  }, extra || {}),
  Object.assign({
    idConvocatoria: 900002, periodo: 2, periodoDescripcion: 'Enero-Junio', anio: 2026,
    fechaInicio: '2026-11-04', fechaFin: '2026-11-25', idTipoSolicitud: 2,
    tipoSolicitud: 'Hijo de homologado', idFormulario: 2, idSolicitudExistente: null, idEstatusSolicitud: null, puedeIniciar: true,
  }, extra || {}),
];
const CONV = (extra) => Object.assign({ idConvocatoria: 900002, idPeriodo: 2, anio: 2026, fechaInicio: '2026-11-04', fechaFin: '2026-11-25', idSolicitud: null, idEstatusSolicitud: null,
  tipos: [{ idTipoSolicitud: 1, nombre: 'Hijo de trabajador', idFormulario: 1 }, { idTipoSolicitud: 2, nombre: 'Hijo de homologado', idFormulario: 2 }] }, extra || {});

function espera(cond, ms) { return new Promise((ok, mal) => { const t0 = Date.now(); (function r() { let v; try { v = cond(); } catch (e) { v = false; } if (v) return ok(v); if (Date.now() - t0 > (ms || 3000)) return mal(new Error('timeout esperando condicion: ' + cond)); setTimeout(r, 10); })(); }); }

async function crear(op) {
  op = op || {};
  const reqs = [];
  const sesion = op.sesion || { authenticated: true, role: 'STUDENT', identity: '21281181', csrfToken: 'tok-123' };
  let html = fs.readFileSync(path.join(RAIZ, 'formulario.html'), 'utf8')
    .replace(/<link[^>]+(googleapis|cdnjs|itt-security\.css)[^>]*>/g, '')
    .replace(/src="\/compartido\/([^"]+)"/g, (m, r) => 'src="' + pathToFileURL(COMPARTIDO + r.split('/').join(path.sep)).href + '"')
    .replace(/src="js\/(app|formulario)\.js"/g, (m, n) => 'src="' + pathToFileURL(RAIZ + 'js' + path.sep + n + '.js').href + '"');
  const servidor = (metodo, url, headers, cuerpo) => {
    reqs.push({ metodo, url, headers, cuerpo });
    if (op.servidor) { const r = op.servidor(metodo, url, headers, cuerpo); if (r) return r; }
    if (url === '/api/auth/me') return sesion.authenticated ? { status: 200, body: sesion } : { status: 401, body: Object.assign({}, sesion) };
    if (url === '/api/solicitante/perfil') return { status: 200, body: op.perfil || { tipo: 'ESTUDIANTE', identificador: '21281181', nombreCompleto: 'Sofia Lopez Garcia', carrera: 'Sistemas', semestre: 5 } };
    if (url === '/api/solicitante/convocatorias') return { status: 200, body: op.convocatorias || CONV_FILAS() };
    let m = url.match(/^\/api\/solicitante\/formulario\?convocatoria=(\d+)&tipo=(\d+)$/);
    if (m) return { status: 200, body: m[2] === '1' ? FORM1 : FORM2 };
    if (metodo === 'POST' && url === '/api/solicitante/solicitudes') return headers['X-CSRF-TOKEN'] === 'tok-123' ? { status: 201, body: { idSolicitud: 42, estatus: 'PENDIENTE' } } : { status: 403, body: { error: 'Solicitud no válida', errores: [] } };
    return { status: 404, body: { error: 'x', errores: [] } };
  };
  const consola = new VirtualConsole();
  const errores = [];
  consola.on('jsdomError', (e) => { const m = String(e.message || e); if (!/Could not load (link|img)/.test(m)) errores.push(m); });
  consola.on('error', (e) => errores.push(String(e)));
  const dom = new JSDOM(html, { url: 'http://localhost/formulario.html' + (op.query || ''), runScripts: 'dangerously', resources: 'usable', pretendToBeVisual: true, virtualConsole: consola,
    beforeParse(w) {
      w.confirm = () => (op.confirmar === undefined ? true : op.confirmar);
      w.fetch = async (url, o) => {
        o = o || {};
        const cuerpo = o.body ? JSON.parse(o.body) : null;
        const headers = {};
        if (o.headers && typeof o.headers.forEach === 'function') {
          o.headers.forEach((v, k) => {
            headers[k] = v;
            if (k.toLowerCase() === 'x-csrf-token') headers['X-CSRF-TOKEN'] = v;
          });
        } else {
          Object.assign(headers, o.headers || {});
        }
        const r = servidor((o.method || 'GET'), url, headers, cuerpo);
        const resp = { ok: r.status >= 200 && r.status < 300, status: r.status, statusText: '', json: async () => r.body };
        resp.clone = () => resp;
        return resp;
      };
    } });
  const doc = dom.window.document;
  await espera(() => dom.window.document.readyState === 'complete');
  return { dom, w: dom.window, doc, reqs, errores };
}

let total = 0, fallos = 0;
function ok(cond, nombre) { total++; if (cond) console.log('  OK    ' + nombre); else { fallos++; console.log('  FALLA ' + nombre); } }
const txt = (n) => (n ? n.textContent.replace(/\s+/g, ' ').trim() : null);
const visible = (n) => n && !n.classList.contains('hidden');

(async () => {
  console.log('\n[1] Estudiante: flujo nuevo completo');
  let t = await crear();
  let { doc, w, reqs } = t;
  await espera(() => txt(doc.getElementById('info-nombre')) === 'Sofia Lopez Garcia');
  ok(txt(doc.getElementById('info-extra')) === '5.º semestre', 'tarjeta con semestre');
  ok(doc.getElementById('nav-panel').getAttribute('href') === 'panel-estatus.html', 'enlace al panel del estudiante');
  await espera(() => doc.querySelectorAll('input[name="tipo-solicitud"]').length === 2);
  ok(doc.querySelectorAll('.pregunta').length === 0, 'sin preguntas hasta elegir el tipo');
  ok(doc.getElementById('btn-siguiente').disabled, 'Siguiente deshabilitado sin tipo');
  doc.getElementById('tipo-1').click();
  await espera(() => doc.querySelectorAll('.pregunta').length === 4);
  ok(txt(doc.getElementById('instrucciones')).includes('Responda todas las preguntas.'), 'instrucciones visibles');
  ok(doc.querySelectorAll('textarea').length === 4, '4 campos de texto (3 ABIERTA + la especificacion oculta de la MIXTA)');
  ok(doc.querySelectorAll('input[name="p3"]').length === 2, 'MIXTA con 2 opciones');
  ok(!visible(doc.getElementById('p3-esp')), 'especificacion oculta al inicio');
  ok(txt(doc.getElementById('p4-lbl')).includes('(opcional)') && !doc.getElementById('p4-lbl').querySelector('.requerido'), 'pregunta opcional sin asterisco');
  doc.getElementById('btn-siguiente').click();
  ok(['p1', 'p2', 'p3'].every((p) => doc.getElementById(p + '-error').classList.contains('visible')), 'errores en las 3 obligatorias');
  ok(txt(doc.getElementById('p3-error')) === 'Seleccione una opción.', 'mensaje de MIXTA sin opcion');
  ok(visible(doc.getElementById('paso-1')), 'se queda en el paso 1');
  doc.getElementById('p1').value = 'CLAVE-1'; doc.getElementById('p1').dispatchEvent(new w.Event('input', { bubbles: true }));
  ok(!doc.getElementById('p1-error').classList.contains('visible'), 'el error se limpia al escribir');
  doc.getElementById('p2').value = '  Trabajador Prueba  ';
  doc.getElementById('p3-o2').click();
  ok(visible(doc.getElementById('p3-esp')), 'la especificacion aparece con la opcion que la requiere');
  doc.getElementById('btn-siguiente').click();
  ok(txt(doc.getElementById('p3-error')) === 'Especifique su respuesta.', 'exige especificacion');
  doc.getElementById('p3-txt').value = 'Tutor legal';
  doc.getElementById('p3-o1').click();
  ok(!visible(doc.getElementById('p3-esp')) && doc.getElementById('p3-txt').value === '', 'cambiar de opcion oculta y limpia la especificacion');
  doc.getElementById('p3-o2').click(); doc.getElementById('p3-txt').value = 'Tutor legal';
  doc.getElementById('btn-siguiente').click();
  ok(visible(doc.getElementById('paso-2')) && !visible(doc.getElementById('paso-1')), 'pasa al paso 2');
  const res = txt(doc.getElementById('resumen-solicitud'));
  ok(res.includes('Hijo de trabajador') && res.includes('Trabajador Prueba') && res.includes('Otro (especifique) — Tutor legal') && res.includes('Sin respuesta'), 'resumen correcto (texto recortado y pregunta opcional sin respuesta)');
  ok(doc.getElementById('btn-enviar').disabled, 'Enviar deshabilitado hasta confirmar');
  doc.getElementById('confirmacion-datos').click();
  ok(!doc.getElementById('btn-enviar').disabled, 'Enviar habilitado al confirmar');
  doc.getElementById('btn-enviar').click();
  await espera(() => txt(doc.getElementById('zona-estado')).includes('Folio 42'));
  const post = reqs.filter((r) => r.metodo === 'POST')[0];
  ok(post && post.url === '/api/solicitante/solicitudes' && post.headers['X-CSRF-TOKEN'] === 'tok-123', 'POST con token CSRF');
  ok(JSON.stringify(post.cuerpo) === JSON.stringify({ idConvocatoria: 900002, idTipoSolicitud: 1, respuestas: [
    { idPregunta: 1, idOpcion: null, texto: 'CLAVE-1' }, { idPregunta: 2, idOpcion: null, texto: 'Trabajador Prueba' }, { idPregunta: 3, idOpcion: 2, texto: 'Tutor legal' }] }), 'cuerpo del POST correcto (sin identidad, sin la opcional vacia)');
  ok(!('idUsuario' in post.cuerpo) && !('numeroControl' in post.cuerpo), 'no se envia identidad');
  ok(txt(doc.getElementById('zona-estado')).includes('Estado PENDIENTE'), 'pantalla de exito');
  ok(t.errores.length === 0, 'sin errores de JavaScript'); if (t.errores.length) console.log('    detalle:', JSON.stringify(t.errores.slice(0,4)).slice(0,600));

  console.log('\n[2] Tipo 2 con OPCION_UNICA y cambio de tipo');
  t = await crear(); ({ doc, w, reqs } = t);
  await espera(() => doc.querySelectorAll('input[name="tipo-solicitud"]').length === 2);
  doc.getElementById('tipo-1').click(); await espera(() => doc.querySelectorAll('.pregunta').length === 4);
  doc.getElementById('p1').value = 'algo';
  doc.getElementById('tipo-2').click(); await espera(() => doc.querySelectorAll('.pregunta').length === 2);
  ok(doc.querySelectorAll('input[name="p8"]').length === 2, 'OPCION_UNICA con sus 2 opciones');
  ok(doc.getElementById('tipo-2').checked, 'el selector refleja el nuevo tipo');
  doc.getElementById('p5').value = 'H-1'; doc.getElementById('btn-siguiente').click();
  ok(txt(doc.getElementById('p8-error')) === 'Seleccione una opción.', 'OPCION_UNICA obligatoria');
  doc.getElementById('p8-o10').click(); doc.getElementById('btn-siguiente').click();
  ok(visible(doc.getElementById('paso-2')), 'avanza con OPCION_UNICA respondida');
  doc.getElementById('btn-anterior').click();
  ok(visible(doc.getElementById('paso-1')) && doc.getElementById('p5').value === 'H-1' && doc.getElementById('p8-o10').checked, 'Anterior conserva las respuestas');
  t = await crear({ confirmar: false }); ({ doc, w } = t);
  await espera(() => doc.querySelectorAll('input[name="tipo-solicitud"]').length === 2);
  doc.getElementById('tipo-1').click(); await espera(() => doc.querySelectorAll('.pregunta').length === 4);
  doc.getElementById('p1').value = 'algo'; doc.getElementById('tipo-2').click();
  await new Promise((r) => setTimeout(r, 100));
  ok(doc.getElementById('tipo-1').checked && doc.querySelectorAll('.pregunta').length === 4 && doc.getElementById('p1').value === 'algo', 'si no se confirma el cambio de tipo, se conservan las respuestas');

  console.log('\n[3] Errores del servidor al enviar');
  const llenar = async (doc) => { doc.getElementById('tipo-1').click(); await espera(() => doc.querySelectorAll('.pregunta').length === 4); doc.getElementById('p1').value = 'C'; doc.getElementById('p2').value = 'N'; doc.getElementById('p3-o1').click(); doc.getElementById('btn-siguiente').click(); doc.getElementById('confirmacion-datos').click(); doc.getElementById('btn-enviar').click(); };
  t = await crear({ servidor: (m, u) => (m === 'POST' ? { status: 400, body: { error: 'Revise', errores: [{ idPregunta: 2, mensaje: 'El texto contiene caracteres no permitidos.' }] } } : null) }); ({ doc } = t);
  await espera(() => doc.querySelectorAll('input[name="tipo-solicitud"]').length === 2); await llenar(doc);
  await espera(() => doc.getElementById('p2-error').classList.contains('visible'));
  ok(visible(doc.getElementById('paso-1')) && txt(doc.getElementById('p2-error')).includes('caracteres no permitidos'), '400: vuelve al paso 1 y marca la pregunta');
  ok(txt(doc.getElementById('alerta-formulario')).includes('corregir'), '400: aviso general');
  t = await crear({ servidor: (m) => (m === 'POST' ? { status: 409, body: { error: 'Ya existe una solicitud para esta convocatoria.', errores: [] } } : null) }); ({ doc } = t);
  await espera(() => doc.querySelectorAll('input[name="tipo-solicitud"]').length === 2); await llenar(doc);
  await espera(() => txt(doc.getElementById('alerta-formulario')).includes('Ya existe una solicitud'));
  ok(true, '409: muestra el mensaje del servidor');
  ok(doc.querySelector('#alerta-formulario a') !== null, '409: ofrece ir al panel');
  t = await crear({ servidor: (m, u) => (m === 'POST' ? { status: 401, body: { error: 'x', errores: [] } } : null) }); ({ doc } = t);
  await espera(() => doc.querySelectorAll('input[name="tipo-solicitud"]').length === 2); await llenar(doc);
  await espera(() => txt(doc.getElementById('alerta-formulario')).includes('sesión expiró'));
  ok(doc.querySelector('#alerta-formulario a').getAttribute('href') === 'login.html' && !visible(doc.getElementById('form-solicitud')), '401: sesion expirada con enlace al login y formulario oculto');
  t = await crear({ servidor: (m) => (m === 'POST' ? { status: 403, body: { error: 'x', errores: [] } } : null) }); ({ doc } = t);
  await espera(() => doc.querySelectorAll('input[name="tipo-solicitud"]').length === 2); await llenar(doc);
  await espera(() => txt(doc.getElementById('alerta-formulario')).includes('Acceso no permitido'));
  ok(true, '403: aviso de acceso no permitido');
  t = await crear({ servidor: (m) => (m === 'POST' ? { status: 500, body: { error: 'ORA-00600 interno' } } : null) }); ({ doc } = t);
  await espera(() => doc.querySelectorAll('input[name="tipo-solicitud"]').length === 2); await llenar(doc);
  await espera(() => txt(doc.getElementById('alerta-formulario')).includes('No fue posible'));
  ok(!txt(doc.getElementById('alerta-formulario')).includes('ORA-'), '500: mensaje generico, sin detalles internos');

  console.log('\n[4] Solicitud existente y sin convocatoria');
  t = await crear({ convocatorias: CONV_FILAS({ idSolicitudExistente: 7, idEstatusSolicitud: 1 }) }); ({ doc } = t);
  await espera(() => txt(doc.getElementById('alerta-formulario')).includes('folio 7'));
  ok(!visible(doc.getElementById('form-solicitud')) && txt(doc.getElementById('alerta-formulario')).includes('PENDIENTE'), 'PENDIENTE: no muestra formulario');
  t = await crear({ convocatorias: CONV_FILAS({ idSolicitudExistente: 7, idEstatusSolicitud: 3 }) }); ({ doc } = t);
  await espera(() => txt(doc.getElementById('alerta-formulario')).includes('folio 7'));
  ok(Array.from(doc.querySelectorAll('#alerta-formulario a')).some((a) => a.getAttribute('href') === 'formulario.html?modo=reenvio&solicitud=7'), 'RECHAZADO: ofrece reenviar');
  t = await crear({ convocatorias: [] }); ({ doc } = t);
  await espera(() => txt(doc.getElementById('alerta-formulario')).includes('No hay una convocatoria'));
  ok(!visible(doc.getElementById('form-solicitud')), 'sin convocatoria abierta: sin formulario');
  t = await crear({ query: '?convocatoria=900002&tipo=2' }); ({ doc } = t);
  await espera(() => doc.querySelectorAll('.pregunta').length === 2);
  ok(doc.getElementById('tipo-2').checked && doc.getElementById('p5') !== null, 'query params ?convocatoria=&tipo= preseleccionan el formulario');

  console.log('\n[5] Reenvio');
  const prep = { solicitud: { idSolicitud: 42, idConvocatoria: 900002, idTipoSolicitud: 1, idFormulario: 1, idEstatusSolicitud: 3, fechaSolicitud: '2026-11-15T05:33:29', fechaActualizacion: null, ultimoComentario: 'Falta la clave correcta',
    respuestas: [{ idPregunta: 1, idOpcion: null, textoRespuesta: 'CLAVE-VIEJA' }, { idPregunta: 2, idOpcion: null, textoRespuesta: 'Nombre viejo' }, { idPregunta: 3, idOpcion: 2, textoRespuesta: 'Tutor legal' }] },
    convocatoria: CONV({ idSolicitud: 42, idEstatusSolicitud: 3 }), formulario: FORM1 };
  const srvReenvio = (m, u, h, c) => { if (u === '/api/solicitante/solicitudes/42/reenvio') { if (m === 'GET') return { status: 200, body: prep }; if (m === 'PUT') return { status: 200, body: { idSolicitud: 42, estatus: 'PENDIENTE' } }; } return null; };
  t = await crear({ query: '?modo=reenvio&solicitud=42', servidor: srvReenvio }); ({ doc, w, reqs } = t);
  await espera(() => doc.querySelectorAll('.pregunta').length === 4);
  ok(txt(doc.getElementById('titulo-formulario')) === 'Reenviar solicitud', 'titulo de reenvio');
  ok(doc.getElementById('p1').value === 'CLAVE-VIEJA' && doc.getElementById('p3-o2').checked, 'respuestas precargadas');
  ok(visible(doc.getElementById('p3-esp')) && doc.getElementById('p3-txt').value === 'Tutor legal', 'MIXTA precargada con su especificacion visible');
  ok(txt(doc.getElementById('banner-reenvio')).includes('RECHAZADO') && txt(doc.getElementById('banner-reenvio')).includes('Falta la clave correcta'), 'banner con estado y comentario');
  ok(doc.getElementById('tipo-1').checked, 'tipo actual seleccionado');
  doc.getElementById('p1').value = 'CLAVE-NUEVA';
  doc.getElementById('btn-siguiente').click();
  ok(txt(doc.getElementById('aviso-confirmacion')).includes('misma solicitud'), 'aviso de reenvio en la confirmacion');
  doc.getElementById('confirmacion-datos').click(); doc.getElementById('btn-enviar').click();
  await espera(() => txt(doc.getElementById('zona-estado')).includes('reenviada'));
  const put = reqs.filter((r) => r.metodo === 'PUT')[0];
  ok(put && put.headers['X-CSRF-TOKEN'] === 'tok-123' && put.cuerpo.idTipoSolicitud === 1 && put.cuerpo.respuestas[0].texto === 'CLAVE-NUEVA', 'PUT con CSRF y respuestas corregidas');
  ok(!('idSolicitud' in put.cuerpo), 'el id de la solicitud va solo en la URL');
  t = await crear({ query: '?modo=reenvio&solicitud=42', servidor: srvReenvio }); ({ doc } = t);
  await espera(() => doc.querySelectorAll('.pregunta').length === 4);
  doc.getElementById('tipo-2').click(); await espera(() => doc.querySelectorAll('.pregunta').length === 2);
  ok(doc.getElementById('p5').value === '', 'otro tipo: respuestas vacias');
  doc.getElementById('tipo-1').click(); await espera(() => doc.querySelectorAll('.pregunta').length === 4);
  ok(doc.getElementById('p1').value === 'CLAVE-VIEJA', 'volver al tipo original recupera las respuestas guardadas');
  t = await crear({ query: '?modo=reenvio&solicitud=42', servidor: (m, u) => (u.endsWith('/reenvio') ? { status: 409, body: { error: 'Solo se pueden reenviar solicitudes retiradas, rechazadas o canceladas.', errores: [] } } : null) }); ({ doc } = t);
  await espera(() => txt(doc.getElementById('alerta-formulario')).includes('Solo se pueden reenviar'));
  ok(!visible(doc.getElementById('form-solicitud')), 'reenvio no permitido: sin formulario');
  t = await crear({ query: '?modo=reenvio&solicitud=1', servidor: (m, u) => (u.endsWith('/reenvio') ? { status: 404, body: { error: 'Solicitud no encontrada', errores: [] } } : null) }); ({ doc } = t);
  await espera(() => txt(doc.getElementById('alerta-formulario')).includes('No encontramos'));
  ok(!visible(doc.getElementById('form-solicitud')), 'solicitud ajena (404): sin formulario');
  t = await crear({ query: '?modo=reenvio&solicitud=abc' }); ({ doc, reqs } = t);
  await espera(() => doc.querySelectorAll('input[name="tipo-solicitud"]').length === 2);
  ok(!reqs.some((r) => r.url.includes('reenvio')), 'id de solicitud no numerico: se ignora el modo reenvio');

  console.log('\n[6] Aspirante, seguridad y roles');
  t = await crear({ sesion: { authenticated: true, role: 'APPLICANT', identity: 'F2026T001', periodo: 2, anio: 2026, csrfToken: 'tok-123' }, perfil: { tipo: 'ASPIRANTE', identificador: 'F2026T001', nombreCompleto: null, periodo: 2, anio: 2026 } }); ({ doc } = t);
  await espera(() => txt(doc.getElementById('info-extra')) === 'Periodo 2 / 2026');
  ok(txt(doc.getElementById('info-nombre')) === 'Aspirante de nuevo ingreso' && txt(doc.getElementById('info-control')) === 'Ficha F2026T001', 'tarjeta de aspirante');
  ok(doc.getElementById('nav-panel').getAttribute('href') === 'panel-aspirante.html', 'enlace al panel del aspirante');
  await espera(() => doc.querySelectorAll('input[name="tipo-solicitud"]').length === 2);
  ok(true, 'el aspirante ve el formulario');
  const MAL = FORM1.preguntas[0].texto;
  FORM1.preguntas[0].texto = '<img src=x onerror="window.pwned=1">Clave <b>negrita</b>';
  FORM1.instrucciones = '<script>window.pwned2=1</script>Instrucciones';
  t = await crear(); ({ doc, w } = t);
  await espera(() => doc.querySelectorAll('input[name="tipo-solicitud"]').length === 2);
  doc.getElementById('tipo-1').click(); await espera(() => doc.querySelectorAll('.pregunta').length === 4);
  ok(doc.querySelector('img[src="x"]') === null && doc.querySelector('#contenedor-preguntas b') === null && w.pwned === undefined, 'XSS: el texto de las preguntas no se interpreta como HTML');
  ok(txt(doc.getElementById('instrucciones')).includes('<script>') && w.pwned2 === undefined, 'XSS: las instrucciones se muestran como texto');
  FORM1.preguntas[0].texto = MAL; FORM1.instrucciones = 'Responda todas las preguntas.\nRequisitos:';
  ok(t.w.localStorage.length === 0 && t.w.sessionStorage.length === 0, 'no se guardan datos en el navegador');

  console.log('\n[7] Cuentas que no corresponden');
  t = await crear({ sesion: { authenticated: false, csrfToken: 'x' } }); ({ doc } = t);
  await new Promise((r) => setTimeout(r, 150));
  ok(!t.reqs.some((r) => r.url.includes('/api/solicitante/')), 'sin sesion: no consulta la API del formulario');
  ok(t.errores.some((e) => /navigation|Not implemented/i.test(e)), 'sin sesion: intenta redirigir al login');
  t = await crear({ sesion: { authenticated: true, role: 'ADMIN', identity: 'SA01', csrfToken: 'x' } });
  await new Promise((r) => setTimeout(r, 150));
  ok(!t.reqs.some((r) => r.url.includes('/api/solicitante/')) && t.errores.some((e) => /navigation|Not implemented/i.test(e)), 'ADMIN: no usa la API y se redirige a su panel');
  t = await crear({ servidor: (m, u) => (u.includes('/convocatorias') ? { status: 0 } : null) });
  console.log('\n==== ' + (total - fallos) + ' de ' + total + ' verificaciones correctas ====');
  process.exit(fallos ? 1 : 0);
})().catch((e) => { console.error('ERROR DE LA PRUEBA:', e); process.exit(2); });
