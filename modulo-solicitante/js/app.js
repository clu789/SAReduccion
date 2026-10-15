/**
 * ============================================================
 * app.js - Módulo Solicitante
 * Sistema de Reducción de Monto de Inscripción
 * Instituto Tecnológico de Toluca
 * ============================================================
 */

'use strict';

/* ─────────────────────────────────────────
   CONFIGURACIÓN GLOBAL
───────────────────────────────────────── */
const Config = {
  apiBase: '/api',          // Base URL de la API REST (Spring Boot)
  timeout: 8000,            // ms de timeout por petición
};

/* ─────────────────────────────────────────
   UTILIDADES HTTP (fetch wrapper)
───────────────────────────────────────── */
const Http = {
  csrfToken: null,

  /**
   * Realiza una petición GET.
   * @param {string} endpoint  - Ruta relativa (p.ej. '/solicitudes/25')
   * @returns {Promise<any>}   - JSON parseado
   */
  async get(endpoint) {
    const resp = await fetch(Config.apiBase + endpoint, {
      method: 'GET',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
    });
    const data = await resp.json().catch(() => ({}));
    if (data.csrfToken) this.csrfToken = data.csrfToken;
    if (!resp.ok) {
      const error = new Error('No se pudo comprobar la sesión.');
      error.status = resp.status;
      error.payload = data;
      throw error;
    }
    return data;
  },

  /**
   * Realiza una petición POST con body JSON.
   * @param {string} endpoint
   * @param {object} body
   */
  async post(endpoint, body) {
    const headers = { 'Content-Type': 'application/json' };
    const csrfEndpoints = ['/auth/login', '/auth/login/aspirante', '/auth/logout'];
    if (csrfEndpoints.includes(endpoint)) {
      if (!this.csrfToken) throw new Error('Recargue la página para renovar la sesión.');
      headers['X-CSRF-TOKEN'] = this.csrfToken;
    }
    const resp = await fetch(Config.apiBase + endpoint, {
      method: 'POST',
      headers,
      credentials: 'same-origin',
      body: JSON.stringify(body),
    });
    const data = resp.status === 204 ? null : await resp.json().catch(() => ({}));
    if (data?.csrfToken) this.csrfToken = data.csrfToken;
    if (!resp.ok) {
      const error = new Error('No se pudo completar la solicitud.');
      error.status = resp.status;
      error.payload = data;
      throw error;
    }
    return data;
  },

  async authSession() {
    try {
      return await this.get('/auth/me');
    } catch (error) {
      if (error.status === 401 && error.payload?.csrfToken) return error.payload;
      throw error;
    }
  },

  /**
   * Realiza una petición PUT con body JSON.
   */
  async put(endpoint, body) {
    const resp = await fetch(Config.apiBase + endpoint, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
      body: JSON.stringify(body),
    });
    if (!resp.ok) throw new Error(`HTTP ${resp.status}: ${resp.statusText}`);
    return resp.json();
  },
};

const AuthSession = {
  /** Ruta de la interfaz propia de cada rol. */
  panelFor(role, fallback) {
    switch (role) {
      case 'STUDENT':   return 'panel-estatus.html';
      case 'APPLICANT': return 'panel-aspirante.html';
      case 'ADMIN':     return '/administrativo/panel-admin.html';
      default:          return fallback;
    }
  },

  async requireRole(expectedRole, loginPath) {
    try {
      const session = await Http.authSession();
      if (!session.authenticated) {
        window.location.replace(loginPath);
        return false;
      }
      if (session.role !== expectedRole) {
        window.location.replace(this.panelFor(session.role, loginPath));
        return false;
      }
      return true;
    } catch (error) {
      window.location.replace(loginPath);
      return false;
    }
  },

  /** Si ya hay sesión, envía al usuario a la interfaz de su rol. */
  async redirectByRole(alertId) {
    try {
      const session = await Http.authSession();
      if (session.authenticated) {
        window.location.replace(this.panelFor(session.role, 'login.html'));
      }
    } catch (error) {
      Alerta.mostrar(alertId, 'error', 'El servicio de autenticación no está disponible. Intente más tarde.');
    }
  },

  async logout(loginPath, alertId) {
    try {
      await Http.authSession();
      await Http.post('/auth/logout', {});
      window.location.replace(loginPath);
    } catch (error) {
      Alerta.mostrar(alertId, 'error', 'No se pudo cerrar la sesión. Intente nuevamente.');
    }
  },

  bindLogout(loginPath, alertId) {
    document.querySelectorAll('[data-auth-logout]').forEach((button) => {
      button.addEventListener('click', () => this.logout(loginPath, alertId));
    });
  },
};

