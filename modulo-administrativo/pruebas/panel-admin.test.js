/**
 * Pruebas del modulo administrativo (Equipos 1, 2, 4 y 6), sin BD ni navegador:
 * ejecutan panel-admin.html, convocatorias-admin.html,
 * convocatorias-crear-editar.html y historial-convocatorias.html en jsdom.
 * Uso: node panel-admin.test.js
 */
'use strict';

let JSDOM;
let VirtualConsole;
try {
  ({ JSDOM, VirtualConsole } = require('jsdom'));
} catch (e) {
  ({ JSDOM, VirtualConsole } = require(pathFallback()));
}
function pathFallback() {
  return require('path').resolve(__dirname, '..', '..', 'modulo-solicitante', 'pruebas', 'node_modules', 'jsdom');
}

const fs = require('fs');
const path = require('path');
const { pathToFileURL } = require('url');

const RAIZ = path.resolve(__dirname, '..') + path.sep;
const COMPARTIDO = path.resolve(RAIZ, '..', 'modulo-compartido') + path.sep;

function espera(cond, ms) {
  return new Promise((ok, mal) => {
    const t0 = Date.now();
    (function r() {
      let v;
      try { v = cond(); } catch (e) { v = false; }
      if (v) return ok(v);
      if (Date.now() - t0 > (ms || 3000)) return mal(new Error('timeout esperando condicion: ' + cond));
      setTimeout(r, 10);
    })();
  });
}

async function crear(archivoHtml, op) {
  op = op || {};
  const reqs = [];
  const sesion = op.sesion || { authenticated: true, role: 'ADMIN', identity: 'SA01', csrfToken: 'tok-adm-1' };
  const html = fs.readFileSync(path.join(RAIZ, archivoHtml), 'utf8')
    .replace(/<link[^>]+(googleapis|cdnjs|jsdelivr|itt-security\.css|itt-base\.css|administrativo\.css|convocatorias\.css|historial-convocatorias\.css)[^>]*>/g, '')
    .replace(/src="\/compartido\/([^"]+)"/g, (m, r) => 'src="' + pathToFileURL(COMPARTIDO + r.split('/').join(path.sep)).href + '"')
    .replace(/src="js\/([a-z0-9\-]+)\.js(\?[^"]*)?"/g, (m, n) => 'src="' + pathToFileURL(RAIZ + 'js' + path.sep + n + '.js').href + '"');

  const servidor = (metodo, url, headers, cuerpo) => {
    reqs.push({ metodo, url, headers, cuerpo });
    if (op.servidor) {
      const r = op.servidor(metodo, url, headers, cuerpo);
      if (r) return r;
    }
    if (url === '/api/auth/me') {
      return sesion.authenticated
        ? { status: 200, body: sesion }
        : { status: 401, body: Object.assign({ error: 'NOT_AUTHENTICATED', redirect: '/administrativo/login.html' }, sesion) };
    }
    return { status: 404, body: { error: 'No encontrado' } };
  };

  const consola = new VirtualConsole();
  const errores = [];
  consola.on('jsdomError', (e) => {
    const m = String(e.message || e);
    if (!/Could not load (link|img)/.test(m)) errores.push(m);
  });
  consola.on('error', (e) => errores.push(String(e)));

  const dom = new JSDOM(html, {
    url: 'http://localhost/administrativo/' + archivoHtml + (op.query || ''),
    runScripts: 'dangerously',
    resources: 'usable',
    pretendToBeVisual: true,
    virtualConsole: consola,
    beforeParse(w) {
      w.Element.prototype.scrollIntoView = () => {};
      if (w.HTMLDialogElement) {
        w.HTMLDialogElement.prototype.showModal = function () { this.open = true; };
        w.HTMLDialogElement.prototype.close = function () {
          this.open = false;
          this.dispatchEvent(new w.Event('close'));
        };
      }
      if (w.HTMLElement) {
        w.HTMLElement.prototype.reportValidity = function () { return true; };
      }
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
        const r = servidor((o.method || 'GET').toUpperCase(), url, headers, cuerpo);
        const resp = {
          ok: r.status >= 200 && r.status < 300,
          status: r.status,
          statusText: '',
          json: async () => r.body,
        };
        resp.clone = () => resp;
        return resp;
      };
    },
  });

  const doc = dom.window.document;
  await espera(() => doc.readyState === 'complete');
  return { dom, w: dom.window, doc, reqs, errores };
}

