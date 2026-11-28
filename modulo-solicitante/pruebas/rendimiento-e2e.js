/**
 * Pruebas E2E en vivo + Seguridad + Benchmark de rendimiento (RNF-42 / RNF-43).
 * Requiere Docker Compose levantado (por omision http://localhost:8081).
 * Uso: node rendimiento-e2e.js
 */
'use strict';

const fs = require('fs');
const path = require('path');

function cargarEnv() {
  const ruta = path.resolve(__dirname, '..', '..', '.env');
  const vars = {};
  if (!fs.existsSync(ruta)) return vars;
  for (const linea of fs.readFileSync(ruta, 'utf8').split(/\r?\n/)) {
    const m = /^\s*([A-Z0-9_]+)\s*=\s*([^#\r\n]*)/.exec(linea);
    if (m) vars[m[1]] = m[2].trim();
  }
  return vars;
}

const ENV = cargarEnv();
const BASE = (process.env.BASE_URL || ('http://localhost:' + (ENV.FRONTEND_PORT || '8081'))).replace(/\/$/, '');
const ADMIN_USER = process.env.ADMIN_USER || ENV.ORACLE_ADMIN_USER || 'SA01';
const ADMIN_PASS = process.env.ADMIN_PASS || ENV.ORACLE_ADMIN_PASSWORD || '';
const ALUMNO_NC = process.env.ALUMNO_NC || (ENV.ORACLE_ALUMNO_USER || 'ALU_21281181').replace(/^ALU_/, '');
const ALUMNO_PASS = process.env.ALUMNO_PASS || ENV.ORACLE_ALUMNO_PASSWORD || '';
const ASP_FICHA = process.env.ASP_FICHA || 'F2026A001';
const ASP_CURP = process.env.ASP_CURP || 'MOPA080512MDFRNN01';

let total = 0;
let fallos = 0;
function ok(cond, msg) {
  total++;
  if (cond) console.log('  OK    ' + msg);
  else { fallos++; console.log('  FALLA ' + msg); }
}

function resolverCaptchaAccesible(pregunta) {
  const limpio = String(pregunta || '').replace(/[¿?]/g, '').trim();
  const numMap = {
    cero: 0, uno: 1, dos: 2, tres: 3, cuatro: 4, cinco: 5, seis: 6, siete: 7, ocho: 8, nueve: 9,
    diez: 10, once: 11, doce: 12, trece: 13, catorce: 14, quince: 15, dieciseis: 16, diecisiete: 17,
    dieciocho: 18, diecinueve: 19, veinte: 20,
  };
  let expr = limpio.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  for (const [k, v] of Object.entries(numMap)) {
    expr = expr.replace(new RegExp('\\b' + k + '\\b', 'g'), String(v));
  }
  const m = /(\d+)\s*(\+|mas|\-|menos|\*|x|por)\s*(\d+)/.exec(expr);
  if (!m) throw new Error('No se pudo resolver captcha accesible: ' + pregunta);
  const a = Number(m[1]);
  const b = Number(m[3]);
  const op = m[2];
  if (op === '+' || op === 'mas') return String(a + b);
  if (op === '-' || op === 'menos') return String(a - b);
  return String(a * b);
}

class ClienteHttp {
  constructor() {
    this.cookie = '';
    this.csrf = '';
  }
  extraerCookie(resp) {
    const raw = typeof resp.headers.getSetCookie === 'function'
      ? resp.headers.getSetCookie()
      : (resp.headers.get('set-cookie') ? [resp.headers.get('set-cookie')] : []);
    for (const c of raw) {
      const par = c.split(';')[0];
      if (par) this.cookie = par;
    }
  }
  async req(metodo, ruta, body) {
    const headers = { Accept: 'application/json' };
    if (this.cookie) headers.Cookie = this.cookie;
    if (this.csrf && metodo !== 'GET') headers['X-CSRF-TOKEN'] = this.csrf;
    const opts = { method: metodo, headers };
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(body);
    }
    const t0 = performance.now();
    const resp = await fetch(BASE + ruta, opts);
    const ms = performance.now() - t0;
    this.extraerCookie(resp);
    let json = null;
    try {
      const text = await resp.text();
      json = text ? JSON.parse(text) : null;
    } catch (e) { /* no json */ }
    if (json && json.csrfToken) this.csrf = json.csrfToken;
    return { status: resp.status, headers: resp.headers, body: json, ms };
  }
  async login(perfil, payloadFn) {
    const me = await this.req('GET', '/api/auth/me');
    if (!this.csrf && me.body && me.body.csrfToken) this.csrf = me.body.csrfToken;
    const cap = await this.req('GET', '/api/auth/captcha?tipo=accesible');
    const respCap = cap.body && cap.body.enabled === false ? '' : resolverCaptchaAccesible(cap.body.pregunta);
    const body = payloadFn(respCap);
    const ruta = perfil === 'ASPIRANTE' ? '/api/auth/login/aspirante' : '/api/auth/login';
    return this.req('POST', ruta, body);
  }
}

function percentil(arr, p) {
  const ord = [...arr].sort((a, b) => a - b);
  const idx = Math.min(ord.length - 1, Math.max(0, Math.ceil((p / 100) * ord.length) - 1));
  return ord[idx];
}

async function medir(cliente, nombre, ruta, totalPeticiones = 25, concurrencia = 5) {
  const tiempos = [];
  let okCount = 0;
  const rondas = Math.ceil(totalPeticiones / concurrencia);
  for (let i = 0; i < rondas; i++) {
    const batch = [];
    for (let j = 0; j < concurrencia; j++) {
      batch.push(cliente.req('GET', ruta));
    }
    const resultados = await Promise.all(batch);
    for (const r of resultados) {
      tiempos.push(r.ms);
      if (r.status === 200) okCount++;
    }
  }
  const sum = tiempos.reduce((a, b) => a + b, 0);
  const min = Math.min(...tiempos);
  const max = Math.max(...tiempos);
  const avg = sum / tiempos.length;
  const p50 = percentil(tiempos, 50);
  const p95 = percentil(tiempos, 95);
  console.log(
    `  ${nombre.padEnd(42)} | ${okCount}/${tiempos.length} OK | min=${min.toFixed(1)}ms avg=${avg.toFixed(1)}ms p50=${p50.toFixed(1)}ms p95=${p95.toFixed(1)}ms max=${max.toFixed(1)}ms`
  );
  ok(okCount === tiempos.length, `Rendimiento ${nombre}: ${okCount}/${tiempos.length} HTTP 200`);
}

(async () => {
  console.log('\n[1] Cabeceras de seguridad y CSP estricta en Nginx (' + BASE + ')');
  const vistas = [
    '/solicitante/login.html',
    '/solicitante/panel-estatus.html',
    '/administrativo/panel-admin.html',
    '/administrativo/convocatorias-admin.html',
  ];
  for (const vista of vistas) {
    const resp = await fetch(BASE + vista);
    const csp = resp.headers.get('content-security-policy') || '';
    ok(
      resp.status === 200
      && resp.headers.get('x-content-type-options') === 'nosniff'
      && resp.headers.get('x-frame-options') === 'DENY'
      && csp.includes("script-src 'self'")
      && !csp.includes('unsafe-inline'),
      'Cabeceras y CSP estricta sin unsafe-inline en ' + vista
    );
  }

  console.log('\n[2] Rutas protegidas sin sesion -> 401');
  const anon = new ClienteHttp();
  ok((await anon.req('GET', '/api/solicitudes')).status === 401, 'GET /api/solicitudes sin sesion -> 401');
  ok((await anon.req('GET', '/api/admin/convocatorias')).status === 401, 'GET /api/admin/convocatorias sin sesion -> 401');
  ok((await anon.req('GET', '/api/solicitante/perfil')).status === 401, 'GET /api/solicitante/perfil sin sesion -> 401');

  console.log('\n[3] Login real, aislamiento de roles y rutas eliminadas (404)');
  const admin = new ClienteHttp();
  const rAdm = await admin.login('ADMINISTRADOR', (c) => ({
    username: ADMIN_USER, password: ADMIN_PASS, perfil: 'ADMINISTRADOR', captcha: c,
  }));
  ok(rAdm.status === 200 && rAdm.body.role === 'ADMIN', 'Login real de administrador (' + ADMIN_USER + ')');
  ok((await admin.req('GET', '/api/solicitante/perfil')).status === 403, 'ADMIN bloqueado en /api/solicitante/perfil (403)');
  ok((await admin.req('GET', '/api/convocatorias')).status === 404, 'Ruta unificada eliminada /api/convocatorias -> 404');

  const est = new ClienteHttp();
  const rEst = await est.login('ESTUDIANTE', (c) => ({
    username: 'ALU_' + ALUMNO_NC, password: ALUMNO_PASS, perfil: 'ESTUDIANTE', captcha: c,
  }));
  ok(rEst.status === 200 && rEst.body.role === 'STUDENT', 'Login real de estudiante (ALU_' + ALUMNO_NC + ')');
  ok((await est.req('GET', '/api/solicitante/convocatorias-abiertas')).status === 404, 'Ruta unificada eliminada /api/solicitante/convocatorias-abiertas -> 404');
  ok((await est.req('GET', '/api/solicitudes')).status === 403, 'STUDENT bloqueado en /api/solicitudes (403)');
  ok((await est.req('GET', '/api/admin/convocatorias')).status === 403, 'STUDENT bloqueado en /api/admin/convocatorias (403)');

  const asp = new ClienteHttp();
  const rAsp = await asp.login('ASPIRANTE', (c) => ({
    ficha: ASP_FICHA, curp: ASP_CURP, captcha: c,
  }));
  ok((rAsp.status === 200 && rAsp.body.role === 'APPLICANT') || rAsp.status === 202, 'Login real de aspirante (' + ASP_FICHA + ') -> HTTP ' + rAsp.status);
  if (rAsp.status === 200) {
    const perfAsp = await asp.req('GET', '/api/solicitante/perfil');
    ok(perfAsp.status === 200 && !('curp' in perfAsp.body) && !('correo' in perfAsp.body), 'Perfil aspirante omite CURP y correo');
  }

  console.log('\n[4] Benchmark de rendimiento y concurrencia (25 reqs, 5 concurrentes)');
  await medir(admin, 'GET /api/solicitudes', '/api/solicitudes');
  await medir(admin, 'GET /api/solicitudes/estadisticas', '/api/solicitudes/estadisticas');
  await medir(admin, 'GET /api/admin/convocatorias', '/api/admin/convocatorias');
  await medir(est, 'GET /api/solicitante/convocatorias', '/api/solicitante/convocatorias');
  await medir(est, 'GET /api/solicitante/solicitudes', '/api/solicitante/solicitudes');
  await medir(est, 'GET /api/solicitante/formulario (900002,1)', '/api/solicitante/formulario?convocatoria=900002&tipo=1');

  await admin.req('POST', '/api/auth/logout', {});
  await est.req('POST', '/api/auth/logout', {});
  if (rAsp.status === 200) await asp.req('POST', '/api/auth/logout', {});

  console.log('\n==== ' + (total - fallos) + ' de ' + total + ' verificaciones correctas ====');
  process.exit(fallos ? 1 : 0);
})().catch((e) => {
  console.error('ERROR E2E:', e);
  process.exit(2);
});
