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
      const error = new Error(data?.error || 'No se pudo completar la solicitud.');
      error.status = resp.status;
      error.payload = data;
      throw error;
    }
    return data;
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
    Modal.iniciarCierre();
  },

  /* ── Catálogo de convocatorias y selección inicial ── */
  async cargarConvocatorias() {
    const select = document.getElementById('filtro-convocatoria');
    try {
      const convocatorias = await Http.get('/convocatorias');
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
      case 'solicitante':        return (solicitud.solicitante || solicitud.nombreSolicitante || '').toUpperCase();
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
      tbody.innerHTML = `<tr><td colspan="7" class="text-center" style="padding:24px;color:var(--itt-texto-claro)">
        <i class="fa fa-inbox" style="font-size:2rem;display:block;margin-bottom:8px;"></i>
        ${motivo}</td></tr>`;
      return;
    }

    tbody.innerHTML = solicitudes.map(s => {
      const badgeClass = ESTATUS_BADGE[s.estatus] || 'badge-revision';
      const actualizado = this.formatearFecha(s.fechaActualizacion ?? s.fecha_actualizacion);

      return `<tr>
        <td>${s.idSolicitud}</td>
        <td>${s.solicitante || s.nombreSolicitante || '—'}</td>
        <td>${s.tipoSolicitante   || '—'}</td>
        <td>${s.tipoSolicitud      || '—'}</td>
        <td>${actualizado}</td>
        <td><span class="badge-estatus ${badgeClass}">${s.estatus}</span></td>
        <td class="acciones-celda">
          <button class="btn-itt btn-primario btn-accion-icono" onclick="PanelAdmin.verDetalle(${s.idSolicitud})" title="Ver detalle">
            <i class="fa fa-eye"></i>
          </button>
          <button class="btn-itt btn-secundario btn-accion-icono" onclick="PanelAdmin.verRespuestas(${s.idSolicitud})" title="Ver respuestas del formulario">
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
    if (solicitante) solicitante.textContent = solicitud.solicitante || solicitud.nombreSolicitante || '—';

    const lista = document.getElementById('lista-respuestas');
    if (!lista) return;
    if (solicitud.respuestas && solicitud.respuestas.length) {
      lista.innerHTML = solicitud.respuestas.map(r =>
        `<div class="detalle-campo">
          <label>Pregunta #${r.idPregunta}</label>
          <div class="valor">${r.respuesta || '—'}</div>
        </div>`
      ).join('');
    } else {
      lista.innerHTML =
        '<p style="color:var(--itt-texto-claro);font-style:italic;">Sin respuestas registradas.</p>';
    }
  },

  renderDetalle(solicitud) {
    this._solicitudActual = solicitud;

    // Datos generales
    const campos = {
      'det-folio':      solicitud.idSolicitud,
      'det-nombre':     solicitud.solicitante || solicitud.nombreSolicitante,
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

    // Historial (pendiente de endpoint en Fase 3)
    const historialEl = document.getElementById('historial-lista');
    if (historialEl) {
      if (solicitud.historial && solicitud.historial.length) {
        historialEl.innerHTML = solicitud.historial.map(h => {
          const { clase, icon } = ESTATUS_ICONO[h.estatus] || { clase: 'pendiente', icon: 'fa-info' };
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
      } else {
        historialEl.innerHTML =
          '<li style="color:var(--itt-texto-claro);font-style:italic;padding:8px 0;">Sin historial registrado.</li>';
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
      bloque.style.display = 'none';
      return;
    }
    bloque.style.display = '';

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
        <div class="valor">${deduccion.estatus || '—'}</div>
      </div>`;
  },

  /* ── Muestra u oculta botones según el estatus de la solicitud ── */
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

    const esPendiente  = solicitud.estatus === 'PENDIENTE';
    const esCancelado  = solicitud.estatus === 'CANCELADO';
    const esAutorizado = solicitud.estatus === 'AUTORIZADO' && solicitud.deduccion;

    Object.values(botones).forEach(b => { if (b) b.style.display = 'none'; });
    if (grupoComentario) grupoComentario.style.display = 'none';
    if (areaDecision) areaDecision.style.display = (esPendiente || esCancelado || esAutorizado) ? '' : 'none';

    if (esPendiente) {
      if (botones.aprobar)  botones.aprobar.style.display = '';
      if (botones.rechazar) botones.rechazar.style.display = '';
      if (grupoComentario)  grupoComentario.style.display = '';
    }
    if (esCancelado) {
      if (botones.reautorizar) botones.reautorizar.style.display = '';
      if (grupoComentario)     grupoComentario.style.display = '';
    }
    if (esAutorizado) {
      if (botones.cancelar) botones.cancelar.style.display = '';
      if (botones.aplicar)  botones.aplicar.style.display = '';
      if (grupoComentario)  grupoComentario.style.display = '';
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
     (autorizar_deduccion), solo cambia el texto mostrado en el panel. ── */
  async aprobar() {
    return this._autorizarComun(
      '¿Confirma la aprobación de esta solicitud? Se autorizará la deducción.',
      'Solicitud aprobada correctamente.'
    );
  },

  async reautorizar() {
    return this._autorizarComun(
      '¿Confirma reautorizar la deducción de esta solicitud cancelada?',
      'Deducción reautorizada correctamente.'
    );
  },

  async _autorizarComun(mensajeConfirm, mensajeExito) {
    if (!this._solicitudActual) return;
    const comentario = document.getElementById('comentario-correccion')?.value?.trim();
    if (!comentario) {
      Alerta.mostrar('alerta-decision', 'advertencia', 'Escriba un comentario para continuar.');
      return;
    }
    if (!confirm(mensajeConfirm)) return;

    const idActual = this._solicitudActual.idSolicitud;
    Spinner.show();
    try {
      await Http.put(`/solicitudes/${idActual}/aprobar`, { comentario });
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
  async solicitarCorreccion() {
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

  /* ── Decisión: Cancelar deducción de una solicitud AUTORIZADA ── */
  async cancelarDeduccion() {
    if (!this._solicitudActual || !this._solicitudActual.deduccion) return;
    const comentario = document.getElementById('comentario-correccion')?.value?.trim();
    if (!comentario) {
      Alerta.mostrar('alerta-decision', 'advertencia', 'Escriba un comentario para cancelar la deducción.');
      return;
    }
    if (!confirm('¿Confirma cancelar la deducción autorizada? La solicitud pasará a CANCELADO.')) return;

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
     No exige comentario: aplicar_deduccion no lo recibe en la BD. ── */
  async aplicarDeduccion() {
    if (!this._solicitudActual || !this._solicitudActual.deduccion) return;
    if (!confirm('¿Confirma aplicar esta deducción? La solicitud pasará a APLICADO y no podrá modificarse.')) return;

    const idDeduccion = this._solicitudActual.deduccion.idDeduccion;
    Spinner.show();
    try {
      await Http.put(`/deducciones/${idDeduccion}/aplicar`, {});
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
