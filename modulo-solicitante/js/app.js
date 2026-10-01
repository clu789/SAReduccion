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
    if (!resp.ok) throw new Error(`HTTP ${resp.status}: ${resp.statusText}`);
    return resp.json();
  },

  /**
   * Realiza una petición POST con body JSON.
   * @param {string} endpoint
   * @param {object} body
   */
  async post(endpoint, body) {
    const resp = await fetch(Config.apiBase + endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
      body: JSON.stringify(body),
    });
    if (!resp.ok) throw new Error(`HTTP ${resp.status}: ${resp.statusText}`);
    return resp.json();
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
    return /^\d{8}$/.test(valor.trim());
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
        tabs.forEach(t => t.classList.remove('activo'));
        tab.classList.add('activo');

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

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      if (!this.validar()) return;

      Spinner.show();
      try {
        const tipoActivo = document.querySelector('.tab-tipo.activo')?.dataset.tipo || 'alumno';
        const payload = tipoActivo === 'alumno'
          ? {
              identificador: document.getElementById('numero-control').value.trim(),
              password: document.getElementById('password-alumno').value,
              rol: 'ALUMNO',
            }
          : {
              identificador: document.getElementById('curp-aspirante').value.trim().toUpperCase(),
              password: document.getElementById('folio-aspirante').value.trim(),
              rol: 'ASPIRANTE',
            };

        // const data = await Http.post('/auth/login', payload);
        // Redirigir a panel de estatus tras login exitoso
        window.location.href = 'panel-estatus.html';
      } catch (err) {
        Alerta.mostrar('alerta-login', 'error', 'Credenciales incorrectas. Verifique e intente de nuevo.');
      } finally {
        Spinner.hide();
      }
    });
  },

  validar() {
    let valido = true;
    const tipoActivo = document.querySelector('.tab-tipo.activo')?.dataset.tipo || 'alumno';

    if (tipoActivo === 'alumno') {
      const nc = document.getElementById('numero-control');
      const pw = document.getElementById('password-alumno');

      if (!nc || !Validacion.numeroControl(nc.value)) {
        Validacion.marcarError(nc, 'Ingrese un número de control válido (8 dígitos).');
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
        Validacion.marcarError(folio, 'Ingrese su folio de aspirante.');
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
    case 'pagina-panel-estatus':    PanelEstatus.init();      break;
    case 'pagina-formulario':       FormularioSolicitud.init(); break;
  }
});
