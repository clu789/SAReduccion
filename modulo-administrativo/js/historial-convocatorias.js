
'use strict';

/* Etiquetas de las cuatro fechas, en el orden de la convocatoria */
const CAMPOS_FECHA = [
  { clave: 'inicio',       nombre: 'Inicio de solicitudes',  anterior: 'inicioAnterior',       nuevo: 'inicioNuevo' },
  { clave: 'fin',          nombre: 'Fin de solicitudes',     anterior: 'finAnterior',          nuevo: 'finNuevo' },
  { clave: 'autorizacion', nombre: 'Límite de autorización', anterior: 'autorizacionAnterior', nuevo: 'autorizacionNueva' },
  { clave: 'aplicacion',   nombre: 'Límite de aplicación',   anterior: 'aplicacionAnterior',   nuevo: 'aplicacionNueva' },
];

const PERIODOS = { 1: 'Agosto-Diciembre', 2: 'Enero-Junio' };

/* ─────────────────────────────────────────
   UTILIDADES DE FECHA
───────────────────────────────────────── */
const Fecha = {
  /* 'YYYY-MM-DD' o 'YYYY-MM-DDTHH:mm:ss' → Date local (sin desfase de zona) */
  parse(valor) {
    if (!valor) return null;
    const [f, h = '00:00:00'] = String(valor).replace(' ', 'T').split('T');
    const [a, m, d] = f.split('-').map(Number);
    const [hh, mm, ss] = h.split(':').map(Number);
    if (!a || !m || !d) return null;
    return new Date(a, m - 1, d, hh || 0, mm || 0, ss || 0);
  },
  corta(valor) {
    const d = this.parse(valor);
    if (!d) return '—';
    return d.toLocaleDateString('es-MX', { day: '2-digit', month: '2-digit', year: 'numeric' });
  },
  larga(valor) {
    const d = this.parse(valor);
    if (!d) return '—';
    return d.toLocaleDateString('es-MX', { day: '2-digit', month: 'short', year: 'numeric' });
  },
  conHora(valor) {
    const d = this.parse(valor);
    if (!d) return '—';
    return `${this.corta(valor)}, ${d.toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' })}`;
  },
  difDias(anterior, nuevo) {
    const previo = this.parse(anterior);
    const posterior = this.parse(nuevo);
    if (!previo || !posterior) return 0;
    const ms = posterior - previo;
    return Math.round(ms / 86400000);
  },
  soloDia(valor) {
    return String(valor || '').slice(0, 10);
  },
};

