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
  /** Momento de la última respuesta del servidor; la sesión expira por inactividad en el backend. */
  ultimaActividad: Date.now(),

  /** Registra la actividad y notifica una sesión expirada (401) en endpoints de negocio. */
  registrarRespuesta(endpoint, status) {
    this.ultimaActividad = Date.now();
    if (status === 401 && !endpoint.startsWith('/auth/')) {
      SesionInactividad.expirada();
    }
  },

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
    this.registrarRespuesta(endpoint, resp.status);
    if (!resp.ok) {
      const error = new Error(data.error || 'No se pudo comprobar la sesión.');
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
    this.registrarRespuesta(endpoint, resp.status);
    if (!resp.ok) {
      const error = new Error(data?.error || 'No se pudo completar la solicitud.');
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
   * Realiza una petición PUT con body JSON. Las operaciones de cambio
   * siempre envían el token CSRF de la sesión.
   */
  async put(endpoint, body) {
    if (!this.csrfToken) await this.authSession();
    const resp = await fetch(Config.apiBase + endpoint, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': this.csrfToken || '' },
      credentials: 'same-origin',
      body: JSON.stringify(body ?? {}),
    });
    const data = await resp.json().catch(() => ({}));
    this.registrarRespuesta(endpoint, resp.status);
    if (!resp.ok) {
      const error = new Error(data.error || 'No se pudo completar la operación.');
      error.status = resp.status;
      error.payload = data;
      throw error;
    }
    return data;
  },
};