function mensajeLogin(error) {
  if (error.status === 429) return 'Demasiados intentos. Espere unos minutos e intente de nuevo.';
  if (error.status === 503) return 'El servicio de autenticación no está disponible. Intente más tarde.';
  if (error.status === 403) return 'La sesión de seguridad expiró. Recargue la página e intente de nuevo.';
  return 'Credenciales o perfil no válidos.';
}

/* ─────────────────────────────────────────
   SPINNER
───────────────────────────────────────── */
const Spinner = {
  show() {
    const el = document.getElementById('spinner-overlay');
    if (el) el.classList.add('activo');
  },
  hide() {
    const el = document.getElementById('spinner-overlay');
    if (el) el.classList.remove('activo');
  },
};

/* ─────────────────────────────────────────
   ALERTAS EN-PÁGINA
───────────────────────────────────────── */
const Alerta = {
  /**
   * Muestra un mensaje de alerta dentro de un contenedor.
   * @param {string} contenedorId - ID del elemento donde se inserta la alerta
   * @param {'info'|'exito'|'advertencia'|'error'} tipo
   * @param {string} mensaje
   * @param {number} [duracion=0] - 0 = permanente; ms para auto-cerrar
   */
  mostrar(contenedorId, tipo, mensaje, duracion = 0) {
    const contenedor = document.getElementById(contenedorId);
    if (!contenedor) return;

    const icono = {
      info: 'fa-info-circle',
      exito: 'fa-check-circle',
      advertencia: 'fa-exclamation-triangle',
      error: 'fa-times-circle',
    }[tipo] || 'fa-info-circle';

    const div = document.createElement('div');
    div.className = `alerta alerta-${tipo}`;
    div.innerHTML = `<i class="fa ${icono}"></i><span>${mensaje}</span>`;

    contenedor.innerHTML = '';
    contenedor.appendChild(div);

    if (duracion > 0) {
      setTimeout(() => { contenedor.innerHTML = ''; }, duracion);
    }
  },
  limpiar(contenedorId) {
    const el = document.getElementById(contenedorId);
    if (el) el.innerHTML = '';
  },
};

/* ─────────────────────────────────────────
   MODAL
───────────────────────────────────────── */
const Modal = {
  abrir(modalId) {
    const el = document.getElementById(modalId);
    if (el) el.classList.add('activo');
  },
  cerrar(modalId) {
    const el = document.getElementById(modalId);
    if (el) el.classList.remove('activo');
  },
  /** Cierra cualquier modal activo al pulsar fuera del modal-box */
  iniciarCierre() {
    document.querySelectorAll('.modal-overlay').forEach(overlay => {
      overlay.addEventListener('click', (e) => {
        if (e.target === overlay) overlay.classList.remove('activo');
      });
    });
  },
};

/* ─────────────────────────────────────────
   VALIDACIÓN DE FORMULARIOS
───────────────────────────────────────── */
const Validacion = {
  /**
   * Valida un campo requerido.
   * @returns {boolean}
   */
  requerido(campo) {
    return campo.value.trim() !== '';
  },

  /** Validación básica de número de control (ej: 20230001) */
  numeroControl(valor) {
    return /^[A-Z0-9]{8,26}$/i.test(valor.trim());
  },

  /** Validación de CURP */
  curp(valor) {
    const regex = /^[A-Z]{4}\d{6}[HM][A-Z]{5}[A-Z0-9]\d$/i;
    return regex.test(valor.trim());
  },

  /**
   * Marca un campo como erróneo y muestra su mensaje de error.
   * Convenio: el mensaje de error tiene id = `${campo.id}-error`
   */
  marcarError(campo, mensaje) {
    campo.classList.add('error');
    campo.classList.remove('valido');
    const msgEl = document.getElementById(`${campo.id}-error`);
    if (msgEl) { msgEl.textContent = mensaje; msgEl.classList.add('visible'); }
  },

  marcarValido(campo) {
    campo.classList.remove('error');
    campo.classList.add('valido');
    const msgEl = document.getElementById(`${campo.id}-error`);
    if (msgEl) msgEl.classList.remove('visible');
  },

  limpiarCampo(campo) {
    campo.classList.remove('error', 'valido');
    const msgEl = document.getElementById(`${campo.id}-error`);
    if (msgEl) { msgEl.textContent = ''; msgEl.classList.remove('visible'); }
  },
};

