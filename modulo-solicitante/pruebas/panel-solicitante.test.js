/**
 * Pruebas del login y paneles del solicitante (Equipos 1 y 3), sin BD ni navegador:
 * ejecutan login.html, panel-estatus.html y panel-aspirante.html con
 * /compartido/js/itt-security.js y js/app.js en jsdom simulando la API.
 * Uso: node panel-solicitante.test.js
 */
'use strict';

const { JSDOM, VirtualConsole } = require('jsdom');
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
  const sesion = op.sesion || { authenticated: true, role: 'STUDENT', identity: '21281181', csrfToken: 'tok-sol-1' };
  const html = fs.readFileSync(path.join(RAIZ, archivoHtml), 'utf8')
    .replace(/<link[^>]+(googleapis|cdnjs|itt-security\.css|itt-base\.css|solicitante\.css)[^>]*>/g, '')
    .replace(/src="\/compartido\/([^"]+)"/g, (m, r) => 'src="' + pathToFileURL(COMPARTIDO + r.split('/').join(path.sep)).href + '"')
    .replace(/src="js\/(app)\.js(\?[^"]*)?"/g, (m, n) => 'src="' + pathToFileURL(RAIZ + 'js' + path.sep + n + '.js').href + '"');

  const servidor = (metodo, url, headers, cuerpo) => {
    reqs.push({ metodo, url, headers, cuerpo });
    if (op.servidor) {
      const r = op.servidor(metodo, url, headers, cuerpo);
      if (r) return r;
    }
    if (url === '/api/auth/me') {
      return sesion.authenticated
        ? { status: 200, body: sesion }
        : { status: 401, body: Object.assign({ error: 'NOT_AUTHENTICATED', redirect: '/solicitante/login.html' }, sesion) };
    }
    if (url.startsWith('/api/auth/captcha')) {
      return { status: 200, body: { enabled: true, tipo: 'imagen', longitud: 5, imagen: 'data:image/png;base64,AA==' } };
    }
    if (url === '/api/solicitante/perfil') {
      return {
        status: 200,
        body: op.perfil || {
          tipo: 'ESTUDIANTE',
          identificador: '21281181',
          nombre: 'Sofia',
          primerApellido: 'Lopez',
          segundoApellido: 'Hernandez',
          nombreCompleto: 'Sofia Lopez Hernandez',
          carrera: 'Ingenieria en Sistemas Computacionales',
          semestre: 5,
        },
      };
    }
    if (url === '/api/solicitante/convocatorias') {
      return { status: 200, body: op.convocatorias || [] };
    }
    if (url === '/api/solicitante/solicitudes') {
      return { status: 200, body: op.solicitudes || [] };
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
    url: 'http://localhost/solicitante/' + archivoHtml + (op.query || ''),
    runScripts: 'dangerously',
    resources: 'usable',
    pretendToBeVisual: true,
    virtualConsole: consola,
    beforeParse(w) {
      w.Element.prototype.scrollIntoView = () => {};
      w.scrollTo = () => {};
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
  console.log('\n[1] Login de estudiante y cambio de pestana');
  let t = await crear('login.html', { sesion: { authenticated: false, csrfToken: 'tok-login' } });
  let { doc, w, reqs } = t;
  await espera(() => doc.querySelector('#captcha-login input'));
  doc.getElementById('form-login-estudiante').dispatchEvent(new w.Event('submit', { bubbles: true, cancelable: true }));
  ok(doc.getElementById('numero-control-error').classList.contains('visible')
    && doc.getElementById('password-alumno-error').classList.contains('visible'), 'valida campos obligatorios de alumno');

  doc.getElementById('numero-control').value = '21281181';
  doc.getElementById('password-alumno').value = 'Secreta123';
  doc.querySelector('#captcha-login input').value = 'ABC12';
  let loginServ = (m, u, h, c) => (m === 'POST' && u === '/api/auth/login'
    ? { status: 200, body: { authenticated: true, role: 'STUDENT', identity: '21281181', csrfToken: 'tok-2' } }
    : null);
  t = await crear('login.html', { sesion: { authenticated: false, csrfToken: 'tok-login' }, servidor: loginServ });
  ({ doc, w, reqs } = t);
  await espera(() => doc.querySelector('#captcha-login input'));
  doc.getElementById('numero-control').value = '21281181';
  doc.getElementById('password-alumno').value = 'Secreta123';
  doc.querySelector('#captcha-login input').value = 'ABC12';
  doc.getElementById('form-login-estudiante').dispatchEvent(new w.Event('submit', { bubbles: true, cancelable: true }));
  await espera(() => reqs.some((r) => r.metodo === 'POST' && r.url === '/api/auth/login'));
  const postLogin = reqs.find((r) => r.metodo === 'POST' && r.url === '/api/auth/login');
  ok(postLogin.headers['X-CSRF-TOKEN'] === 'tok-login'
    && postLogin.cuerpo.username === 'ALU_21281181'
    && postLogin.cuerpo.perfil === 'ESTUDIANTE'
    && postLogin.cuerpo.captcha === 'ABC12', 'POST /api/auth/login envia ALU_, perfil, captcha y CSRF');
  await espera(() => doc.getElementById('password-alumno').value === '');
  ok(true, 'limpia la contrasena tras el intento');

  console.log('\n[2] Login de aspirante con OTP activo y reenvio');
  const otpSrv = (m, u, h, c) => {
    if (m === 'POST' && u === '/api/auth/login/aspirante') {
      return { status: 202, body: { otpRequired: true, destino: 'f***@demo.mx', csrfToken: 'tok-otp' } };
    }
    if (m === 'POST' && u === '/api/auth/login/aspirante/otp/reenviar') {
      return h['X-CSRF-TOKEN'] ? { status: 429, body: { error: 'TOO_MANY_ATTEMPTS' } } : { status: 403, body: {} };
    }
    if (m === 'POST' && u === '/api/auth/login/aspirante/otp') {
      return c.codigo === '654321'
        ? { status: 200, body: { authenticated: true, role: 'APPLICANT', identity: 'F2026A001' } }
        : { status: 401, body: { error: 'OTP_INVALID', message: 'Codigo invalido' } };
    }
    return null;
  };
  t = await crear('login.html', { sesion: { authenticated: false, csrfToken: 'tok-login' }, servidor: otpSrv });
  ({ doc, w, reqs } = t);
  await espera(() => doc.querySelector('#captcha-login input'));
  doc.getElementById('tab-aspirante').click();
  ok(doc.getElementById('campos-alumno').hidden && !doc.getElementById('campos-aspirante').hidden, 'tab Nuevo Ingreso muestra campos de aspirante');
  doc.getElementById('curp-aspirante').value = 'MOPA080512MDFRNN01';
  doc.getElementById('folio-aspirante').value = 'F2026A001';
  doc.querySelector('#captcha-login input').value = 'XYZ99';
  doc.getElementById('form-login-estudiante').dispatchEvent(new w.Event('submit', { bubbles: true, cancelable: true }));
  await espera(() => !doc.getElementById('paso-otp').hidden);
  ok(doc.getElementById('paso-credenciales').hidden && txt(doc.getElementById('otp-destino')) === 'f***@demo.mx', '202 otpRequired muestra paso OTP');

  doc.getElementById('btn-otp-reenviar').click();
  await espera(() => txt(doc.getElementById('alerta-login')).includes('Espere un minuto'));
  ok(reqs.some((r) => r.url === '/api/auth/login/aspirante/otp/reenviar'), 'reenviar OTP maneja 429');

  doc.getElementById('codigo-otp').value = '654321';
  doc.getElementById('form-login-estudiante').dispatchEvent(new w.Event('submit', { bubbles: true, cancelable: true }));
  await espera(() => reqs.some((r) => r.url === '/api/auth/login/aspirante/otp'));
  const postOtp = reqs.find((r) => r.url === '/api/auth/login/aspirante/otp');
  ok(postOtp.cuerpo.codigo === '654321' && Boolean(postOtp.headers['X-CSRF-TOKEN']), 'confirma codigo OTP con CSRF');

  console.log('\n[3] Panel de estudiante: perfil, convocatorias, seguimiento y retiro');
  let retirado = false;
  const convAbiertas = [
    {
      idConvocatoria: 900002, periodo: 2, periodoDescripcion: 'Enero-Junio', anio: 2026,
      fechaInicio: '2026-11-01', fechaFin: '2026-11-30',
      idTipoSolicitud: 1, tipoSolicitud: 'Hijo de trabajador', tipoDescripcion: 'Desc 1',
      idFormulario: 1, montoDeduccion: 500, idSolicitudExistente: null,
    },
    {
      idConvocatoria: 900003, periodo: 1, periodoDescripcion: 'Agosto-Diciembre', anio: 2027,
      fechaInicio: '2027-08-01', fechaFin: '2027-08-20',
      idTipoSolicitud: 2, tipoSolicitud: 'Hijo de homologado', tipoDescripcion: 'Desc 2',
      idFormulario: 2, montoDeduccion: 650, idSolicitudExistente: 10,
    },
  ];
  const solicitudesEst = () => [
    {
      idSolicitud: 10, idConvocatoria: 900002, periodo: 2, periodoDescripcion: 'Enero-Junio', anio: 2026,
      idTipoSolicitud: 1, tipoSolicitud: 'Hijo de trabajador', idFormulario: 1,
      estatus: retirado ? 'RETIRADO' : 'PENDIENTE',
      fechaSolicitud: '2026-11-10T09:00:00', fechaActualizacion: '2026-11-10T09:00:00',
      fechaInicioRecepcion: '2026-11-01', fechaFinRecepcion: '2026-11-30',
      recepcionAbierta: true, puedeRetirar: !retirado, puedeReenviar: retirado,
      historial: retirado ? [{ estatus: 'RETIRADO', fecha: '2026-11-11T10:00:00', administrativo: false }] : [],
      deduccion: null,
    },
    {
      idSolicitud: 11, idConvocatoria: 900001, periodo: 1, periodoDescripcion: 'Agosto-Diciembre', anio: 2026,
      idTipoSolicitud: 2, tipoSolicitud: 'Hijo de homologado', idFormulario: 2,
      estatus: 'AUTORIZADO',
      fechaSolicitud: '2026-08-10T09:00:00', fechaActualizacion: '2026-08-12T10:00:00',
      fechaInicioRecepcion: '2026-08-01', fechaFinRecepcion: '2026-08-20',
      recepcionAbierta: false, puedeRetirar: false, puedeReenviar: false,
      historial: [{ estatus: 'AUTORIZADO', fecha: '2026-08-12T10:00:00', administrativo: true, comentario: '<img src=x onerror="window.xssPanel=1">Aprobado' }],
      deduccion: { idDeduccion: 3, estatus: 'AUTORIZADO', montoPago: 1600, montoDeduccion: 650, montoFinal: 950 },
    },
    {
      idSolicitud: 12, idConvocatoria: 900000, periodo: 2, periodoDescripcion: 'Enero-Junio', anio: 2025,
      idTipoSolicitud: 1, tipoSolicitud: 'Hijo de trabajador', idFormulario: 1,
      estatus: 'APLICADO',
      fechaSolicitud: '2025-01-10T09:00:00', fechaActualizacion: '2025-01-20T10:00:00',
      fechaInicioRecepcion: '2025-01-01', fechaFinRecepcion: '2025-01-25',
      recepcionAbierta: false, puedeRetirar: false, puedeReenviar: false,
      historial: [{ estatus: 'APLICADO', fecha: '2025-01-20T10:00:00', administrativo: true }],
      deduccion: { idDeduccion: 2, estatus: 'APLICADO', montoPago: 1450, montoDeduccion: 500, montoFinal: 950 },
    },
  ];

  t = await crear('panel-estatus.html', {
    convocatorias: convAbiertas,
    servidor: (m, u, h) => {
      if (u === '/api/solicitante/solicitudes') return { status: 200, body: solicitudesEst() };
      if (m === 'PUT' && u === '/api/solicitante/solicitudes/10/retirar') {
        retirado = true;
        return { status: 200, body: { mensaje: 'Solicitud retirada correctamente.' } };
      }
      return null;
    },
  });
  ({ doc, w, reqs } = t);
  await espera(() => doc.querySelectorAll('.solicitud-card').length === 3);
  ok(txt(doc.getElementById('nombre-usuario-nav')) === 'Sofia', 'navbar muestra nombre del estudiante');
  ok(txt(doc.getElementById('perfil-datos')).includes('21281181')
    && txt(doc.getElementById('perfil-datos')).includes('Sistemas'), 'modal de perfil muestra datos del estudiante');
  ok(doc.querySelector('#lista-convocatorias a[href*="convocatoria=900002"]') !== null, 'convocatoria libre ofrece Iniciar solicitud');
  ok(txt(doc.getElementById('lista-convocatorias')).includes('Solo se permite una solicitud por convocatoria'), 'convocatoria existente bloquea nuevo inicio');

  const cardAut = doc.getElementById('solicitud-11');
  ok(cardAut.querySelector('[data-retirar]') === null && txt(cardAut).includes('$950.00'), 'AUTORIZADO muestra deduccion y no ofrece retirar');
  ok(cardAut.querySelector('img[src="x"]') === null && w.xssPanel === undefined && txt(cardAut).includes('<img src=x'), 'XSS en comentario administrativo escapado');
  const cardAp = doc.getElementById('solicitud-12');
  ok(cardAp.querySelector('a.btn-editar') === null && txt(cardAp).includes('trámite concluyó'), 'APLICADO no ofrece botones de edicion ni retiro');

  const btnRetirar = doc.querySelector('#solicitud-10 [data-retirar="10"]');
  ok(btnRetirar !== null, 'PENDIENTE ofrece boton Retirar solicitud');
  btnRetirar.click();
  ok(doc.getElementById('modal-retirar').classList.contains('activo') && txt(doc.getElementById('retiro-folio')) === '10', 'abre modal de confirmacion de retiro');
  doc.getElementById('btn-confirmar-retiro').click();
  await espera(() => doc.querySelector('#solicitud-10 a.btn-editar') !== null);
  const putRetiro = reqs.find((r) => r.metodo === 'PUT' && r.url === '/api/solicitante/solicitudes/10/retirar');
  ok(putRetiro && putRetiro.headers['X-CSRF-TOKEN'] === 'tok-sol-1', 'retiro envia PUT con CSRF y recarga');
  ok(doc.querySelector('#solicitud-10 a.btn-editar').getAttribute('href').includes('formulario.html?modo=reenvio&solicitud=10'), 'tras retirar habilita Corregir y reenviar');
  ok(t.errores.length === 0, 'panel estudiante sin errores JS');
  if (t.errores.length) console.log('    detalle errores:', t.errores);

  console.log('\n[4] Panel de aspirante y aislamiento de roles');
  t = await crear('panel-aspirante.html', {
    sesion: { authenticated: true, role: 'APPLICANT', identity: 'F2026B004', periodo: 2, anio: 2026, csrfToken: 'tok-asp-1' },
    perfil: {
      tipo: 'ASPIRANTE',
      identificador: 'F2026B004',
      nombre: 'Diego',
      nombreCompleto: 'Diego Fuentes Lara',
      semestre: 1,
      periodo: 2,
      periodoDescripcion: 'Enero-Junio',
      anio: 2026,
    },
    solicitudes: [{
      idSolicitud: 25, idConvocatoria: 900002, periodo: 2, periodoDescripcion: 'Enero-Junio', anio: 2026,
      idTipoSolicitud: 2, tipoSolicitud: 'Hijo de homologado', idFormulario: 2,
      estatus: 'RECHAZADO',
      fechaSolicitud: '2026-11-05T12:00:00', fechaActualizacion: '2026-11-06T12:00:00',
      fechaInicioRecepcion: '2026-11-01', fechaFinRecepcion: '2026-11-30',
      recepcionAbierta: true, puedeRetirar: false, puedeReenviar: true,
      historial: [{ estatus: 'RECHAZADO', fecha: '2026-11-06T12:00:00', administrativo: true, comentario: 'Falta comprobante' }],
      deduccion: null,
    }],
  });
  ({ doc, reqs } = t);
  await espera(() => doc.querySelectorAll('.solicitud-card').length === 1);
  ok(txt(doc.getElementById('perfil-datos')).includes('F2026B004')
    && txt(doc.getElementById('perfil-datos')).includes('Enero-Junio 2026')
    && !txt(doc.getElementById('perfil-datos')).includes('CURP'), 'perfil aspirante muestra ficha y periodo sin CURP');
  ok(doc.querySelector('#solicitud-25 a.btn-editar').getAttribute('href').includes('modo=reenvio&solicitud=25'), 'RECHAZADO ofrece Corregir y reenviar');

  t = await crear('panel-estatus.html', {
    sesion: { authenticated: false, csrfToken: 'x' },
  });
  await new Promise((r) => setTimeout(r, 120));
  ok(!t.reqs.some((r) => r.url.startsWith('/api/solicitante/'))
    && t.errores.some((e) => /navigation|Not implemented/i.test(e)), 'sin sesion no consulta API y redirige a login');

  t = await crear('panel-estatus.html', {
    sesion: { authenticated: true, role: 'ADMIN', identity: 'SA01', home: '/administrativo/panel-admin.html', csrfToken: 'x' },
  });
  await new Promise((r) => setTimeout(r, 120));
  ok(!t.reqs.some((r) => r.url.startsWith('/api/solicitante/'))
    && t.errores.some((e) => /navigation|Not implemented/i.test(e)), 'ADMIN en panel solicitante no consulta API y redirige');

  console.log('\n==== ' + (total - fallos) + ' de ' + total + ' verificaciones correctas ====');
  process.exit(fallos ? 1 : 0);
})().catch((e) => {
  console.error('ERROR DE LA PRUEBA:', e);
  process.exit(2);
});
