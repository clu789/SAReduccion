/**
 * ============================================================
 * app.js - Módulo Administrativo
 * Sistema de Reducción de Monto de Inscripción
 * Instituto Tecnológico de Toluca
 * ============================================================
 */

'use strict';

/* ─────────────────────────────────────────
   CONFIGURACIÓN GLOBAL
───────────────────────────────────────── */
const Config = {
  apiBase: '/api',
  timeout: 8000,
};

/* ─────────────────────────────────────────
   UTILIDADES HTTP (fetch wrapper)
───────────────────────────────────────── */
const Http = {
  csrfToken: null,

  async get(endpoint) {
    if (window.ITTSecurity) {
      return window.ITTSecurity.json(Config.apiBase + endpoint, { method: 'GET' });
    }
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

  async post(endpoint, body) {
    if (window.ITTSecurity) {
      return window.ITTSecurity.json(Config.apiBase + endpoint, {
        method: 'POST',
        body: JSON.stringify(body),
      });
    }
    const headers = { 'Content-Type': 'application/json' };
    if (!this.csrfToken) await this.authSession();
    if (!this.csrfToken) throw new Error('Recargue la página para renovar la sesión.');
    headers['X-CSRF-TOKEN'] = this.csrfToken;
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
    // Equipo 1: con el cliente común cargado se comparte una sola consulta de
    // sesión, para que el token CSRF y el captcha pertenezcan a la misma sesión.
    if (window.ITTSecurity) {
      const session = await window.ITTSecurity.sesion();
      if (session.csrfToken) this.csrfToken = session.csrfToken;
      return session;
    }
    try {
      return await this.get('/auth/me');
    } catch (error) {
      if (error.status === 401 && error.payload?.csrfToken) return error.payload;
      throw error;
    }
  },

  async put(endpoint, body) {
    if (window.ITTSecurity) {
      return window.ITTSecurity.json(Config.apiBase + endpoint, {
        method: 'PUT',
        body: JSON.stringify(body),
      });
    }
    const headers = { 'Content-Type': 'application/json' };
    if (!this.csrfToken) throw new Error('Recargue la página para renovar la sesión.');
    headers['X-CSRF-TOKEN'] = this.csrfToken;
    const resp = await fetch(Config.apiBase + endpoint, {
      method: 'PUT',
      headers,
      credentials: 'same-origin',
      body: JSON.stringify(body),
    });
    const data = resp.status === 204 ? null : await resp.json().catch(() => ({}));
    if (data?.csrfToken) this.csrfToken = data.csrfToken;
    if (!resp.ok) {
      const error = new Error(data?.message || data?.error || 'No se pudo completar la solicitud.');
      error.status = resp.status;
      error.payload = data;
      throw error;
    }
    return data;
  },
};

const AuthSession = {
  async requireRole(expectedRole, loginPath, otherProfileLoginPath) {
    if (window.ITTSecurity) {
      const session = await window.ITTSecurity.protegerPagina(expectedRole, { login: loginPath });
      return Boolean(session);
    }
    try {
      const session = await Http.authSession();
      if (!session.authenticated) {
        window.location.replace(loginPath);
        return false;
      }
      if (session.role !== expectedRole) {
        window.location.replace(otherProfileLoginPath);
        return false;
      }
      return true;
    } catch (error) {
      window.location.replace(loginPath);
      return false;
    }
  },

  async loginPage(expectedRole, panelPath, alertId) {
    try {
      const session = await Http.authSession();
      if (session.authenticated && session.role === expectedRole) {
        window.location.replace(panelPath);
        return;
      }
      if (session.authenticated) {
        Alerta.mostrar(alertId, 'advertencia', 'Hay una sesión activa de otro perfil. Inicie sesión con la cuenta autorizada.');
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
  if (error.payload?.error === 'CAPTCHA_INVALID') return 'La verificación de seguridad no es correcta. Intente de nuevo.';
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
   ESCAPE HTML Y ALERTAS EN-PÁGINA
───────────────────────────────────────── */
const Html = {
  escapar(valor) {
    return String(valor ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  },
};

const Alerta = {
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
    div.className = `alerta alerta-${Html.escapar(tipo)}`;
    const iconEl = document.createElement('i');
    iconEl.className = `fa ${icono}`;
    iconEl.setAttribute('aria-hidden', 'true');
    const spanEl = document.createElement('span');
    spanEl.textContent = mensaje == null ? '' : String(mensaje);
    div.appendChild(iconEl);
    div.appendChild(spanEl);
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
  _confirmResolve: null,
  _cierreIniciado: false,

  abrir(modalId) {
    const el = document.getElementById(modalId);
    if (el) el.classList.add('activo');
  },
  cerrar(modalId) {
    const el = document.getElementById(modalId);
    if (el) el.classList.remove('activo');
    // Si cierran #modal-confirmar sin pasar por resolverConfirmar() (la X o
    // el fondo), se resuelve como "cancelado" para no dejar la promesa colgada.
    if (modalId === 'modal-confirmar' && this._confirmResolve) {
      const resolve = this._confirmResolve;
      this._confirmResolve = null;
      resolve(false);
    }
  },
  iniciarCierre() {
    if (this._cierreIniciado) return;
    this._cierreIniciado = true;
    document.querySelectorAll('.modal-overlay').forEach(overlay => {
      overlay.addEventListener('click', (e) => {
        if (e.target === overlay) this.cerrar(overlay.id);
      });
    });
    document.querySelectorAll('[data-modal-cerrar]').forEach(btn => {
      btn.addEventListener('click', () => this.cerrar(btn.dataset.modalCerrar));
    });
    document.querySelectorAll('[data-confirmar-valor]').forEach(btn => {
      btn.addEventListener('click', () => this.resolverConfirmar(btn.dataset.confirmarValor === 'true'));
    });
  },

  /* ── Reemplazo de confirm() nativo con el modal propio del panel.
     Uso: const ok = await Modal.confirmar('¿Seguro?'); if (!ok) return; ── */
  confirmar(mensaje, titulo = 'Confirmar acción') {
    return new Promise((resolve) => {
      const tituloEl = document.getElementById('confirmar-titulo');
      const textoEl = document.getElementById('confirmar-texto');
      if (tituloEl) {
        const iconEl = document.createElement('i');
        iconEl.className = 'fa fa-question-circle';
        iconEl.setAttribute('aria-hidden', 'true');
        tituloEl.replaceChildren(iconEl, document.createTextNode(` ${titulo == null ? 'Confirmar acción' : String(titulo)}`));
      }
      if (textoEl) textoEl.textContent = mensaje == null ? '' : String(mensaje);
      this._confirmResolve = resolve;
      this.abrir('modal-confirmar');
    });
  },

  resolverConfirmar(valor) {
    const resolve = this._confirmResolve;
    this._confirmResolve = null;
    this.cerrar('modal-confirmar');
    if (resolve) resolve(valor);
  },
};

/* ─────────────────────────────────────────
   MÓDULO: LOGIN ADMINISTRADOR
   Equipo 4
───────────────────────────────────────── */
const LoginAdmin = {
  init() {
    const form = document.getElementById('form-login-admin');
    if (!form) return;
    AuthSession.loginPage('ADMIN', 'panel-admin.html', 'alerta-login-admin');

    const usuario = document.getElementById('usuario-admin');
    const password = document.getElementById('password-admin');
    const boton = document.getElementById('btn-login-admin');
    const etiquetaBoton = boton?.querySelector('.btn-login-admin-text');
    const indicadorCarga = boton?.querySelector('.login-button-spinner');
    const togglePassword = document.getElementById('toggle-password-admin');

    togglePassword?.addEventListener('click', () => {
      const mostrar = password.type === 'password';
      password.type = mostrar ? 'text' : 'password';
      togglePassword.textContent = mostrar ? 'Ocultar' : 'Mostrar';
      togglePassword.setAttribute('aria-label', `${mostrar ? 'Ocultar' : 'Mostrar'} contraseña`);
      togglePassword.setAttribute('aria-pressed', String(mostrar));
    });

    const validarCampo = (campo, idError, mensaje, esValido) => {
      const error = document.getElementById(idError);
      campo?.classList.toggle('error', !esValido);
      campo?.setAttribute('aria-invalid', String(!esValido));
      if (error) {
        error.textContent = esValido ? '' : mensaje;
        error.classList.toggle('visible', !esValido);
      }
      return esValido;
    };

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      if (boton?.disabled) return;

      const validoUsuario = validarCampo(
        usuario, 'usuario-admin-error', 'Ingrese su usuario administrativo.', Boolean(usuario?.value.trim())
      );
      const validaPassword = validarCampo(
        password, 'password-admin-error', 'Ingrese su contraseña.', Boolean(password?.value)
      );
      if (!validoUsuario || !validaPassword) return;

      if (boton) {
        boton.disabled = true;
        boton.setAttribute('aria-busy', 'true');
      }
      if (indicadorCarga) indicadorCarga.hidden = false;
      if (etiquetaBoton) etiquetaBoton.textContent = 'Verificando acceso…';
      try {
        const username = usuario.value.trim().toUpperCase();
        const captcha = window.ITTSecurity ? window.ITTSecurity.captchaDe('#captcha-login-admin') : null;
        const session = await Http.post('/auth/login', {
          username,
          password: password.value,
          perfil: 'ADMINISTRADOR',
          captcha: captcha?.valor() || '',
        });
        if (session.role !== 'ADMIN' || session.identity !== username) {
          await Http.post('/auth/logout', {});
          throw new Error('Perfil no autorizado');
        }
        password.value = '';
        window.location.href = 'panel-admin.html';
      } catch (err) {
        Alerta.mostrar('alerta-login-admin', 'error', mensajeLogin(err));
        // El captcha es de un solo uso: cada intento necesita un desafío nuevo.
        window.ITTSecurity?.captchaDe('#captcha-login-admin')?.recargar();
      } finally {
        password.value = '';
        if (boton) {
          boton.disabled = false;
          boton.removeAttribute('aria-busy');
        }
        if (indicadorCarga) indicadorCarga.hidden = true;
        if (etiquetaBoton) etiquetaBoton.textContent = 'Acceder al Panel';
      }
    });
  },
};

/* ─────────────────────────────────────────
   MÓDULO: PANEL DE ADMINISTRACIÓN / BANDEJA
   Equipo 2
───────────────────────────────────────── */
const ESTATUS_BADGE = {
  PENDIENTE:  'badge-pendiente',
  AUTORIZADO: 'badge-autorizado',
  RECHAZADO:  'badge-rechazado',
  CANCELADO:  'badge-cancelado',
  APLICADO:   'badge-aplicado',
  RETIRADO:   'badge-retirado',
};

const ESTATUS_ICONO = {
  PENDIENTE:  { clase: 'pendiente',  icon: 'fa-clock-o' },
  AUTORIZADO: { clase: 'autorizado', icon: 'fa-check' },
  RECHAZADO:  { clase: 'rechazado',  icon: 'fa-times' },
  CANCELADO:  { clase: 'cancelado',  icon: 'fa-ban' },
  APLICADO:   { clase: 'aplicado',   icon: 'fa-flag-checkered' },
  RETIRADO:   { clase: 'retirado',   icon: 'fa-undo' },
};

const PanelAdmin = {
  solicitudes: [],      // Datos cargados desde la API
  filtroActual: 'todos',
  filtroConvocatoria: '',
  filtroSolicitante: '',
  hayFiltroConvocatoria: false,
  ordenColumna: null,
  ordenDireccion: 'asc',
  _solicitudActual: null,

  async init() {
    const bandeja = document.getElementById('tabla-solicitudes');
    if (!bandeja) return;
    if (!await AuthSession.requireRole('ADMIN', 'login.html', '/solicitante/login.html')) return;
    AuthSession.bindLogout('login.html', 'alerta-admin');

    await this.cargarConvocatorias();
    await Promise.all([this.cargarEstadisticas(), this.cargarSolicitudes()]);
    this.iniciarFiltros();
    this.iniciarBuscador();
    this.iniciarOrdenamiento();
    this.iniciarEventosModales();
    const tbody = bandeja.querySelector('tbody');
    if (tbody) {
      tbody.addEventListener('click', (e) => {
        const boton = e.target.closest('button[data-accion][data-id]');
        if (!boton) return;
        const id = Number(boton.dataset.id);
        if (!Number.isSafeInteger(id) || id <= 0) return;
        if (boton.dataset.accion === 'detalle') this.verDetalle(id);
        else if (boton.dataset.accion === 'respuestas') this.verRespuestas(id);
      });
    }
    Modal.iniciarCierre();
  },

  /* ── Catálogo de convocatorias y selección inicial ── */
  async cargarConvocatorias() {
    const select = document.getElementById('filtro-convocatoria');
    try {
      const convocatorias = await ConvocatoriasAdmin.listar();
      const lista = Array.isArray(convocatorias) ? convocatorias : [];
      if (!select) return;

      select.innerHTML = '';
      lista.forEach(c => {
        const option = document.createElement('option');
        option.value = String(c.idConvocatoria);
        option.textContent = this.etiquetaConvocatoria(c);
        select.appendChild(option);
      });

      const todas = document.createElement('option');
      todas.value = '';
      todas.textContent = 'Todas las convocatorias';
      select.appendChild(todas);

      const activa = lista.find(c => c.activa) || null;
      this.filtroConvocatoria = activa ? String(activa.idConvocatoria) : '';
      select.value = this.filtroConvocatoria;
      select.disabled = false;
    } catch (err) {
      if (select) {
        select.innerHTML = '<option value="">Convocatorias no disponibles</option>';
        select.disabled = true;
      }
      console.warn('No se pudieron cargar las convocatorias:', err.message);
    }
  },

  etiquetaConvocatoria(convocatoria) {
    const partes = [String(convocatoria.idConvocatoria)];
    if (convocatoria.periodo) partes.push(`Periodo ${convocatoria.periodo}`);
    if (convocatoria.anio) partes.push(String(convocatoria.anio));
    const etiqueta = partes.join(' · ');
    return convocatoria.activa ? `${etiqueta} · Activa` : etiqueta;
  },

  /* ── Estadísticas de la barra superior ── */
  async cargarEstadisticas() {
    try {
      const params = new URLSearchParams();
      if (this.filtroConvocatoria) params.set('convocatoria', this.filtroConvocatoria);
      const query = params.toString();
      const stats = await Http.get(query ? `/solicitudes/estadisticas?${query}` : '/solicitudes/estadisticas');
      this.actualizarContador('stat-total',      stats.total      ?? 0);
      this.actualizarContador('stat-pendiente',  stats.pendiente  ?? 0);
      this.actualizarContador('stat-autorizado', stats.autorizado ?? 0);
      this.actualizarContador('stat-rechazado',  stats.rechazado  ?? 0);
      this.actualizarContador('stat-retirado',   stats.retirado   ?? 0);
    } catch (err) {
      console.warn('No se pudieron cargar estadísticas:', err.message);
    }
  },

  actualizarContador(id, valor) {
    const el = document.getElementById(id);
    if (el) el.textContent = valor;
  },

  /* ── Carga la lista de solicitudes con filtros combinados ── */
  async cargarSolicitudes() {
    Spinner.show();
    try {
      const params = new URLSearchParams();
      if (this.filtroActual && this.filtroActual !== 'todos') {
        params.set('estatus', this.filtroActual);
      }
      if (this.filtroConvocatoria) params.set('convocatoria', this.filtroConvocatoria);
      if (this.filtroSolicitante)  params.set('solicitante',  this.filtroSolicitante);

      const query = params.toString();
      const endpoint = query ? `/solicitudes?${query}` : '/solicitudes';

      this.solicitudes = await Http.get(endpoint);
      this.hayFiltroConvocatoria = !!this.filtroConvocatoria;
      this.aplicarOrden();
    } catch (err) {
      Alerta.mostrar('alerta-bandeja', 'error', err.message || 'No se pudieron cargar las solicitudes.');
    } finally {
      Spinner.hide();
    }
  },

  /* ── Ordenamiento por columna (en memoria, sin tocar el backend) ── */
  iniciarOrdenamiento() {
    document.querySelectorAll('#tabla-solicitudes th.th-ordenable').forEach(th => {
      th.classList.add('cursor-ordenable');
      th.addEventListener('click', () => {
        const columna = th.dataset.col;
        if (!columna) return;
        if (this.ordenColumna === columna) {
          this.ordenDireccion = this.ordenDireccion === 'asc' ? 'desc' : 'asc';
        } else {
          this.ordenColumna = columna;
          this.ordenDireccion = 'asc';
        }
        this.aplicarOrden();
        this.actualizarIndicadoresOrden();
      });
    });
  },

  actualizarIndicadoresOrden() {
    document.querySelectorAll('#tabla-solicitudes th.th-ordenable').forEach(th => {
      const icono = th.querySelector('i');
      if (!icono) return;
      const activo = th.dataset.col === this.ordenColumna;
      icono.className = activo
        ? `fa fa-sort-${this.ordenDireccion === 'asc' ? 'asc' : 'desc'}`
        : 'fa fa-sort';
    });
  },

  aplicarOrden() {
    if (!this.ordenColumna) {
      this.renderTabla(this.solicitudes);
      return;
    }
    const col = this.ordenColumna;
    const dir = this.ordenDireccion === 'asc' ? 1 : -1;
    const ordenadas = [...this.solicitudes].sort((a, b) => {
      const va = this.valorOrden(a, col);
      const vb = this.valorOrden(b, col);
      if (va == null && vb == null) return 0;
      if (va == null) return 1;
      if (vb == null) return -1;
      if (typeof va === 'number' && typeof vb === 'number') return (va - vb) * dir;
      return String(va).localeCompare(String(vb), 'es', { numeric: true }) * dir;
    });
    this.renderTabla(ordenadas);
  },

  valorOrden(solicitud, columna) {
    switch (columna) {
      case 'idSolicitud':        return Number(solicitud.idSolicitud);
      case 'solicitante':        return (solicitud.nombreSolicitante || solicitud.solicitante || '').toUpperCase();
      case 'tipoSolicitante':    return (solicitud.tipoSolicitante || '').toUpperCase();
      case 'tipoSolicitud':      return (solicitud.tipoSolicitud || '').toUpperCase();
      case 'estatus':            return (solicitud.estatus || '').toUpperCase();
      case 'fechaActualizacion': {
        const valor = solicitud.fechaActualizacion ?? solicitud.fecha_actualizacion;
        return valor ? new Date(valor).getTime() : null;
      }
      default: return null;
    }
  },

  /* ── Renderiza la tabla de solicitudes ── */
  renderTabla(solicitudes) {
    const tbody = document.querySelector('#tabla-solicitudes tbody');
    if (!tbody) return;

    if (!solicitudes.length) {
      const motivo = this.hayFiltroConvocatoria
        ? 'No hay solicitudes para la convocatoria seleccionada.'
        : 'No hay solicitudes que mostrar.';
      tbody.innerHTML = `<tr><td colspan="7" class="text-center tabla-estado-vacia">
        <i class="fa fa-inbox tabla-vacia-icono"></i>
        ${motivo}</td></tr>`;
      return;
    }

    tbody.innerHTML = solicitudes.map(s => {
      const id = Number(s.idSolicitud);
      const badgeClass = Html.escapar(ESTATUS_BADGE[s.estatus] || 'badge-revision');
      const actualizado = Html.escapar(this.formatearFecha(s.fechaActualizacion ?? s.fecha_actualizacion));
      const idEsc = Number.isSafeInteger(id) ? id : Html.escapar(s.idSolicitud);

      return `<tr>
        <td>${idEsc}</td>
        <td>${Html.escapar(s.nombreSolicitante || s.solicitante || '—')}</td>
        <td>${Html.escapar(s.tipoSolicitante   || '—')}</td>
        <td>${Html.escapar(s.tipoSolicitud      || '—')}</td>
        <td>${actualizado}</td>
        <td><span class="badge-estatus ${badgeClass}">${Html.escapar(s.estatus)}</span></td>
        <td class="acciones-celda">
          <button class="btn-itt btn-primario btn-accion-icono" data-accion="detalle" data-id="${idEsc}" title="Ver detalle">
            <i class="fa fa-eye"></i>
          </button>
          <button class="btn-itt btn-secundario btn-accion-icono" data-accion="respuestas" data-id="${idEsc}" title="Ver respuestas del formulario">
            <i class="fa fa-list-alt"></i>
          </button>
        </td>
      </tr>`;
    }).join('');
  },

  /* ── Formatea una fecha ISO/Date para mostrarla en la tabla ── */
  formatearFecha(valor) {
    if (!valor) return '—';
    const fecha = new Date(valor);
    if (isNaN(fecha.getTime())) return valor;
    return fecha.toLocaleString('es-MX', {
      day: '2-digit', month: '2-digit', year: 'numeric',
      hour: '2-digit', minute: '2-digit',
    });
  },

  /* ── Chips de filtro ── */
  iniciarFiltros() {
    document.querySelectorAll('.filtro-chip').forEach(chip => {
      chip.addEventListener('click', () => {
        document.querySelectorAll('.filtro-chip').forEach(c => c.classList.remove('activo'));
        chip.classList.add('activo');
        this.filtroActual = chip.dataset.estatus;
        this.cargarSolicitudes();
      });
    });

    const selectConvocatoria = document.getElementById('filtro-convocatoria');
    if (selectConvocatoria) {
      selectConvocatoria.addEventListener('change', () => {
        this.filtroConvocatoria = selectConvocatoria.value;
        this.cargarEstadisticas();
        this.cargarSolicitudes();
      });
    }
  },

  /* ── Buscador por folio o solicitante ── */
  iniciarBuscador() {
    const input = document.getElementById('buscador');
    if (!input) return;
    let temporizador = null;
    input.addEventListener('input', () => {
      clearTimeout(temporizador);
      temporizador = setTimeout(() => {
        this.filtroSolicitante = input.value.trim();
        this.cargarSolicitudes();
      }, 350);
    });
  },

  /* ── Ver detalle de solicitud y cargar historial ── */
  async verDetalle(idSolicitud) {
    Spinner.show();
    try {
      const solicitud = await Http.get(`/solicitudes/${idSolicitud}`);
      this.renderDetalle(solicitud);
      Modal.abrir('modal-detalle');
    } catch (err) {
      Alerta.mostrar('alerta-bandeja', 'error', err.message || 'No se pudo cargar el detalle de la solicitud.');
    } finally {
      Spinner.hide();
    }
  },

  /* ── Ver respuestas del formulario en ventana emergente ── */
  async verRespuestas(idSolicitud) {
    Spinner.show();
    try {
      const solicitud = await Http.get(`/solicitudes/${idSolicitud}`);
      this.renderRespuestas(solicitud);
      Modal.abrir('modal-respuestas');
    } catch (err) {
      Alerta.mostrar('alerta-bandeja', 'error', err.message || 'No se pudieron cargar las respuestas.');
    } finally {
      Spinner.hide();
    }
  },

  renderRespuestas(solicitud) {
    const folio = document.getElementById('resp-folio');
    const solicitante = document.getElementById('resp-solicitante');
    if (folio) folio.textContent = solicitud.idSolicitud ?? '—';
    if (solicitante) solicitante.textContent = solicitud.nombreSolicitante || solicitud.solicitante || '—';

    const lista = document.getElementById('lista-respuestas');
    if (!lista) return;
    if (solicitud.respuestas && solicitud.respuestas.length) {
      const filas = [...solicitud.respuestas].sort((a, b) => {
        const ordenA = Number.isFinite(a.orden) ? a.orden : Number.MAX_SAFE_INTEGER;
        const ordenB = Number.isFinite(b.orden) ? b.orden : Number.MAX_SAFE_INTEGER;
        return ordenA - ordenB;
      });
      lista.innerHTML = filas.map(r => {
        let respuesta = '';
        if (r.tipoPregunta === 'OPCION_UNICA' || r.tipoPregunta === 'MIXTA') {
          respuesta = r.opcion || 'Sin opción registrada';
          if (r.tipoPregunta === 'MIXTA' && r.respuesta) {
            respuesta += ` — ${r.respuesta}`;
          }
        } else {
          respuesta = r.respuesta || 'Sin respuesta';
        }
        const etiqueta = r.obligatoria === 'S' ? 'Obligatoria' : 'Opcional';
        const pregunta = r.pregunta || `Pregunta #${r.idPregunta || '—'}`;
        return `<div class="detalle-campo">
          <label>${Html.escapar(pregunta)} · ${etiqueta}</label>
          <div class="valor">${Html.escapar(respuesta)}</div>
        </div>`;
      }).join('');
    } else {
      lista.innerHTML = '<p class="texto-vacio-italica">Sin respuestas registradas.</p>';
    }
  },

  renderDetalle(solicitud) {
    this._solicitudActual = solicitud;
    document.getElementById('comentario-correccion').value = '';
    const aviso = document.getElementById('form-notificacion');
    aviso.reset();
    aviso.querySelector('[role="status"]').textContent = '';

    // Datos generales
    const campos = {
      'det-folio':      solicitud.idSolicitud,
      'det-nombre':     solicitud.nombreSolicitante || solicitud.solicitante,
      'det-tipo':       solicitud.tipoSolicitante,
      'det-tipo-solicitud': solicitud.tipoSolicitud,
      'det-convocatoria': solicitud.idConvocatoria,
      'det-fecha-actualizacion': this.formatearFecha(solicitud.fechaActualizacion),
      'det-estatus':    solicitud.estatus,
    };
    Object.entries(campos).forEach(([id, val]) => {
      const el = document.getElementById(id);
      if (el) el.textContent = val ?? '—';
    });

    // Badge de estatus
    const badgeEl = document.getElementById('det-badge-estatus');
    if (badgeEl) {
      const badgeClass = ESTATUS_BADGE[solicitud.estatus] || 'badge-revision';
      badgeEl.className = `badge-estatus ${badgeClass}`;
      badgeEl.textContent = solicitud.estatus;
    }

    // Deducción (solo si existe)
    this.renderDeduccion(solicitud.deduccion);

    // Historial de la solicitud (quien lo hizo: nombre del admin, o "Alumno")
    const historialEl = document.getElementById('historial-lista');
    if (historialEl) {
      if (solicitud.historial && solicitud.historial.length) {
        historialEl.innerHTML = solicitud.historial.map(h => {
          const { clase, icon } = ESTATUS_ICONO[h.estatus] || { clase: 'pendiente', icon: 'fa-info' };
          const comentarioHtml = h.comentario
            ? `<div class="hist-comentario"><i class="fa fa-quote-left"></i> ${Html.escapar(h.comentario)}</div>`
            : '';
          return `
            <li class="historial-item">
              <div class="historial-icono ${Html.escapar(clase)}"><i class="fa ${Html.escapar(icon)}"></i></div>
              <div class="historial-info">
                <div class="hist-titulo">${Html.escapar(h.estatusNombre || h.estatus)}</div>
                <div class="hist-meta">${Html.escapar(this.formatearFecha(h.fecha))} · ${Html.escapar(h.administrador || 'Alumno')}</div>
                ${comentarioHtml}
              </div>
            </li>`;
        }).join('');
      } else {
        historialEl.innerHTML = '<li class="historial-vacio">Sin historial registrado.</li>';
      }
    }

    // Acciones disponibles segun el estatus
    this.ajustarAcciones(solicitud);
  },

  renderDeduccion(deduccion) {
    const bloque = document.getElementById('det-deduccion');
    const contenido = document.getElementById('det-deduccion-contenido');
    if (!bloque || !contenido) return;

    if (!deduccion) {
      bloque.hidden = true;
      return;
    }
    bloque.hidden = false;

    const monto = (valor) => valor == null ? '—' : `$${Number(valor).toLocaleString('es-MX', { minimumFractionDigits: 2 })}`;
    contenido.innerHTML = `
      <div class="detalle-campo">
        <label>Monto de pago</label>
        <div class="valor">${monto(deduccion.montoPago)}</div>
      </div>
      <div class="detalle-campo">
        <label>Deducción</label>
        <div class="valor">${monto(deduccion.montoDeduccion)}</div>
      </div>
      <div class="detalle-campo">
        <label>Monto final</label>
        <div class="valor">${monto(deduccion.montoFinal)}</div>
      </div>
      <div class="detalle-campo">
        <label>Estatus deducción</label>
        <div class="valor">${Html.escapar(deduccion.estatus || '—')}</div>
      </div>`;
  },

  /* ── Eventos de botones y formularios dentro de modales (sin inline handlers) ── */
  iniciarEventosModales() {
    document.querySelectorAll('[data-cerrar-detalle]').forEach(btn => {
      btn.addEventListener('click', () => this.cerrarDetalle());
    });
    document.getElementById('btn-aprobar')?.addEventListener('click', () => this.aprobar());
    document.getElementById('btn-rechazar')?.addEventListener('click', () => this.solicitarCorreccion());
    document.getElementById('btn-reautorizar')?.addEventListener('click', () => this.reautorizar());
    document.getElementById('btn-cancelar-deduccion')?.addEventListener('click', () => this.cancelarDeduccion());
    document.getElementById('btn-aplicar-deduccion')?.addEventListener('click', () => this.aplicarDeduccion());
    document.getElementById('agregar-comentario')?.addEventListener('change', () => this.alternarComentario());
    document.getElementById('comentario-correccion')?.addEventListener('input', () => this.actualizarVistaCorreo());
    document.getElementById('confirmar-decision-correo')?.addEventListener('click', () => this.confirmarDecisionCorreo());
    document.getElementById('btn-volver-decision-correo')?.addEventListener('click', () => this.cerrarEditorCorreo());
    document.getElementById('form-notificacion')?.addEventListener('submit', (e) => Notificaciones.enviarManual(e));
  },

  /* ── Muestra u oculta botones según el estatus de la solicitud ── */
  aprobar() { this.prepararCorreo('Autorizado', 'ejecutarAprobacion'); },
  reautorizar() { this.prepararCorreo('Autorizado', 'ejecutarReautorizacion'); },
  solicitarCorreccion() { this.prepararCorreo('Rechazado', 'ejecutarRechazo'); },
  cancelarDeduccion() { this.prepararCorreo('Cancelado', 'ejecutarCancelacion'); },
  aplicarDeduccion() { this.prepararCorreo('Aplicado', 'ejecutarAplicacion'); },

  prepararCorreo(estado, accion) {
    if (!this._solicitudActual) return;
    this._decisionCorreo = { estado, accion };
    const obligatorio = estado === 'Rechazado';
    const casilla = document.getElementById('agregar-comentario');
    casilla.checked = obligatorio;
    casilla.disabled = obligatorio;
    document.getElementById('titulo-decision-correo').textContent =
      estado === 'Aplicado' ? 'Aplicar deducción · cambio definitivo' : 'Confirmar ' + estado.toLowerCase();
    document.getElementById('editor-decision-correo').hidden = false;
    document.querySelector('.decision-acciones').hidden = true;
    this.alternarComentario();
    document.getElementById('editor-decision-correo').scrollIntoView({ behavior: 'smooth', block: 'start' });
  },

  alternarComentario() {
    const mostrar = document.getElementById('agregar-comentario').checked;
    const campo = document.getElementById('comentario-correccion');
    document.getElementById('grupo-comentario').hidden = !mostrar;
    document.getElementById('grupo-comentario').style.display = mostrar ? '' : 'none';
    campo.required = this._decisionCorreo?.estado === 'Rechazado';
    if (!mostrar) campo.value = '';
    this.actualizarVistaCorreo();
  },

  cerrarEditorCorreo() {
    this._decisionCorreo = null;
    document.getElementById('editor-decision-correo').hidden = true;
    document.querySelector('.decision-acciones').hidden = false;
    document.getElementById('comentario-correccion').value = '';
  },

  async confirmarDecisionCorreo() {
    const decision = this._decisionCorreo;
    if (!decision) return;
    const comentario = document.getElementById('comentario-correccion');
    if (decision.estado === 'Rechazado' && !comentario.value.trim()) {
      comentario.setCustomValidity('Indique el motivo del rechazo.');
      comentario.reportValidity();
      comentario.setCustomValidity('');
      return;
    }
    const boton = document.getElementById('confirmar-decision-correo');
    boton.disabled = true;
    try {
      await this[decision.accion]();
    } finally {
      boton.disabled = false;
    }
  },

  actualizarVistaCorreo() {
    const estado = this._decisionCorreo?.estado || 'Autorizado';
    const textos = {
      Autorizado: 'Tu solicitud fue aprobada.',
      Rechazado: 'Tu solicitud fue rechazada.',
      Aplicado: 'La deducción de tu solicitud fue aplicada.',
      Cancelado: 'La deducción de tu solicitud fue cancelada.',
    };
    const comentario = document.getElementById('comentario-correccion').value.trim();
    document.getElementById('correo-asunto').textContent =
      'Solicitud ' + this._solicitudActual.idSolicitud;
    document.getElementById('correo-estado').textContent = estado;
    document.getElementById('correo-texto').textContent = textos[estado];
    document.getElementById('correo-comentario').textContent = comentario;
    document.getElementById('correo-comentario-bloque').hidden = !comentario;
  },

  ajustarAcciones(solicitud) {
    const botones = {
      aprobar:     document.getElementById('btn-aprobar'),
      rechazar:    document.getElementById('btn-rechazar'),
      reautorizar: document.getElementById('btn-reautorizar'),
      cancelar:    document.getElementById('btn-cancelar-deduccion'),
      aplicar:     document.getElementById('btn-aplicar-deduccion'),
    };
    const grupoComentario = document.getElementById('grupo-comentario');
    const areaDecision = document.getElementById('area-decision');

    this.cerrarEditorCorreo();
    const esPendiente  = solicitud.estatus === 'PENDIENTE';
    const esCancelado  = solicitud.estatus === 'CANCELADO';
    const esAutorizado = solicitud.estatus === 'AUTORIZADO' && solicitud.deduccion;

    Object.values(botones).forEach(b => { if (b) b.style.display = 'none'; });
    if (grupoComentario) grupoComentario.style.display = 'none';
    if (areaDecision) areaDecision.style.display = (esPendiente || esCancelado || esAutorizado) ? '' : 'none';

    if (esPendiente) {
      if (botones.aprobar)  botones.aprobar.style.display = '';
      if (botones.rechazar) botones.rechazar.style.display = '';

    }
    if (esCancelado) {
      if (botones.reautorizar) botones.reautorizar.style.display = '';

    }
    if (esAutorizado) {
      if (botones.cancelar) botones.cancelar.style.display = '';
      if (botones.aplicar)  botones.aplicar.style.display = '';

    }
  },

  /* ── Cierra el detalle limpiando el estado para no arrastrar datos viejos ── */
  cerrarDetalle() {
    Modal.cerrar('modal-detalle');
    this._solicitudActual = null;
    const comentario = document.getElementById('comentario-correccion');
    if (comentario) comentario.value = '';
    Alerta.limpiar('alerta-decision');
  },

  /* ── Decisión: Aprobar (PENDIENTE) o Reautorizar (CANCELADO) ──
     Ambas pegan al mismo endpoint /aprobar: es la misma operación de BD
     (autorizar_deduccion), solo cambia el texto mostrado en el panel.
     El comentario es opcional para estas dos operaciones. ── */
  async ejecutarAprobacion() {
    return this._autorizarComun(
      '¿Confirma la aprobación de esta solicitud? Se autorizará la deducción.',
      'Solicitud aprobada correctamente.'
    );
  },

  async ejecutarReautorizacion() {
    return this._autorizarComun(
      '¿Confirma reautorizar la deducción de esta solicitud cancelada?',
      'Deducción reautorizada correctamente.'
    );
  },

  async _autorizarComun(mensajeConfirm, mensajeExito) {
    if (!this._solicitudActual) return;
    // El comentario es opcional al aprobar o reautorizar; solo rechazar lo exige.
    const comentario = document.getElementById('comentario-correccion')?.value?.trim() || null;
    const idActual = this._solicitudActual.idSolicitud;
    const body = { comentario };
    Spinner.show();
    try {
      await Http.put(`/solicitudes/${idActual}/aprobar`, body);
      this.cerrarDetalle();
      Alerta.mostrar('alerta-bandeja', 'exito', mensajeExito, 4000);
      await this.cargarSolicitudes();
      await this.cargarEstadisticas();
    } catch (err) {
      Alerta.mostrar('alerta-decision', 'error', err.message || 'No se pudo completar la operación.');
    } finally {
      Spinner.hide();
    }
  },

  /* ── Decisión: Rechazar / solicitar corrección ── */
  async ejecutarRechazo() {
    if (!this._solicitudActual) return;
    const comentario = document.getElementById('comentario-correccion')?.value?.trim();
    if (!comentario) {
      Alerta.mostrar('alerta-decision', 'advertencia', 'Escriba un comentario indicando la corrección requerida.');
      return;
    }

    const idActual = this._solicitudActual.idSolicitud;
    Spinner.show();
    try {
      await Http.put(`/solicitudes/${idActual}/rechazar`, { comentario });
      this.cerrarDetalle();
      Alerta.mostrar('alerta-bandeja', 'advertencia', 'Solicitud rechazada / devuelta para corrección.', 4000);
      await this.cargarSolicitudes();
      await this.cargarEstadisticas();
    } catch (err) {
      Alerta.mostrar('alerta-decision', 'error', err.message || 'No se pudo registrar la corrección.');
    } finally {
      Spinner.hide();
    }
  },

  /* ── Decisión: Cancelar deducción de una solicitud AUTORIZADA ──
     El comentario es opcional; si se escribe, se guarda en el historial. ── */
  async ejecutarCancelacion() {
    if (!this._solicitudActual || !this._solicitudActual.deduccion) return;
    const comentario = document.getElementById('comentario-correccion')?.value?.trim() || null;


    const idDeduccion = this._solicitudActual.deduccion.idDeduccion;
    Spinner.show();
    try {
      await Http.put(`/deducciones/${idDeduccion}/cancelar`, { comentario });
      this.cerrarDetalle();
      Alerta.mostrar('alerta-bandeja', 'advertencia', 'Deducción cancelada correctamente.', 4000);
      await this.cargarSolicitudes();
      await this.cargarEstadisticas();
    } catch (err) {
      Alerta.mostrar('alerta-decision', 'error', err.message || 'No se pudo cancelar la deducción.');
    } finally {
      Spinner.hide();
    }
  },

  /* ── Decisión: Aplicar una deducción autorizada (estado definitivo) ──
     El comentario opcional se incluye en el aviso por correo. ── */
  async ejecutarAplicacion() {
    if (!this._solicitudActual || !this._solicitudActual.deduccion) return;


    const idDeduccion = this._solicitudActual.deduccion.idDeduccion;
    Spinner.show();
    try {
      await Http.put(`/deducciones/${idDeduccion}/aplicar`, {
        comentario: document.getElementById('comentario-correccion')?.value?.trim() || null,
      });
      this.cerrarDetalle();
      Alerta.mostrar('alerta-bandeja', 'exito', 'Deducción aplicada correctamente.', 4000);
      await this.cargarSolicitudes();
      await this.cargarEstadisticas();
    } catch (err) {
      Alerta.mostrar('alerta-decision', 'error', err.message || 'No se pudo aplicar la deducción.');
    } finally {
      Spinner.hide();
    }
  },
};

/* ─────────────────────────────────────────
   MÓDULO: ENVÍO DE NOTIFICACIONES
   Equipo 6  (disparado desde backend;
              aquí solo se registra manualmente si es necesario)
───────────────────────────────────────── */
const Notificaciones = {
  async enviarManual(event) {
    event.preventDefault();
    const solicitud = PanelAdmin._solicitudActual;
    if (!solicitud) return;

    const form = event.currentTarget;
    const mensaje = form.elements.mensaje;
    const boton = form.querySelector('button');
    const estado = form.querySelector('[role="status"]');
    boton.disabled = true;
    estado.textContent = 'Enviando aviso…';
    try {
      await Http.post('/notificaciones', { idSolicitud: solicitud.idSolicitud, mensaje: mensaje.value });
      if (PanelAdmin._solicitudActual === solicitud) {
        form.reset();
        estado.textContent = 'Aviso enviado al servidor de correo.';
      }
    } catch (err) {
      if (PanelAdmin._solicitudActual === solicitud) {
        estado.textContent = err.payload?.mensaje || 'No se pudo enviar el aviso.';
      }
    } finally {
      boton.disabled = false;
    }
  },
};

/* ─────────────────────────────────────────
   MÓDULO: GESTIÓN DE CONVOCATORIAS
   Equipo 4
───────────────────────────────────────── */
const ConvocatoriasAdmin = {
  async listar() {
    const respuesta = await Http.get('/admin/convocatorias');
    if (!Array.isArray(respuesta.convocatorias)) {
      throw new Error('La respuesta del listado de convocatorias no es válida.');
    }
    return respuesta.convocatorias;
  },

  async consultar(idConvocatoria) {
    const id = Number(idConvocatoria);
    if (!Number.isSafeInteger(id) || id <= 0) {
      throw new Error('Seleccione un identificador de convocatoria válido.');
    }
    return Http.get(`/admin/convocatorias/${id}`);
  },
};

/* ─────────────────────────────────────────
   INICIALIZACIÓN AUTOMÁTICA
───────────────────────────────────────── */
document.addEventListener('DOMContentLoaded', () => {
  Modal.iniciarCierre();

  const pagina = document.body.id;
  switch (pagina) {
    case 'pagina-login-admin':  LoginAdmin.init();  break;
    case 'pagina-panel-admin':  PanelAdmin.init();  break;
    case 'pagina-convocatorias-admin':
      (async () => {
        const btnGuardar = document.getElementById('btnGuardarConvocatoria');
        if (btnGuardar) {
          btnGuardar.disabled = true;
          btnGuardar.textContent = 'Verificando sesión...';
        }
        const autorizado = await AuthSession.requireRole('ADMIN', 'login.html', '/solicitante/login.html');
        if (!autorizado) return;
        AuthSession.bindLogout('login.html', 'alertaValidacion');
        if (btnGuardar) {
          btnGuardar.disabled = false;
          const iconEl = document.createElement('i');
          iconEl.className = 'fa-solid fa-floppy-disk';
          iconEl.setAttribute('aria-hidden', 'true');
          btnGuardar.replaceChildren(iconEl, document.createTextNode(' Guardar convocatoria'));
        }
      })();
      break;
  }
});