/* ─────────────────────────────────────────
   TABS DE TIPO DE USUARIO (Login)
───────────────────────────────────────── */
const TabsTipoUsuario = {
  init() {
    const tabs = document.querySelectorAll('.tab-tipo');
    tabs.forEach(tab => {
      tab.addEventListener('click', () => {
        tabs.forEach(t => {
          t.classList.remove('activo');
          t.setAttribute('aria-selected', 'false');
        });
        tab.classList.add('activo');
        tab.setAttribute('aria-selected', 'true');

        // Ocultar/mostrar campos según tipo
        const tipo = tab.dataset.tipo; // 'alumno' | 'aspirante'
        this.cambiarVista(tipo);
      });
    });
  },

  cambiarVista(tipo) {
    const camposAlumno    = document.getElementById('campos-alumno');
    const camposAspirante = document.getElementById('campos-aspirante');
    if (camposAlumno)    camposAlumno.style.display    = tipo === 'alumno'    ? 'block' : 'none';
    if (camposAspirante) camposAspirante.style.display = tipo === 'aspirante' ? 'block' : 'none';

    // Al cambiar de pestaña se descartan datos sensibles y avisos previos.
    const password = document.getElementById('password-alumno');
    const curp     = document.getElementById('curp-aspirante');
    if (password) password.value = '';
    if (curp) curp.value = '';
    Alerta.limpiar('alerta-login');
  },
};

/* ─────────────────────────────────────────
   MÓDULO: LOGIN DE ESTUDIANTE
   Equipo 5
───────────────────────────────────────── */
const LoginEstudiante = {
  init() {
    const form = document.getElementById('form-login-estudiante');
    if (!form) return;

    TabsTipoUsuario.init();
    // Si ya existe una sesión activa, dirigir a la interfaz de su rol.
    AuthSession.redirectByRole('alerta-login');

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const tipoActivo = document.querySelector('.tab-tipo.activo')?.dataset.tipo || 'alumno';
      if (!this.validar(tipoActivo)) return;

      Spinner.show();
      try {
        // Garantiza un token CSRF vigente para el POST.
        await Http.authSession();
        if (tipoActivo === 'aspirante') {
          await this.ingresarAspirante();
        } else {
          await this.ingresarAlumno();
        }
      } catch (err) {
        Alerta.mostrar('alerta-login', 'error', mensajeLogin(err));
      } finally {
        this.limpiarSensibles();
        Spinner.hide();
      }
    });
  },

  /** Login de alumno: ALU_<numero_control> + contrasena institucional. */
  async ingresarAlumno() {
    const numeroControl = document.getElementById('numero-control').value.trim().toUpperCase();
    const password = document.getElementById('password-alumno').value;
    const session = await Http.post('/auth/login', {
      username: `ALU_${numeroControl}`,
      password,
    });
    if (session.role !== 'STUDENT' || session.identity !== numeroControl) {
      await Http.post('/auth/logout', {});
      throw new Error('Perfil no autorizado');
    }
    window.location.href = 'panel-estatus.html';
  },

  /**
   * Login provisional de aspirante: ficha + CURP. La validacion real (ficha,
   * CURP y anio vigente) la realiza el backend con PKG_ASPIRANTE; aqui solo
   * se envian los datos y se comprueba el rol devuelto.
   */
  async ingresarAspirante() {
    const ficha = document.getElementById('folio-aspirante').value.trim().toUpperCase();
    const curp = document.getElementById('curp-aspirante').value.trim().toUpperCase();
    const session = await Http.post('/auth/login/aspirante', { ficha, curp });
    if (session.role !== 'APPLICANT' || session.identity !== ficha) {
      await Http.post('/auth/logout', {});
      throw new Error('Perfil no autorizado');
    }
    window.location.href = 'panel-aspirante.html';
  },

  /** Limpia campos sensibles tras cualquier intento. */
  limpiarSensibles() {
    const password = document.getElementById('password-alumno');
    const curp = document.getElementById('curp-aspirante');
    if (password) password.value = '';
    if (curp) curp.value = '';
  },

  validar(tipoActivo = 'alumno') {
    let valido = true;

    if (tipoActivo === 'alumno') {
      const nc = document.getElementById('numero-control');
      const pw = document.getElementById('password-alumno');

      if (!nc || !Validacion.numeroControl(nc.value)) {
        Validacion.marcarError(nc, 'Ingrese un número de control válido.');
        valido = false;
      } else Validacion.marcarValido(nc);

      if (!pw || !Validacion.requerido(pw)) {
        Validacion.marcarError(pw, 'Ingrese su contraseña.');
        valido = false;
      } else Validacion.marcarValido(pw);

    } else {
      const curp  = document.getElementById('curp-aspirante');
      const folio = document.getElementById('folio-aspirante');

      if (!curp || !Validacion.curp(curp.value)) {
        Validacion.marcarError(curp, 'Ingrese una CURP válida.');
        valido = false;
      } else Validacion.marcarValido(curp);

      if (!folio || !Validacion.requerido(folio)) {
        Validacion.marcarError(folio, 'Ingrese su ficha de registro.');
        valido = false;
      } else Validacion.marcarValido(folio);
    }

    return valido;
  },
};