/* ─────────────────────────────────────────
   MÓDULO PRINCIPAL
───────────────────────────────────────── */
const HistorialConvocatorias = {
  convocatorias: [],
  registros: [],
  seleccionada: null,

  async init() {
    if (!await AuthSession.requireRole('ADMIN', 'login.html', '/solicitante/login.html')) return;
    AuthSession.bindLogout('login.html', 'alerta-historial');
    this.enlazarEventos();
    await this.cargarConvocatorias();
  },

  enlazarEventos() {
    document.getElementById('filtro-convocatoria')
      .addEventListener('change', (e) => this.cargarHistorial(Number(e.target.value)));
    ['filtro-administrador', 'filtro-campo', 'filtro-desde', 'filtro-hasta'].forEach((id) => {
      document.getElementById(id).addEventListener('change', () => this.renderTabla());
    });
    document.getElementById('btn-limpiar-filtros').addEventListener('click', () => this.limpiarFiltros());
    document.querySelector('#tabla-historial tbody').addEventListener('click', (e) => {
      const btn = e.target.closest('button[data-historial-id]');
      if (!btn) return;
      this.verDetalle(Number(btn.dataset.historialId));
    });
    document.getElementById('modal-cambio').addEventListener('click', (e) => {
      if (e.target.id === 'modal-cambio') this.cerrarDetalle();
    });
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape') this.cerrarDetalle(); });
  },

  async cargarConvocatorias() {
    const select = document.getElementById('filtro-convocatoria');
    try {
      this.convocatorias = await ConvocatoriasAdmin.listar();
      select.replaceChildren();
      this.convocatorias.forEach(c => select.add(new Option(this.etiquetaConvocatoria(c), String(c.idConvocatoria))));
      select.disabled = !this.convocatorias.length;
      const preferida = this.convocatorias.find(c => c.estado === 'ACTIVA') || this.convocatorias[0];
      if (preferida) {
        select.value = String(preferida.idConvocatoria);
        await this.cargarHistorial(preferida.idConvocatoria);
      } else {
        select.add(new Option('Sin convocatorias', ''));
        this.renderResumen();
        this.renderTabla();
      }
    } catch (err) {
      this.alerta('error', err.message || 'No se pudieron cargar las convocatorias.');
      select.replaceChildren(new Option('No disponibles', ''));
      select.disabled = true;
    }
  },

  async cargarHistorial(idConvocatoria) {
    const id = Number(idConvocatoria);
    this.seleccionada = this.convocatorias.find(c => Number(c.idConvocatoria) === id) || null;
    const version = this.versionCarga = (this.versionCarga || 0) + 1;
    this.spinner(true);
    this.registros = [];
    this.alerta('', '');
    try {
      if (!this.seleccionada) throw new Error('Seleccione una convocatoria válida.');
      const datos = await Http.get(`/admin/convocatorias/${id}/historial`);
      if (version !== this.versionCarga) return;
      if (!Array.isArray(datos)) throw new Error('El historial recibido no tiene el formato esperado.');
      this.registros = datos;
      this.llenarAdministradores();
      this.renderResumen();
      this.renderTabla();
    } catch (err) {
      if (version !== this.versionCarga) return;
      this.llenarAdministradores();
      this.renderResumen();
      this.renderTabla();
      this.alerta('error', err.message || 'No se pudo cargar el historial.');
    } finally {
      if (version === this.versionCarga) this.spinner(false);
    }
  },

  /* ── Render ── */
  etiquetaConvocatoria(c) {
    return `${c.idConvocatoria} · ${PERIODOS[c.periodo] || 'Periodo ' + c.periodo} ${c.anio} · ${this.estadoTexto(c.estado)}`;
  },

  estadoTexto(estado) {
    return ({ ACTIVA: 'Activa', PROXIMA: 'Próxima', CERRADA: 'Cerrada', SIN_DEFINIR: 'Sin definir' })[estado] || 'Sin definir';
  },

  escapar(valor) {
    return String(valor ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
  },

  camposModificados(r) {
    return CAMPOS_FECHA.filter((f) => Fecha.soloDia(r[f.anterior]) !== Fecha.soloDia(r[f.nuevo]));
  },

  llenarAdministradores() {
    const select = document.getElementById('filtro-administrador');
    const admins = new Map();
    this.registros.forEach((r) => admins.set(r.idAdministrador, r.administradorNombre || ''));
    select.replaceChildren(new Option('Todos', ''));
    [...admins].forEach(([id, nombre]) => select.add(new Option(nombre ? `${id} · ${nombre}` : String(id), String(id))));
  },

  renderResumen() {
    const c = this.seleccionada;
    const contenedor = document.getElementById('resumen-fechas');
    if (!c) { contenedor.replaceChildren(); document.getElementById('resumen-titulo').textContent = 'Sin convocatoria'; document.getElementById('resumen-estado').textContent = ''; document.getElementById('resumen-total').textContent = '0'; document.getElementById('resumen-ultima').textContent = '—'; return; }

    document.getElementById('resumen-titulo').textContent =
      `Convocatoria ${c.idConvocatoria} · ${PERIODOS[c.periodo] || 'Periodo ' + c.periodo} ${c.anio}`;
    const estado = document.getElementById('resumen-estado');
    estado.textContent = this.estadoTexto(c.estado);
    estado.className = `hc-estado-conv ${(c.estado || 'SIN_DEFINIR').toLowerCase()}`;

    const vigentes = {
      inicio: c.fechaInicio, fin: c.fechaFin,
      autorizacion: c.fechaLimiteAutorizacion, aplicacion: c.fechaLimiteAplicacion,
    };
    contenedor.innerHTML = CAMPOS_FECHA.map((f, i) => {
      const veces = this.registros.filter((r) => this.camposModificados(r).some(x => x.clave === f.clave)).length;
      return `
        <div class="hc-fecha-card">
          <div class="hc-fecha-paso">Paso ${i + 1}</div>
          <div class="hc-fecha-nombre">${f.nombre}</div>
          <div class="hc-fecha-valor">${Fecha.larga(vigentes[f.clave])}</div>
          ${veces ? `<span class="hc-fecha-cambios" title="Veces modificada"><i class="fa-solid fa-pen"></i> ${veces}</span>` : ''}
        </div>`;
    }).join('');

    document.getElementById('resumen-total').textContent = this.registros.length;
    const ultima = [...this.registros].sort((a, b) => Fecha.parse(b.fechaRegistro) - Fecha.parse(a.fechaRegistro))[0];
    document.getElementById('resumen-ultima').textContent =
      ultima ? `${Fecha.conHora(ultima.fechaRegistro)} (${ultima.idAdministrador})` : 'Sin cambios';
  },

  filtrar() {
    const admin = document.getElementById('filtro-administrador').value;
    const campo = document.getElementById('filtro-campo').value;
    const desde = document.getElementById('filtro-desde').value;
    const hasta = document.getElementById('filtro-hasta').value;

    return this.registros.filter((r) => {
      const dia = Fecha.soloDia(r.fechaRegistro);
      if (admin && String(r.idAdministrador) !== admin) return false;
      if (campo && !this.camposModificados(r).some((f) => f.clave === campo)) return false;
      if (desde && dia < desde) return false;
      if (hasta && dia > hasta) return false;
      return true;
    });
  },

  renderTabla() {
    const tbody = document.querySelector('#tabla-historial tbody');
    const filas = this.filtrar();
    document.getElementById('contador-registros').textContent =
      `${filas.length} de ${this.registros.length} registro(s)`;

    if (!this.registros.length) {
      tbody.innerHTML = `
        <tr><td colspan="5" class="hc-vacio">
          <i class="fa-solid fa-calendar"></i>
          Esta convocatoria no tiene modificaciones registradas.<br>
          <small>La creación de la convocatoria no genera registro en el historial.</small>
        </td></tr>`;
      return;
    }
    if (!filas.length) {
      tbody.innerHTML = `
        <tr><td colspan="5" class="hc-vacio">
          <i class="fa-solid fa-filter"></i> Ningún registro coincide con los filtros.
        </td></tr>`;
      return;
    }

    tbody.innerHTML = filas.map((r) => {
      const idHistorial = Number(r.idHistorialConvocatoria);
      const idSeguro = Number.isSafeInteger(idHistorial) ? idHistorial : this.escapar(r.idHistorialConvocatoria);
      const chips = this.camposModificados(r).map((f) => {
        const dif = Fecha.difDias(r[f.anterior], r[f.nuevo]);
        return `<span class="hc-chip">${this.escapar(f.nombre)}<span class="hc-chip-dif">${dif > 0 ? '+' : ''}${dif} d</span></span>`;
      }).join('');
      return `
        <tr>
          <td>${idSeguro}</td>
          <td>${this.escapar(Fecha.conHora(r.fechaRegistro))}</td>
          <td>${r.administradorNombre ? `${this.escapar(r.administradorNombre)}<span class="hc-admin-id">${this.escapar(r.idAdministrador)}</span>` : this.escapar(r.idAdministrador)}</td>
          <td><div class="hc-chips">${chips || '<em style="color:#999;">Sin diferencias</em>'}</div></td>
          <td class="text-center">
            <button type="button" class="btn-blue-itt"
                    title="Ver detalle" data-historial-id="${idSeguro}">
              <i class="fa-solid fa-eye"></i>
            </button>
          </td>
        </tr>`;
    }).join('');
  },

  /* ── Modal de detalle ── */
  verDetalle(idHistorial) {
    const r = this.registros.find((x) => Number(x.idHistorialConvocatoria) === Number(idHistorial));
    if (!r) return;
    const c = this.seleccionada;

    document.getElementById('cambio-id').textContent = `#${r.idHistorialConvocatoria}`;
    document.getElementById('cambio-convocatoria').textContent = c ? this.etiquetaConvocatoria(c) : r.idConvocatoria;
    document.getElementById('cambio-admin').textContent = r.administradorNombre ? `${r.administradorNombre} (${r.idAdministrador})` : r.idAdministrador;
    document.getElementById('cambio-fecha').textContent = Fecha.conHora(r.fechaRegistro);

    document.getElementById('cambio-comparativa').innerHTML = CAMPOS_FECHA.map((f) => {
      const cambio = Fecha.soloDia(r[f.anterior]) !== Fecha.soloDia(r[f.nuevo]);
      const dif = Fecha.difDias(r[f.anterior], r[f.nuevo]);
      return cambio
        ? `<tr class="hc-con-cambio">
             <td>${f.nombre}</td>
             <td class="hc-valor-anterior">${Fecha.corta(r[f.anterior])}</td>
             <td class="hc-flecha"><i class="fa-solid fa-arrow-right"></i></td>
             <td><span class="hc-valor-nuevo">${Fecha.corta(r[f.nuevo])}</span>
                 <span class="hc-dif-dias">${dif > 0 ? '+' : ''}${dif} días</span></td>
           </tr>`
        : `<tr class="hc-sin-cambio">
             <td>${f.nombre}</td>
             <td>${Fecha.corta(r[f.anterior])}</td>
             <td class="hc-flecha">=</td>
             <td>Sin cambio</td>
           </tr>`;
    }).join('');

    document.getElementById('modal-cambio').classList.add('activo');
  },

  cerrarDetalle() {
    document.getElementById('modal-cambio').classList.remove('activo');
  },

  limpiarFiltros() {
    ['filtro-administrador', 'filtro-campo', 'filtro-desde', 'filtro-hasta']
      .forEach((id) => { document.getElementById(id).value = ''; });
    this.renderTabla();
  },

  /* ── Auxiliares visuales (equivalentes a Spinner/Alerta de app.js) ── */
  spinner(mostrar) {
    document.getElementById('spinner-overlay')?.classList.toggle('activo', mostrar);
  },

  alerta(tipo, mensaje) {
    const iconos = { info: 'fa-info-circle', exito: 'fa-check-circle', advertencia: 'fa-exclamation-triangle', error: 'fa-times-circle' };
    const el = document.getElementById('alerta-historial');
    el.replaceChildren();
    if (!mensaje) return;
    const aviso = document.createElement('div');
    aviso.className = `alert alert-${tipo === 'error' ? 'danger' : 'info'}`;
    aviso.textContent = mensaje;
    el.appendChild(aviso);
  },
};

document.addEventListener('DOMContentLoaded', () => HistorialConvocatorias.init());
