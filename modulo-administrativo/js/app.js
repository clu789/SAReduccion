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
    const headers = { 'Content-Type': 'application/json' };
    if (endpoint === '/auth/login' || endpoint === '/auth/logout') {
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
  async requireRole(expectedRole, loginPath, otherProfileLoginPath) {
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
  iniciarCierre() {
    document.querySelectorAll('.modal-overlay').forEach(overlay => {
      overlay.addEventListener('click', (e) => {
        if (e.target === overlay) overlay.classList.remove('activo');
      });
    });
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

    form.addEventListener('submit', async (e) => {
      e.preventDefault();

      const usuario  = document.getElementById('usuario-admin');
      const password = document.getElementById('password-admin');

      let valido = true;
      if (!usuario || !usuario.value.trim()) {
        usuario?.classList.add('error');
        valido = false;
      } else usuario?.classList.remove('error');

      if (!password || !password.value) {
        password?.classList.add('error');
        valido = false;
      } else password?.classList.remove('error');

      if (!valido) return;

      Spinner.show();
      try {
        await Http.authSession();
        const username = usuario.value.trim().toUpperCase();
        const session = await Http.post('/auth/login', {
          username,
          password: password.value,
        });
        if (session.role !== 'ADMIN' || session.identity !== username) {
          await Http.post('/auth/logout', {});
          throw new Error('Perfil no autorizado');
        }
        password.value = '';
        window.location.href = 'panel-admin.html';
      } catch (err) {
        Alerta.mostrar('alerta-login-admin', 'error', mensajeLogin(err));
      } finally {
        password.value = '';
        Spinner.hide();
      }
    });
  },
};

/* ─────────────────────────────────────────
   MÓDULO: PANEL DE ADMINISTRACIÓN / BANDEJA
   Equipo 2
───────────────────────────────────────── */
const PanelAdmin = {
  solicitudes: [],      // Datos cargados desde la API
  filtroActual: 'todos',

  async init() {
    const bandeja = document.getElementById('tabla-solicitudes');
    if (!bandeja) return;
    if (!await AuthSession.requireRole('ADMIN', 'login.html', '/solicitante/login.html')) return;
    AuthSession.bindLogout('login.html', 'alerta-admin');

    await this.cargarEstadisticas();
    await this.cargarSolicitudes();
    this.iniciarFiltros();
    Modal.iniciarCierre();
  },

  /* ── Estadísticas de la barra superior ── */
  async cargarEstadisticas() {
    try {
      const stats = await Http.get('/solicitudes/estadisticas');
      this.actualizarContador('stat-total',      stats.total      ?? 0);
      this.actualizarContador('stat-pendiente',  stats.pendiente  ?? 0);
      this.actualizarContador('stat-correccion', stats.correccion ?? 0);
      this.actualizarContador('stat-aprobado',   stats.aprobado   ?? 0);
    } catch (err) {
      console.warn('No se pudieron cargar estadísticas:', err.message);
    }
  },

  actualizarContador(id, valor) {
    const el = document.getElementById(id);
    if (el) el.textContent = valor;
  },

  /* ── Carga la lista de solicitudes ── */
  async cargarSolicitudes(estatus = null) {
    Spinner.show();
    try {
      const endpoint = estatus && estatus !== 'todos'
        ? `/solicitudes?estatus=${estatus}`
        : '/solicitudes';

      this.solicitudes = await Http.get(endpoint);
      this.renderTabla(this.solicitudes);
    } catch (err) {
      Alerta.mostrar('alerta-bandeja', 'error', 'No se pudieron cargar las solicitudes.');
    } finally {
      Spinner.hide();
    }
  },

  /* ── Renderiza la tabla de solicitudes ── */
  renderTabla(solicitudes) {
    const tbody = document.querySelector('#tabla-solicitudes tbody');
    if (!tbody) return;

    if (!solicitudes.length) {
      tbody.innerHTML = `<tr><td colspan="6" class="text-center" style="padding:24px;color:var(--itt-texto-claro)">
        <i class="fa fa-inbox" style="font-size:2rem;display:block;margin-bottom:8px;"></i>
        No hay solicitudes que mostrar.</td></tr>`;
      return;
    }

    tbody.innerHTML = solicitudes.map(s => {
      const badgeClass = {
        PENDIENTE:  'badge-pendiente',
        CORRECCION: 'badge-correccion',
        APROBADO:   'badge-aprobado',
        RECHAZADO:  'badge-rechazado',
      }[s.estatus] || 'badge-revision';

      return `<tr>
        <td>${s.idSolicitud}</td>
        <td>${s.nombreSolicitante || '—'}</td>
        <td>${s.tipoSolicitante   || '—'}</td>
        <td>${s.fechaSolicitud    || '—'}</td>
        <td><span class="badge-estatus ${badgeClass}">${s.estatus}</span></td>
        <td>
          <button class="btn-itt btn-primario" onclick="PanelAdmin.verDetalle(${s.idSolicitud})" title="Ver detalle">
            <i class="fa fa-eye"></i>
          </button>
        </td>
      </tr>`;
    }).join('');
  },

  /* ── Chips de filtro ── */
  iniciarFiltros() {
    document.querySelectorAll('.filtro-chip').forEach(chip => {
      chip.addEventListener('click', () => {
        document.querySelectorAll('.filtro-chip').forEach(c => c.classList.remove('activo'));
        chip.classList.add('activo');
        const estatus = chip.dataset.estatus;
        this.filtroActual = estatus;
        this.cargarSolicitudes(estatus);
      });
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
      Alerta.mostrar('alerta-bandeja', 'error', 'No se pudo cargar el detalle de la solicitud.');
    } finally {
      Spinner.hide();
    }
  },

  renderDetalle(solicitud) {
    // Datos generales
    const campos = {
      'det-folio':      solicitud.idSolicitud,
      'det-nombre':     solicitud.nombreSolicitante,
      'det-tipo':       solicitud.tipoSolicitante,
      'det-fecha':      solicitud.fechaSolicitud,
      'det-estatus':    solicitud.estatus,
    };
    Object.entries(campos).forEach(([id, val]) => {
      const el = document.getElementById(id);
      if (el) el.textContent = val ?? '—';
    });

    // Badge de estatus
    const badgeEl = document.getElementById('det-badge-estatus');
    if (badgeEl) {
      const badgeClass = {
        PENDIENTE:  'badge-pendiente',
        CORRECCION: 'badge-correccion',
        APROBADO:   'badge-aprobado',
        RECHAZADO:  'badge-rechazado',
      }[solicitud.estatus] || 'badge-revision';
      badgeEl.className = `badge-estatus ${badgeClass}`;
      badgeEl.textContent = solicitud.estatus;
    }

    // Respuestas del formulario
    const listaRespuestas = document.getElementById('lista-respuestas');
    if (listaRespuestas && solicitud.respuestas) {
      listaRespuestas.innerHTML = solicitud.respuestas.map(r =>
        `<div class="detalle-campo">
          <label>${r.pregunta}</label>
          <div class="valor">${r.respuesta || '—'}</div>
        </div>`
      ).join('');
    }

    // Historial
    const historialEl = document.getElementById('historial-lista');
    if (historialEl && solicitud.historial) {
      const iconosHistorial = {
        PENDIENTE:  { clase: 'pendiente',  icon: 'fa-clock-o' },
        APROBADO:   { clase: 'aprobado',   icon: 'fa-check' },
        RECHAZADO:  { clase: 'rechazado',  icon: 'fa-times' },
        CORRECCION: { clase: 'correccion', icon: 'fa-pencil' },
      };

      historialEl.innerHTML = solicitud.historial.map(h => {
        const { clase, icon } = iconosHistorial[h.estatus] || { clase: 'pendiente', icon: 'fa-info' };
        const comentarioHtml = h.comentario
          ? `<div class="hist-comentario"><i class="fa fa-quote-left"></i> ${h.comentario}</div>`
          : '';
        return `
          <li class="historial-item">
            <div class="historial-icono ${clase}"><i class="fa ${icon}"></i></div>
            <div class="historial-info">
              <div class="hist-titulo">${h.estatusNombre || h.estatus}</div>
              <div class="hist-meta">${h.fecha || ''} ${h.administrador ? '· ' + h.administrador : ''}</div>
              ${comentarioHtml}
            </div>
          </li>`;
      }).join('');
    }

    // Guardar ID actual para las acciones de decisión
    this._idActual = solicitud.idSolicitud;
  },

  /* ── Decisión: Aprobar ── */
  async aprobar() {
    if (!this._idActual) return;
    if (!confirm('¿Confirma la aprobación de esta solicitud?')) return;

    Spinner.show();
    try {
      await Http.put(`/solicitudes/${this._idActual}/aprobar`, {});
      Alerta.mostrar('alerta-bandeja', 'exito', 'Solicitud aprobada correctamente.', 4000);
      Modal.cerrar('modal-detalle');
      await this.cargarSolicitudes(this.filtroActual);
      await this.cargarEstadisticas();
    } catch (err) {
      Alerta.mostrar('alerta-bandeja', 'error', 'No se pudo aprobar la solicitud.');
    } finally {
      Spinner.hide();
    }
  },

  /* ── Decisión: Enviar a corrección ── */
  async solicitarCorreccion() {
    if (!this._idActual) return;
    const comentario = document.getElementById('comentario-correccion')?.value?.trim();
    if (!comentario) {
      Alerta.mostrar('alerta-decision', 'advertencia', 'Escriba un comentario indicando la corrección requerida.');
      return;
    }

    Spinner.show();
    try {
      await Http.put(`/solicitudes/${this._idActual}/correccion`, { comentario });
      Alerta.mostrar('alerta-bandeja', 'advertencia', 'Solicitud devuelta para corrección.', 4000);
      Modal.cerrar('modal-detalle');
      await this.cargarSolicitudes(this.filtroActual);
      await this.cargarEstadisticas();
    } catch (err) {
      Alerta.mostrar('alerta-decision', 'error', 'No se pudo registrar la corrección.');
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
  async enviarManual(idSolicitud, mensaje) {
    try {
      await Http.post(`/notificaciones`, { idSolicitud, mensaje });
      Alerta.mostrar('alerta-bandeja', 'exito', 'Notificación enviada.', 3000);
    } catch (err) {
      Alerta.mostrar('alerta-bandeja', 'error', 'Error al enviar la notificación.');
    }
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
  }
});