/* ─────────────────────────────────────────
   MÓDULO: PANEL DE ESTATUS DEL ESTUDIANTE
   Equipo 1
───────────────────────────────────────── */
const PanelEstatus = {
  async init() {
    const timeline = document.getElementById('estatus-timeline');
    if (!timeline) return;
    if (!await AuthSession.requireRole('STUDENT', 'login.html')) return;
    AuthSession.bindLogout('login.html', 'alerta-estatus');

    Spinner.show();
    try {
      // GET /api/solicitudes/mi-solicitud  →  solicitud activa del usuario en sesión
      // const solicitud = await Http.get('/solicitudes/mi-solicitud');
      const solicitud = {
        estatus: 'PENDIENTE',
        historial: [
          { fecha: '2026-10-01', estatusNombre: 'PENDIENTE', estatus: 'PENDIENTE', comentario: 'Solicitud enviada' }
        ]
      };
      this.renderTimeline(solicitud);
      this.renderBadge(solicitud.estatus);
    } catch (err) {
      Alerta.mostrar('alerta-estatus', 'info', 'No se encontró ninguna solicitud activa.');
    } finally {
      Spinner.hide();
    }
  },

  renderBadge(estatus) {
    const badge = document.getElementById('badge-estatus-actual');
    if (!badge) return;
    const mapa = {
      PENDIENTE:  ['badge-pendiente',  'Pendiente de revisión'],
      CORRECCION: ['badge-correccion', 'Corrección requerida'],
      APROBADO:   ['badge-aprobado',   'Aprobado'],
      RECHAZADO:  ['badge-rechazado',  'Rechazado'],
    };
    const [cls, texto] = mapa[estatus] || ['badge-revision', estatus];
    badge.className = `badge-estatus ${cls}`;
    badge.textContent = texto;
  },

  renderTimeline(solicitud) {
    const timeline = document.getElementById('estatus-timeline');
    if (!timeline || !solicitud.historial) return;

    timeline.innerHTML = solicitud.historial.map(item => {
      const estaActivo   = item === solicitud.historial[solicitud.historial.length - 1];
      const esRechazado  = item.estatus === 'RECHAZADO' || item.estatus === 'CORRECCION';
      const esCompletado = item.estatus === 'APROBADO';

      const claseItem = estaActivo
        ? (esRechazado ? 'rechazado' : 'activo')
        : 'completado';

      const comentarioHtml = item.comentario
        ? `<div class="estatus-item-comentario"><i class="fa fa-comment"></i> ${item.comentario}</div>`
        : '';

      return `
        <div class="estatus-item ${claseItem}">
          <div class="estatus-item-fecha">${item.fecha || ''}</div>
          <div class="estatus-item-titulo">${item.estatusNombre || item.estatus}</div>
          ${comentarioHtml}
        </div>`;
    }).join('');
  },
};