/** Mensaje para el usuario ante errores de las operaciones de negocio. */
function mensajeOperacion(error, generico) {
  if (error.status === 401) return 'Su sesión expiró. Inicie sesión nuevamente.';
  if (error.status === 403) return 'La sesión de seguridad cambió. Recargue la página e intente de nuevo.';
  if (error.status === 404) return 'La solicitud no existe o no le pertenece.';
  if (error.status === 409 && error.payload?.error) return error.payload.error;
  return generico;
}

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

  /**
   * Exige uno de los roles indicados. Devuelve la sesión si es válida;
   * en caso contrario redirige y devuelve false.
   * @param {string|string[]} expectedRole
   */
  async requireRole(expectedRole, loginPath) {
    const permitidos = Array.isArray(expectedRole) ? expectedRole : [expectedRole];
    try {
      const session = await Http.authSession();
      if (!session.authenticated) {
        window.location.replace(loginPath);
        return false;
      }
      if (!permitidos.includes(session.role)) {
        window.location.replace(this.panelFor(session.role, loginPath));
        return false;
      }
      return session;
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
    if (new URLSearchParams(window.location.search).get('sesion') === 'expirada') {
      Alerta.mostrar('alerta-login', 'advertencia', 'Su sesión expiró. Inicie sesión nuevamente.');
    }
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
   UTILIDADES DE PRESENTACIÓN
───────────────────────────────────────── */
const Html = {
  /** Escapa texto antes de insertarlo con innerHTML (comentarios, nombres, etc.). */
  escapar(valor) {
    return String(valor ?? '').replace(/[&<>"']/g, (c) => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
    }[c]));
  },
};

const Formato = {
  /** Convierte 'AAAA-MM-DD' (fecha local) o 'AAAA-MM-DDTHH:mm:ss' en Date. */
  aFecha(valor) {
    const texto = String(valor);
    if (/^\d{4}-\d{2}-\d{2}$/.test(texto)) {
      return new Date(Number(texto.slice(0, 4)), Number(texto.slice(5, 7)) - 1, Number(texto.slice(8, 10)));
    }
    return new Date(texto);
  },
  fecha(valor) {
    if (!valor) return '—';
    const fecha = this.aFecha(valor);
    if (Number.isNaN(fecha.getTime())) return String(valor);
    return fecha.toLocaleDateString('es-MX', { day: '2-digit', month: 'short', year: 'numeric' });
  },
  fechaHora(valor) {
    if (!valor) return '—';
    const fecha = this.aFecha(valor);
    if (Number.isNaN(fecha.getTime())) return String(valor);
    return fecha.toLocaleString('es-MX', {
      day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
    });
  },
  moneda(valor) {
    if (valor === null || valor === undefined) return '—';
    return Number(valor).toLocaleString('es-MX', { style: 'currency', currency: 'MXN' });
  },
};

const EstatusSolicitud = {
  mapa: {
    PENDIENTE:  ['badge-pendiente', 'Pendiente de revisión', 'fa-hourglass-half'],
    AUTORIZADO: ['badge-aprobado',  'Autorizado',            'fa-check'],
    RECHAZADO:  ['badge-rechazado', 'Rechazado',             'fa-times'],
    CANCELADO:  ['badge-cancelado', 'Cancelado',             'fa-ban'],
    APLICADO:   ['badge-aplicado',  'Aplicado',              'fa-check-circle'],
    RETIRADO:   ['badge-retirado',  'Retirado',              'fa-undo'],
  },
  datos(estatus) {
    return this.mapa[estatus] || ['badge-revision', estatus || 'Sin estatus', 'fa-info-circle'];
  },
  etiqueta(estatus) {
    return this.datos(estatus)[1];
  },
  badge(estatus) {
    const [clase, texto, icono] = this.datos(estatus);
    return `<span class="badge-estatus ${clase}"><i class="fa ${icono}"></i> ${Html.escapar(texto)}</span>`;
  },
};

/* ─────────────────────────────────────────
   SESIÓN EXPIRADA E INACTIVIDAD
   Equipo 3 · usa /api/auth/me y /api/auth/logout del equipo 1.
   El backend cierra la sesión tras 30 minutos sin peticiones
   (server.servlet.session.timeout); se avisa 5 minutos antes.
───────────────────────────────────────── */
const SesionInactividad = {
  duracionMs: 30 * 60 * 1000,
  avisoMs: 5 * 60 * 1000,
  loginPath: 'login.html',
  intervalo: null,
  finalizada: false,

  iniciar(loginPath) {
    this.loginPath = loginPath;
    this.crearModales();
    this.intervalo = setInterval(() => this.revisar(), 15000);
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden) this.revisar();
    });
  },

  revisar() {
    if (this.finalizada) return;
    const restante = this.duracionMs - (Date.now() - Http.ultimaActividad);
    if (restante <= 0) {
      this.expirada();
    } else if (restante <= this.avisoMs) {
      const minutos = document.getElementById('inactividad-minutos');
      if (minutos) minutos.textContent = String(Math.max(1, Math.ceil(restante / 60000)));
      Modal.abrir('modal-inactividad');
    }
  },

  async continuar() {
    try {
      const session = await Http.authSession();
      if (!session.authenticated) {
        this.expirada();
        return;
      }
      Modal.cerrar('modal-inactividad');
    } catch (error) {
      this.expirada();
    }
  },

  /** Muestra el aviso de sesión expirada y redirige al inicio de sesión. */
  expirada() {
    if (this.finalizada) return;
    this.finalizada = true;
    clearInterval(this.intervalo);
    this.crearModales();
    Modal.cerrar('modal-inactividad');
    Modal.abrir('modal-sesion-expirada');
    setTimeout(() => this.irALogin(), 5000);
  },

  irALogin() {
    window.location.replace(`${this.loginPath}?sesion=expirada`);
  },

  crearModales() {
    if (document.getElementById('modal-sesion-expirada')) return;
    const contenedor = document.createElement('div');
    contenedor.innerHTML = `
      <div id="modal-inactividad" class="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="titulo-inactividad">
        <div class="modal-box">
          <div class="modal-header"><h3 id="titulo-inactividad"><i class="fa fa-clock-o"></i> Sesión por expirar</h3></div>
          <div class="modal-body">
            <div class="alerta alerta-advertencia mb-0">
              <i class="fa fa-exclamation-triangle"></i>
              <span>Por inactividad, su sesión se cerrará en <strong id="inactividad-minutos">5</strong> minuto(s).
              ¿Desea continuar?</span>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn-itt btn-secundario" id="btn-inactividad-salir"><i class="fa fa-sign-out"></i> Salir</button>
            <button type="button" class="btn-itt btn-primario" id="btn-inactividad-continuar"><i class="fa fa-refresh"></i> Continuar sesión</button>
          </div>
        </div>
      </div>
      <div id="modal-sesion-expirada" class="modal-overlay" role="alertdialog" aria-modal="true" aria-labelledby="titulo-sesion-expirada">
        <div class="modal-box">
          <div class="modal-header"><h3 id="titulo-sesion-expirada"><i class="fa fa-lock"></i> Sesión expirada</h3></div>
          <div class="modal-body">
            <div class="alerta alerta-error mb-0">
              <i class="fa fa-times-circle"></i>
              <span>Su sesión expiró o ya no tiene acceso. Será dirigido al inicio de sesión.</span>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn-itt btn-primario" id="btn-sesion-expirada-login"><i class="fa fa-sign-in"></i> Iniciar sesión</button>
          </div>
        </div>
      </div>`;
    document.body.appendChild(contenedor);
    document.getElementById('btn-inactividad-continuar').addEventListener('click', () => this.continuar());
    document.getElementById('btn-inactividad-salir').addEventListener('click', () => AuthSession.logout(this.loginPath, 'alerta-panel'));
    document.getElementById('btn-sesion-expirada-login').addEventListener('click', () => this.irALogin());
  },
};