let total = 0;
let fallos = 0;
function ok(cond, nombre) {
  total++;
  if (cond) console.log('  OK    ' + nombre);
  else { fallos++; console.log('  FALLA ' + nombre); }
}
const txt = (n) => (n ? n.textContent.replace(/\s+/g, ' ').trim() : null);

(async () => {
  console.log('\n[1] Panel administrativo: bandeja, filtros, orden, respuestas y decisiones');
  const convs = [
    {
      idConvocatoria: 900002, periodo: 2, anio: 2026, estado: 'ACTIVA', activa: true,
      fechaInicio: '2026-11-01', fechaFin: '2026-11-30',
      fechaLimiteAutorizacion: '2026-12-10', fechaLimiteAplicacion: '2026-12-20',
      puedeEditarFechas: true, puedeEditarMontos: true,
      montos: [{ idTipoSolicitud: 1, montoDeduccion: 500 }, { idTipoSolicitud: 2, montoDeduccion: 650 }],
    },
    {
      idConvocatoria: 900001, periodo: 1, anio: 2026, estado: 'CERRADA', activa: false,
      fechaInicio: '2026-07-01', fechaFin: '2026-07-15',
      fechaLimiteAutorizacion: '2026-07-30', fechaLimiteAplicacion: '2026-08-10',
      puedeEditarFechas: false, puedeEditarMontos: false,
      montos: [{ idTipoSolicitud: 1, montoDeduccion: 400 }, { idTipoSolicitud: 2, montoDeduccion: 550 }],
    },
  ];
  const solicitudesBase = [
    {
      idSolicitud: 101, solicitante: '21281181', nombreSolicitante: '<img src=x onerror="window.xssTab=1">Sofia Lopez',
      tipoSolicitante: 'ESTUDIANTE', tipoSolicitud: 'Hijo de trabajador', idConvocatoria: 900002,
      fechaActualizacion: '2026-11-15T10:00:00', estatus: 'PENDIENTE',
      respuestas: [
        { idPregunta: 4, pregunta: 'Observaciones', obligatoria: 'N', tipoPregunta: 'ABIERTA', orden: 4, respuesta: null },
        { idPregunta: 1, pregunta: 'Clave trabajador', obligatoria: 'S', tipoPregunta: 'ABIERTA', orden: 1, respuesta: 'CLAVE-1' },
        { idPregunta: 3, pregunta: 'Parentesco', obligatoria: 'S', tipoPregunta: 'MIXTA', orden: 3, opcion: 'Otro', respuesta: 'Tutor' },
      ],
      historial: [{ estatus: 'PENDIENTE', estatusNombre: 'Pendiente', fecha: '2026-11-15T10:00:00', administrador: null }],
      deduccion: null,
    },
    {
      idSolicitud: 102, solicitante: 'F2026A002', nombreSolicitante: 'Emilio Rojas',
      tipoSolicitante: 'ASPIRANTE', tipoSolicitud: 'Hijo de homologado', idConvocatoria: 900002,
      fechaActualizacion: '2026-11-16T10:00:00', estatus: 'AUTORIZADO',
      respuestas: [],
      historial: [{ estatus: 'AUTORIZADO', estatusNombre: 'Autorizado', fecha: '2026-11-16T10:00:00', administrador: 'SA01', comentario: 'OK' }],
      deduccion: { idDeduccion: 55, montoPago: 1900, montoDeduccion: 650, montoFinal: 1250, estatus: 'AUTORIZADO' },
    },
    {
      idSolicitud: 103, solicitante: '21281185', nombreSolicitante: 'Fernanda Castillo',
      tipoSolicitante: 'ESTUDIANTE', tipoSolicitud: 'Hijo de trabajador', idConvocatoria: 900002,
      fechaActualizacion: '2026-11-17T10:00:00', estatus: 'CANCELADO',
      respuestas: [],
      historial: [{ estatus: 'CANCELADO', estatusNombre: 'Cancelado', fecha: '2026-11-17T10:00:00', administrador: 'SA02' }],
      deduccion: { idDeduccion: 56, montoPago: 1600, montoDeduccion: 500, montoFinal: 1100, estatus: 'CANCELADO' },
    },
  ];

  const srvAdmin = (m, u, h, c) => {
    if (u === '/api/admin/convocatorias') return { status: 200, body: { convocatorias: convs } };
    if (u.startsWith('/api/solicitudes/estadisticas')) {
      return { status: 200, body: { total: 3, pendiente: 1, autorizado: 1, rechazado: 0, cancelado: 1, aplicado: 0, retirado: 0 } };
    }
    if (u.startsWith('/api/solicitudes?')) return { status: 200, body: solicitudesBase };
    if (u === '/api/solicitudes') return { status: 200, body: solicitudesBase };
    const det = u.match(/^\/api\/solicitudes\/(\d+)$/);
    if (det && m === 'GET') {
      const s = solicitudesBase.find((x) => x.idSolicitud === Number(det[1]));
      return s ? { status: 200, body: s } : { status: 404, body: { error: 'No existe' } };
    }
    if (m === 'PUT' && /^\/api\/solicitudes\/\d+\/(aprobar|rechazar)$/.test(u)) {
      return { status: 200, body: { ok: true } };
    }
    if (m === 'PUT' && /^\/api\/deducciones\/\d+\/(cancelar|aplicar)$/.test(u)) {
      return { status: 200, body: { ok: true } };
    }
    if (m === 'POST' && u === '/api/notificaciones') {
      if (c.idSolicitud === 101) return { status: 422, body: { mensaje: 'La solicitud no tiene un correo disponible.' } };
      return { status: 204, body: null };
    }
    return null;
  };

  let t = await crear('panel-admin.html', { servidor: srvAdmin });
  let { doc, w, reqs } = t;
  await espera(() => doc.querySelectorAll('#tabla-solicitudes tbody tr').length === 3);
  ok(txt(doc.getElementById('stat-total')) === '3' && txt(doc.getElementById('stat-pendiente')) === '1', 'estadisticas renderizadas');
  ok(doc.querySelector('#tabla-solicitudes img[src="x"]') === null && w.xssTab === undefined, 'XSS en tabla escapado');

  doc.getElementById('filtro-retirado').click();
  await espera(() => reqs.some((r) => r.url.includes('estatus=6')));
  ok(true, 'chip Retiradas consulta estatus=6');

  doc.querySelector('#tabla-solicitudes th[data-col="idSolicitud"]').click();
  doc.querySelector('#tabla-solicitudes th[data-col="idSolicitud"]').click();
  ok(txt(doc.querySelector('#tabla-solicitudes tbody tr td')) === '103', 'ordenamiento descendente por folio funciona');

  doc.querySelector('button[data-accion="respuestas"][data-id="101"]').click();
  await espera(() => doc.getElementById('modal-respuestas').classList.contains('activo'));
  const respTxt = txt(doc.getElementById('lista-respuestas'));
  ok(respTxt.indexOf('Clave trabajador') < respTxt.indexOf('Parentesco')
    && respTxt.includes('Otro — Tutor')
    && respTxt.includes('Sin respuesta'), 'modal respuestas ordena y muestra MIXTA y opcional vacia');
  doc.querySelector('#modal-respuestas [data-modal-cerrar="modal-respuestas"]').click();

  doc.querySelector('button[data-accion="detalle"][data-id="101"]').click();
  await espera(() => doc.getElementById('modal-detalle').classList.contains('activo'));
  doc.getElementById('btn-rechazar').click();
  ok(!doc.getElementById('editor-decision-correo').hidden
    && doc.getElementById('agregar-comentario').checked
    && doc.getElementById('agregar-comentario').disabled, 'rechazar exige comentario y muestra editor');
  doc.getElementById('confirmar-decision-correo').click();
  ok(!reqs.some((r) => r.url.endsWith('/rechazar')), 'no envia rechazo sin comentario');
  doc.getElementById('comentario-correccion').value = 'Falta documento';
  doc.getElementById('comentario-correccion').dispatchEvent(new w.Event('input', { bubbles: true }));
  ok(txt(doc.getElementById('correo-comentario')) === 'Falta documento', 'vista previa del correo refleja el comentario');
  doc.getElementById('confirmar-decision-correo').click();
  await espera(() => reqs.some((r) => r.url === '/api/solicitudes/101/rechazar'));
  const putRech = reqs.find((r) => r.url === '/api/solicitudes/101/rechazar');
  ok(putRech.headers['X-CSRF-TOKEN'] === 'tok-adm-1' && putRech.cuerpo.comentario === 'Falta documento', 'PUT rechazar envia CSRF y comentario');

  doc.querySelector('button[data-accion="detalle"][data-id="102"]').click();
  await espera(() => doc.getElementById('modal-detalle').classList.contains('activo'));
  doc.getElementById('btn-aplicar-deduccion').click();
  doc.getElementById('agregar-comentario').click();
  doc.getElementById('comentario-correccion').value = 'Aplicada hoy';
  doc.getElementById('confirmar-decision-correo').click();
  await espera(() => reqs.some((r) => r.url === '/api/deducciones/55/aplicar'));
  const putAp = reqs.find((r) => r.url === '/api/deducciones/55/aplicar');
  ok(putAp.cuerpo.comentario === 'Aplicada hoy' && putAp.headers['X-CSRF-TOKEN'] === 'tok-adm-1', 'PUT aplicar envia comentario opcional y CSRF');

  doc.querySelector('button[data-accion="detalle"][data-id="101"]').click();
  await espera(() => doc.getElementById('modal-detalle').classList.contains('activo'));
  doc.getElementById('mensaje-notificacion').value = 'Aviso de prueba';
  doc.getElementById('form-notificacion').dispatchEvent(new w.Event('submit', { bubbles: true, cancelable: true }));
  await espera(() => txt(doc.querySelector('#form-notificacion [role="status"]')).includes('no tiene un correo disponible'));
  ok(true, 'aviso manual muestra error 422 cuando no hay correo');
  ok(t.errores.length === 0, 'panel admin sin errores JS');

  console.log('\n[2] Convocatorias admin (listado, filtros y edicion en <dialog>)');
  const srvConv = (m, u, h, c) => {
    if (u === '/api/admin/convocatorias') return { status: 200, body: { convocatorias: convs } };
    if (m === 'GET' && u === '/api/admin/convocatorias/900002') return { status: 200, body: convs[0] };
    if (m === 'PUT' && u === '/api/admin/convocatorias/900002') {
      return { status: 200, body: { mensaje: 'Convocatoria actualizada correctamente.', huboCambios: true } };
    }
    return null;
  };
  t = await crear('convocatorias-admin.html', { servidor: srvConv });
  ({ doc, w, reqs } = t);
  await espera(() => doc.querySelectorAll('#cuerpoConvocatorias tr').length === 2);
  doc.getElementById('filtroEstado').value = 'ACTIVA';
  doc.getElementById('filtroEstado').dispatchEvent(new w.Event('change'));
  ok(doc.querySelectorAll('#cuerpoConvocatorias tr').length === 1, 'filtro por estado ACTIVA filtra tabla');
  doc.querySelector('button[data-accion="editar"][data-id="900002"]').click();
  await espera(() => doc.getElementById('modalEditarConvocatoria').open && !doc.getElementById('guardarEditar').disabled);
  doc.getElementById('editarTrabajador').value = '550';
  doc.getElementById('formEditarConvocatoria').dispatchEvent(new w.Event('submit', { bubbles: true, cancelable: true }));
  await espera(() => reqs.some((r) => r.metodo === 'PUT' && r.url === '/api/admin/convocatorias/900002'));
  const putConv = reqs.find((r) => r.metodo === 'PUT' && r.url === '/api/admin/convocatorias/900002');
  ok(putConv.cuerpo.montoTrabajador === 550 && putConv.headers['X-CSRF-TOKEN'] === 'tok-adm-1', 'edicion envia PUT con CSRF y monto nuevo');

  console.log('\n[3] Crear convocatoria: validaciones y alta');
  t = await crear('convocatorias-crear-editar.html', {
    servidor: (m, u, h, c) => (m === 'POST' && u === '/api/admin/convocatorias'
      ? { status: 201, body: { idConvocatoria: 900010, mensaje: 'Convocatoria creada correctamente.' } }
      : null),
  });
  ({ doc, w, reqs } = t);
  await espera(() => !doc.getElementById('btnGuardarConvocatoria').disabled);
  doc.getElementById('periodo').value = '1';
  doc.getElementById('anio').value = '2027';
  doc.getElementById('fechaInicio').value = '2027-08-10';
  doc.getElementById('fechaFin').value = '2027-08-05';
  doc.getElementById('fechaAutorizacion').value = '2027-08-20';
  doc.getElementById('fechaAplicacion').value = '2027-08-30';
  doc.getElementById('montoInterno').value = '500';
  doc.getElementById('montoHomologado').value = '650';
  doc.getElementById('formConvocatoria').dispatchEvent(new w.Event('submit', { bubbles: true, cancelable: true }));
  ok(txt(doc.getElementById('alertaValidacion')).includes('Orden de fechas inválido'), 'rechaza orden de fechas invalido');

  doc.getElementById('fechaFin').value = '2027-08-15';
  doc.getElementById('montoInterno').value = '500.123';
  doc.getElementById('formConvocatoria').dispatchEvent(new w.Event('submit', { bubbles: true, cancelable: true }));
  ok(txt(doc.getElementById('alertaValidacion')).includes('máximo dos decimales'), 'rechaza montos con mas de 2 decimales');

  doc.getElementById('montoInterno').value = '500.50';
  doc.getElementById('formConvocatoria').dispatchEvent(new w.Event('submit', { bubbles: true, cancelable: true }));
  await espera(() => txt(doc.getElementById('alertaValidacion')).includes('900010'));
  const postConv = reqs.find((r) => r.metodo === 'POST' && r.url === '/api/admin/convocatorias');
  ok(postConv && postConv.cuerpo.montoTrabajador === 500.5 && postConv.headers['X-CSRF-TOKEN'] === 'tok-adm-1', 'alta de convocatoria envia POST con CSRF');

  console.log('\n[4] Historial de convocatorias y aislamiento de roles');
  const histData = [
    {
      idHistorialConvocatoria: 7, idConvocatoria: 900002, idAdministrador: 'SA01', administradorNombre: 'Juan Ramirez',
      fechaRegistro: '2026-11-18T11:00:00',
      inicioAnterior: '2026-11-01', finAnterior: '2026-11-25', autorizacionAnterior: '2026-12-10', aplicacionAnterior: '2026-12-20',
      inicioNuevo: '2026-11-01', finNuevo: '2026-11-30', autorizacionNueva: '2026-12-10', aplicacionNueva: '2026-12-20',
    },
  ];
  t = await crear('historial-convocatorias.html', {
    servidor: (m, u) => {
      if (u === '/api/admin/convocatorias') return { status: 200, body: { convocatorias: convs } };
      if (u === '/api/admin/convocatorias/900002/historial') return { status: 200, body: histData };
      return null;
    },
  });
  ({ doc } = t);
  await espera(() => doc.querySelectorAll('#tabla-historial tbody button[data-historial-id]').length === 1);
  doc.querySelector('#tabla-historial tbody button[data-historial-id="7"]').click();
  ok(doc.getElementById('modal-cambio').classList.contains('activo')
    && txt(doc.getElementById('cambio-comparativa')).includes('+5 días'), 'historial abre comparativa con diferencia de dias');

  t = await crear('panel-admin.html', {
    sesion: { authenticated: true, role: 'STUDENT', identity: '21281181', home: '/solicitante/panel-estatus.html', csrfToken: 'x' },
  });
  await new Promise((r) => setTimeout(r, 120));
  ok(!t.reqs.some((r) => r.url === '/api/solicitudes')
    && t.errores.some((e) => /navigation|Not implemented/i.test(e)), 'STUDENT no accede al panel administrativo');

  console.log('\n==== ' + (total - fallos) + ' de ' + total + ' verificaciones correctas ====');
  process.exit(fallos ? 1 : 0);
})().catch((e) => {
  console.error('ERROR DE LA PRUEBA:', e);
  process.exit(2);
});