/* ─────────────────────────────────────────
   MÓDULO: FORMULARIO DE SOLICITUD
   Equipo 3
───────────────────────────────────────── */
const FormularioSolicitud = {
  pasoActual: 1,
  totalPasos: 3,

  async init() {
    const form = document.getElementById('form-solicitud');
    if (!form) return;
    if (!await AuthSession.requireRole('STUDENT', 'login.html')) return;
    AuthSession.bindLogout('login.html', 'alerta-formulario');

    await this.cargarFormulario();
    this.iniciarNavegacion();
    Modal.iniciarCierre();
  },

  /** Carga las preguntas dinámicas desde la API */
  async cargarFormulario() {
    Spinner.show();
    try {
      // const formulario = await Http.get('/formularios/activo');
      const formulario = {
        preguntas: [
          { idPregunta: 1, tipo: 'TEXT', pregunta: 'Nombre completo del trabajador del TecNM' },
          { idPregunta: 2, tipo: 'TEXT', pregunta: 'Clave del trabajador' },
          { idPregunta: 3, tipo: 'SELECT', pregunta: 'Parentesco con el trabajador', opciones: ['Hijo(a)', 'Cónyuge'] },
          { idPregunta: 4, tipo: 'SELECT', pregunta: 'Tipo de solicitud', opciones: ['Inscripción', 'Reinscripción'] },
          { idPregunta: 5, tipo: 'NUMBER', pregunta: 'Semestre a inscribir' }
        ]
      };
      this.renderPreguntas(formulario.preguntas || []);
    } catch (err) {
      Alerta.mostrar('alerta-formulario', 'error', 'No se pudo cargar el formulario. Intente más tarde.');
    } finally {
      Spinner.hide();
    }
  },

  /**
   * Renderiza preguntas dinámicas dentro de #contenedor-preguntas
   * @param {Array} preguntas
   */
  renderPreguntas(preguntas) {
    const contenedor = document.getElementById('contenedor-preguntas');
    if (!contenedor) return;

    contenedor.innerHTML = preguntas.map(p => {
      let inputHtml = '';
      switch (p.tipo) {
        case 'TEXT':
        case 'NUMBER':
          inputHtml = `<input
            type="${p.tipo.toLowerCase()}"
            id="pregunta-${p.idPregunta}"
            name="pregunta_${p.idPregunta}"
            class="form-control-itt"
            placeholder="${p.pregunta}"
            required>`;
          break;
        case 'SELECT':
          inputHtml = `<select id="pregunta-${p.idPregunta}" name="pregunta_${p.idPregunta}" class="form-control-itt">
            <option value="">-- Seleccione --</option>
            ${(p.opciones || []).map(o => `<option value="${o}">${o}</option>`).join('')}
          </select>`;
          break;
        default:
          inputHtml = `<input type="text" id="pregunta-${p.idPregunta}" name="pregunta_${p.idPregunta}" class="form-control-itt">`;
      }

      return `
        <div class="form-group">
          <label for="pregunta-${p.idPregunta}">${p.pregunta}<span class="requerido">*</span></label>
          ${inputHtml}
          <span class="mensaje-error" id="pregunta-${p.idPregunta}-error"></span>
        </div>`;
    }).join('');
  },

  iniciarNavegacion() {
    const btnSig  = document.getElementById('btn-siguiente');
    const btnAnt  = document.getElementById('btn-anterior');
    const btnEnv  = document.getElementById('btn-enviar');

    if (btnSig) btnSig.addEventListener('click', () => this.siguiente());
    if (btnAnt) btnAnt.addEventListener('click', () => this.anterior());
    if (btnEnv) btnEnv.addEventListener('click', () => this.enviar());
  },

  siguiente() {
    if (this.pasoActual < this.totalPasos) {
      this.irA(this.pasoActual + 1);
    }
  },

  anterior() {
    if (this.pasoActual > 1) {
      this.irA(this.pasoActual - 1);
    }
  },

  irA(paso) {
    document.querySelectorAll('.paso-contenido').forEach(el => el.classList.add('hidden'));
    const pasoDest = document.getElementById(`paso-${paso}`);
    if (pasoDest) pasoDest.classList.remove('hidden');

    this.actualizarStepper(paso);
    this.pasoActual = paso;

    // Mostrar/ocultar botones de navegación
    const btnSig = document.getElementById('btn-siguiente');
    const btnAnt = document.getElementById('btn-anterior');
    const btnEnv = document.getElementById('btn-enviar');

    if (btnAnt) btnAnt.style.display = paso === 1 ? 'none' : 'inline-flex';
    if (btnSig) btnSig.style.display = paso === this.totalPasos ? 'none' : 'inline-flex';
    if (btnEnv) btnEnv.style.display = paso === this.totalPasos ? 'inline-flex' : 'none';
  },

  actualizarStepper(pasoActivo) {
    document.querySelectorAll('.step').forEach((step, idx) => {
      const num = idx + 1;
      step.classList.remove('activo', 'completado');
      if (num < pasoActivo)       step.classList.add('completado');
      else if (num === pasoActivo) step.classList.add('activo');

      const linea = step.nextElementSibling;
      if (linea && linea.classList.contains('step-linea')) {
        linea.classList.toggle('completada', num < pasoActivo);
      }
    });
  },

  /** Envía el formulario a la API */
  async enviar() {
    const form = document.getElementById('form-solicitud');
    if (!form) return;

    const respuestas = [];
    form.querySelectorAll('[name^="pregunta_"]').forEach(campo => {
      const idPregunta = parseInt(campo.name.replace('pregunta_', ''), 10);
      respuestas.push({ idPregunta, respuesta: campo.value });
    });

    Spinner.show();
    try {
      // const resultado = await Http.post('/solicitudes', { respuestas });
      const resultado = { idSolicitud: 'TEST-2026' };
      Alerta.mostrar('alerta-formulario', 'exito', `Solicitud enviada correctamente. Folio: ${resultado.idSolicitud}`);
      // Redirigir a panel de estatus
      setTimeout(() => { window.location.href = 'panel-estatus.html'; }, 2000);
    } catch (err) {
      Alerta.mostrar('alerta-formulario', 'error', 'Error al enviar la solicitud. Intente de nuevo.');
    } finally {
      Spinner.hide();
    }
  },
};