/* ─────────────────────────────────────────
   MÓDULO: PANEL DEL ESTUDIANTE Y ASPIRANTE
   Equipo 3 · perfil, convocatorias, seguimiento, retiro y reenvío.
   API: /api/solicitante/* (identidad tomada de la sesión).
───────────────────────────────────────── */
const PanelSolicitante = {
  rolPorPagina: {
    'pagina-panel-estatus':   'STUDENT',
    'pagina-panel-aspirante': 'APPLICANT',
  },
  idRetiro: null,

  async init() {
    if (!document.getElementById('lista-solicitudes')) return;
    const session = await AuthSession.requireRole(this.rolPorPagina[document.body.id], 'login.html');
    if (!session) return;
    AuthSession.bindLogout('login.html', 'alerta-panel');
    SesionInactividad.iniciar('login.html');
    this.bindAcciones();
    await this.cargar();
  },

  async cargar() {
    Spinner.show();
    try {
      const [perfil, convocatorias, solicitudes] = await Promise.all([
        Http.get('/solicitante/perfil'),
        Http.get('/solicitante/convocatorias'),
        Http.get('/solicitante/solicitudes'),
      ]);
      this.renderPerfil(perfil, solicitudes);
      this.renderConvocatorias(convocatorias);
      this.renderSolicitudes(solicitudes);
    } catch (err) {
      if (err.status !== 401) {
        Alerta.mostrar('alerta-panel', 'error',
          mensajeOperacion(err, 'No se pudo cargar la información del panel. Intente más tarde.'));
      }
    } finally {
      Spinner.hide();
    }
  },

  renderPerfil(perfil, solicitudes) {
    const esEstudiante = perfil.tipo === 'ESTUDIANTE';
    this.setTexto('nombre-usuario-nav', perfil.nombre || perfil.identificador);
    this.setTexto('info-nombre', perfil.nombreCompleto || perfil.identificador);

    const meta = [
      `<span><i class="fa fa-id-card"></i> ${esEstudiante ? 'No. de control' : 'Ficha'}: ${Html.escapar(perfil.identificador)}</span>`,
    ];
    if (esEstudiante && perfil.carrera) {
      meta.push(`<span><i class="fa fa-university"></i> ${Html.escapar(perfil.carrera)}</span>`);
    }
    if (perfil.semestre) {
      meta.push(`<span><i class="fa fa-graduation-cap"></i> ${Html.escapar(perfil.semestre)}° semestre</span>`);
    }
    if (!esEstudiante && perfil.anio) {
      meta.push(`<span><i class="fa fa-calendar"></i> ${Html.escapar(perfil.periodoDescripcion)} ${Html.escapar(perfil.anio)}</span>`);
    }
    const metaEl = document.getElementById('info-meta');
    if (metaEl) metaEl.innerHTML = meta.join('');

    const badge = document.getElementById('badge-estatus-actual');
    if (badge) {
      badge.outerHTML = solicitudes.length
        ? EstatusSolicitud.badge(solicitudes[0].estatus).replace('<span ', '<span id="badge-estatus-actual" ')
        : '<span id="badge-estatus-actual" class="badge-estatus badge-revision"><i class="fa fa-info-circle"></i> Sin solicitudes</span>';
    }
  },

  renderConvocatorias(convocatorias) {
    const contenedor = document.getElementById('lista-convocatorias');
    if (!contenedor) return;
    if (!convocatorias.length) {
      contenedor.innerHTML = `<div class="alerta alerta-info mb-0"><i class="fa fa-info-circle"></i>
        <span>Por ahora no hay convocatorias en periodo de recepción de solicitudes.</span></div>`;
      return;
    }

    const grupos = new Map();
    convocatorias.forEach((c) => {
      if (!grupos.has(c.idConvocatoria)) grupos.set(c.idConvocatoria, []);
      grupos.get(c.idConvocatoria).push(c);
    });

    contenedor.innerHTML = [...grupos.values()].map((tipos) => {
      const c = tipos[0];
      const encabezado = `
        <div class="convocatoria-encabezado">
          <div>
            <div class="convocatoria-titulo">Convocatoria ${Html.escapar(c.periodoDescripcion)} ${Html.escapar(c.anio)}</div>
            <div class="convocatoria-fechas"><i class="fa fa-calendar"></i>
              Recepción de solicitudes: ${Formato.fecha(c.fechaInicio)} al ${Formato.fecha(c.fechaFin)}</div>
          </div>
        </div>`;

      if (c.idSolicitudExistente) {
        return `<div class="convocatoria-item">${encabezado}
          <div class="alerta alerta-info mb-0"><i class="fa fa-info-circle"></i>
            <span>Ya tiene registrada la solicitud con folio <a href="#solicitud-${Html.escapar(c.idSolicitudExistente)}">${Html.escapar(c.idSolicitudExistente)}</a>
            en esta convocatoria. Solo se permite una solicitud por convocatoria.</span></div>
        </div>`;
      }

      const opciones = tipos.map((t) => {
        const url = `formulario.html?convocatoria=${encodeURIComponent(t.idConvocatoria)}`
          + `&tipo=${encodeURIComponent(t.idTipoSolicitud)}&formulario=${encodeURIComponent(t.idFormulario)}`;
        return `
          <div class="convocatoria-tipo">
            <div>
              <div class="convocatoria-tipo-nombre">${Html.escapar(t.tipoSolicitud)}</div>
              <div class="convocatoria-tipo-detalle">${Html.escapar(t.tipoDescripcion || '')}</div>
              <div class="convocatoria-tipo-detalle">Reducción: <strong>${Formato.moneda(t.montoDeduccion)}</strong></div>
            </div>
            <a class="btn-itt btn-agregar" href="${url}"><i class="fa fa-plus-circle"></i> Iniciar solicitud</a>
          </div>`;
      }).join('');
      return `<div class="convocatoria-item">${encabezado}${opciones}</div>`;
    }).join('');
  },

  renderSolicitudes(solicitudes) {
    const contenedor = document.getElementById('lista-solicitudes');
    if (!solicitudes.length) {
      contenedor.innerHTML = `<div class="card-itt"><div class="card-itt-body">
        <div class="alerta alerta-info mb-0"><i class="fa fa-info-circle"></i>
        <span>Aún no tiene solicitudes registradas. Si hay una convocatoria abierta, puede iniciar su trámite en la sección anterior.</span></div>
      </div></div>`;
      return;
    }
    contenedor.innerHTML = solicitudes.map((s) => this.solicitudHtml(s)).join('');
  },

  solicitudHtml(s) {
    return `
      <article class="card-itt solicitud-card" id="solicitud-${Html.escapar(s.idSolicitud)}">
        <div class="card-itt-header solicitud-card-header">
          <span><i class="fa fa-file-text-o"></i> Folio ${Html.escapar(s.idSolicitud)}</span>
          ${EstatusSolicitud.badge(s.estatus)}
        </div>
        <div class="card-itt-body">
          <div class="grid-datos mb-16">
            ${this.dato('Convocatoria', `${Html.escapar(s.periodoDescripcion)} ${Html.escapar(s.anio)}`)}
            ${this.dato('Tipo de solicitud', Html.escapar(s.tipoSolicitud))}
            ${this.dato('Fecha de solicitud', Formato.fecha(s.fechaSolicitud))}
            ${this.dato('Última actualización', Formato.fechaHora(s.fechaActualizacion))}
            ${this.dato('Recepción de solicitudes', `${Formato.fecha(s.fechaInicioRecepcion)} al ${Formato.fecha(s.fechaFinRecepcion)}`)}
          </div>
          ${this.deduccionHtml(s.deduccion)}
          <h3 class="subtitulo-seccion"><i class="fa fa-history"></i> Seguimiento del trámite</h3>
          <div class="estatus-timeline">${this.timelineHtml(s)}</div>
          ${this.accionesHtml(s)}
        </div>
      </article>`;
  },

  dato(etiqueta, valorHtml) {
    return `<div><div class="dato-etiqueta">${etiqueta}</div><div class="dato-valor">${valorHtml}</div></div>`;
  },

  deduccionHtml(d) {
    if (!d) return '';
    const aviso = d.estatus === 'CANCELADO'
      ? `<div class="alerta alerta-advertencia mb-0 mt-12"><i class="fa fa-exclamation-triangle"></i>
          <span>La deducción fue cancelada y permanece así hasta una nueva aprobación de la administración.</span></div>`
      : '';
    return `
      <div class="deduccion-resumen mb-16">
        <div class="deduccion-titulo"><i class="fa fa-money"></i> Deducción ${EstatusSolicitud.badge(d.estatus)}</div>
        <div class="grid-datos">
          ${this.dato('Monto base', Formato.moneda(d.montoPago))}
          ${this.dato('Reducción', Formato.moneda(d.montoDeduccion))}
          ${this.dato('Monto a pagar', `<strong>${Formato.moneda(d.montoFinal)}</strong>`)}
        </div>
        ${aviso}
      </div>`;
  },

  timelineHtml(s) {
    // El alta de la solicitud no genera movimiento en el historial de Oracle.
    const movimientos = [
      { estatus: 'PENDIENTE', fecha: s.fechaSolicitud, administrativo: false, texto: 'Solicitud registrada' },
      ...s.historial,
    ];
    return movimientos.map((m, idx) => {
      const ultimo = idx === movimientos.length - 1;
      const clase = !ultimo ? 'completado'
        : (['RECHAZADO', 'CANCELADO'].includes(m.estatus) ? 'rechazado' : 'activo');
      const origen = m.texto || (m.administrativo ? 'Movimiento de la administración' : 'Movimiento del solicitante');
      const comentario = m.comentario
        ? `<div class="estatus-item-comentario"><i class="fa fa-comment"></i>
            ${m.administrativo ? '<strong>Comentario de la administración:</strong> ' : ''}${Html.escapar(m.comentario)}</div>`
        : '';
      return `
        <div class="estatus-item ${clase}">
          <div class="estatus-item-fecha">${Formato.fechaHora(m.fecha)} · ${Html.escapar(origen)}</div>
          <div class="estatus-item-titulo">${Html.escapar(EstatusSolicitud.etiqueta(m.estatus))}</div>
          ${comentario}
        </div>`;
    }).join('');
  },

  accionesHtml(s) {
    const fin = Formato.fecha(s.fechaFinRecepcion);
    if (s.puedeRetirar) {
      return `
        <div class="acciones-solicitud">
          <div class="alerta alerta-info"><i class="fa fa-info-circle"></i>
            <span>Su solicitud está en revisión y no puede editarse. Si necesita corregirla, puede retirarla hasta el ${fin}
            y después reenviarla dentro del mismo plazo.</span></div>
          <button type="button" class="btn-itt btn-borrar" data-retirar="${Html.escapar(s.idSolicitud)}">
            <i class="fa fa-undo"></i> Retirar solicitud</button>
        </div>`;
    }
    if (s.puedeReenviar) {
      const motivo = {
        RECHAZADO: 'Su solicitud fue rechazada. Revise los comentarios de la administración, corrija la información y reenvíela.',
        CANCELADO: 'La deducción de su solicitud fue cancelada. Puede corregir la información y reenviarla.',
        RETIRADO:  'Retiró esta solicitud. Puede reenviarla con o sin cambios.',
      }[s.estatus];
      const url = `formulario.html?reenviar=${encodeURIComponent(s.idSolicitud)}`
        + `&convocatoria=${encodeURIComponent(s.idConvocatoria)}`
        + `&tipo=${encodeURIComponent(s.idTipoSolicitud)}&formulario=${encodeURIComponent(s.idFormulario)}`;
      return `
        <div class="acciones-solicitud">
          <div class="alerta alerta-advertencia"><i class="fa fa-exclamation-triangle"></i>
            <span>${motivo} El reenvío está disponible hasta el ${fin}.</span></div>
          <a class="btn-itt btn-editar" href="${url}"><i class="fa fa-pencil"></i> Corregir y reenviar</a>
        </div>`;
    }
    if (s.estatus === 'AUTORIZADO') {
      return `<div class="acciones-solicitud"><div class="alerta alerta-exito mb-0"><i class="fa fa-check-circle"></i>
        <span>Su solicitud fue autorizada. La deducción se aplicará en su pago; cualquier cancelación la realiza la administración.</span></div></div>`;
    }
    if (s.estatus === 'APLICADO') {
      return `<div class="acciones-solicitud"><div class="alerta alerta-exito mb-0"><i class="fa fa-check-circle"></i>
        <span>La deducción ya fue aplicada. El trámite concluyó.</span></div></div>`;
    }
    if (!s.recepcionAbierta) {
      return `<div class="acciones-solicitud"><div class="alerta alerta-info mb-0"><i class="fa fa-info-circle"></i>
        <span>El periodo de recepción terminó el ${fin}; ya no es posible retirar ni reenviar esta solicitud.</span></div></div>`;
    }
    return '';
  },

  bindAcciones() {
    document.getElementById('lista-solicitudes').addEventListener('click', (e) => {
      const boton = e.target.closest('[data-retirar]');
      if (!boton) return;
      this.idRetiro = boton.dataset.retirar;
      this.setTexto('retiro-folio', this.idRetiro);
      Alerta.limpiar('alerta-retiro');
      Modal.abrir('modal-retirar');
    });
    document.querySelectorAll('[data-modal-cerrar]').forEach((boton) => {
      boton.addEventListener('click', () => Modal.cerrar(boton.dataset.modalCerrar));
    });
    const confirmar = document.getElementById('btn-confirmar-retiro');
    if (confirmar) confirmar.addEventListener('click', () => this.retirar());
  },

  async retirar() {
    if (!this.idRetiro) return;
    const confirmar = document.getElementById('btn-confirmar-retiro');
    confirmar.disabled = true;
    Spinner.show();
    try {
      await Http.put(`/solicitante/solicitudes/${encodeURIComponent(this.idRetiro)}/retirar`, {});
      Modal.cerrar('modal-retirar');
      Alerta.mostrar('alerta-panel', 'exito',
        `La solicitud con folio ${Html.escapar(this.idRetiro)} fue retirada. Puede reenviarla mientras siga abierto el periodo de recepción.`);
      this.idRetiro = null;
      await this.cargar();
    } catch (err) {
      if (err.status !== 401) {
        Alerta.mostrar('alerta-retiro', 'error', Html.escapar(mensajeOperacion(err, 'No se pudo retirar la solicitud. Intente más tarde.')));
      }
    } finally {
      confirmar.disabled = false;
      Spinner.hide();
    }
  },

  setTexto(id, valor) {
    const el = document.getElementById(id);
    if (el) el.textContent = (valor === null || valor === undefined || valor === '') ? '—' : String(valor);
  },
};

/* ─────────────────────────────────────────
   MÓDULO: FORMULARIO DE SOLICITUD
   Equipo 5
───────────────────────────────────────── */
const FormularioSolicitud = {
  pasoActual: 1,
  totalPasos: 3,

  async init() {
    const form = document.getElementById('form-solicitud');
    if (!form) return;
    if (!await AuthSession.requireRole(['STUDENT', 'APPLICANT'], 'login.html')) return;
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
   INICIALIZACIÓN AUTOMÁTICA
───────────────────────────────────────── */
document.addEventListener('DOMContentLoaded', () => {
  Modal.iniciarCierre();

  // Detectar página activa por id en <body>
  const pagina = document.body.id;

  switch (pagina) {
    case 'pagina-login-estudiante': LoginEstudiante.init();   break;
    case 'pagina-panel-estatus':
    case 'pagina-panel-aspirante':  PanelSolicitante.init();  break;
    case 'pagina-formulario':       FormularioSolicitud.init(); break;
  }
});