/* ─────────────────────────────────────────
   MÓDULO: PANEL DE ASPIRANTE (vista mínima)
   Autenticación provisional por ficha + CURP
───────────────────────────────────────── */
const PanelAspirante = {
  async init() {
    const contenedor = document.getElementById('aspirante-datos');
    if (!contenedor) return;
    if (!await AuthSession.requireRole('APPLICANT', 'login.html')) return;
    AuthSession.bindLogout('login.html', 'alerta-aspirante');

    try {
      const session = await Http.authSession();
      const nav = document.getElementById('nombre-usuario-nav');
      if (nav) nav.textContent = session.identity || 'Aspirante';
      this.setTexto('aspirante-ficha', session.identity);
      this.setTexto('aspirante-periodo', session.periodo);
      this.setTexto('aspirante-anio', session.anio);
    } catch (error) {
      Alerta.mostrar('alerta-aspirante', 'error', 'No se pudo cargar la información de la sesión.');
    }
  },

  setTexto(id, valor) {
    const el = document.getElementById(id);
    if (el) el.textContent = (valor === null || valor === undefined) ? '—' : String(valor);
  },
};

/* ─────────────────────────────────────────
   INICIALIZACIÓN AUTOMÁTICA
───────────────────────────────────────── */
document.addEventListener('DOMContentLoaded', () => {
  Modal.iniciarCierre();

  // Detectar página activa por id en <body>
  const pagina = document.body.id;

  switch (pagina) {
    case 'pagina-login-estudiante': LoginEstudiante.init();   break;
    case 'pagina-panel-estatus':    PanelEstatus.init();      break;
    case 'pagina-panel-aspirante':  PanelAspirante.init();    break;
    case 'pagina-formulario':       FormularioSolicitud.init(); break;
  }
});
