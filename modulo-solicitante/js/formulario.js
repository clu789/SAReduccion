/**
 * ============================================================
 * formulario.js - Formulario dinamico de solicitud (Equipo 5)
 * Sistema de Reduccion de Monto de Inscripcion - ITT
 * ============================================================
 * Dibuja el formulario a partir de la API (/api/solicitante), que lee Oracle:
 *   - preguntas ABIERTA, OPCION_UNICA y MIXTA, con sus opciones;
 *   - envio inicial completo y atomico (sin borradores);
 *   - reenvio de solicitudes RETIRADO, RECHAZADO o CANCELADO (?modo=reenvio&solicitud=ID).
 * Sirve igual a estudiantes (STUDENT) y aspirantes (APPLICANT).
 *
 * Reglas de seguridad de este archivo:
 *   - Todo texto de la API o del usuario se inserta con textContent, nunca con innerHTML.
 *   - La identidad nunca se envia: el servidor la toma de la sesion.
 *   - Las peticiones POST/PUT llevan el token CSRF comun (Http.csrfToken).
 *   - No se guardan respuestas en localStorage ni en sessionStorage.
 * Depende de app.js (Http, AuthSession, Spinner).
 */
(function () {
  'use strict';

  if (!document.body || document.body.id !== 'pagina-formulario-dinamico') return;

  const ESTATUS = { 1: 'PENDIENTE', 2: 'AUTORIZADO', 3: 'RECHAZADO', 4: 'CANCELADO', 5: 'APLICADO', 6: 'RETIRADO' };
  const MAX_TEXTO = 4000;
  const CONTROL_CHARS = /[\u0000-\u0008\u000B\u000C\u000E-\u001F]/;

  const estado = {
    rol: null,            // STUDENT | APPLICANT
    modo: 'nuevo',        // nuevo | reenvio
    convocatoria: null,   // ConvocatoriaDisponible (con sus tipos)
    solicitud: null,      // solo en reenvio
    idTipo: null,         // tipo de solicitud cargado
    tipoOriginal: null,   // tipo de la solicitud que se reenvia
    previasOriginales: {},
    formulario: null,
    previas: {},
    paso: 1,
    enviando: false,
  };

  /* ───────────────── utilidades DOM ───────────────── */
  const $ = (id) => document.getElementById(id);

  function el(tag, props, hijos) {
    const nodo = document.createElement(tag);
    Object.keys(props || {}).forEach((k) => {
      const v = props[k];
      if (k === 'className') nodo.className = v;
      else if (k === 'texto') nodo.textContent = v;
      else if (v !== null && v !== undefined && v !== false) nodo.setAttribute(k, v === true ? '' : String(v));
    });
    (hijos || []).forEach((h) => {
      if (h) nodo.appendChild(typeof h === 'string' ? document.createTextNode(h) : h);
    });
    return nodo;
  }

  function vaciar(nodo) {
    while (nodo && nodo.firstChild) nodo.removeChild(nodo.firstChild);
  }

  function mostrar(id, visible) {
    const nodo = $(id);
    if (nodo) nodo.classList.toggle('hidden', !visible);
  }

  function enlace(texto, href, clase) {
    return el('a', { href, className: 'btn-itt ' + clase, texto });
  }

  /** Alerta con el mismo estilo del sistema; el mensaje siempre es texto plano. */
  function alertar(tipo, texto, acciones) {
    const cont = $('alerta-formulario');
    vaciar(cont);
    const iconos = { info: 'fa-info-circle', exito: 'fa-check-circle', advertencia: 'fa-exclamation-triangle', error: 'fa-times-circle' };
    const caja = el('div', { className: 'alerta alerta-' + tipo, role: tipo === 'error' ? 'alert' : 'status' }, [
      el('i', { className: 'fa ' + (iconos[tipo] || iconos.info), 'aria-hidden': 'true' }),
      el('span', { texto }),
    ]);
    if (acciones && acciones.length) {
      const bloque = el('div', { className: 'acciones-alerta' });
      acciones.forEach((a) => bloque.appendChild(a));
      caja.appendChild(bloque);
    }
    cont.appendChild(caja);
    if (caja.scrollIntoView) caja.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }

  function limpiarAlerta() {
    vaciar($('alerta-formulario'));
  }

  function conReferencia(mensaje, error) {
    const referencia = error && error.payload && error.payload.correlationId;
    if (typeof referencia !== 'string' || !/^[A-Za-z0-9-]{8,64}$/.test(referencia)) {
      return mensaje;
    }
    return mensaje + ' Referencia: ' + referencia + '.';
  }

  /* ───────────────── API ───────────────── */
  async function api(metodo, ruta, cuerpo) {
    const opciones = { method: metodo, credentials: 'same-origin', headers: { 'Content-Type': 'application/json' } };
    if (metodo !== 'GET') {
      await Http.authSession(); // renueva Http.csrfToken
      opciones.headers['X-CSRF-TOKEN'] = Http.csrfToken;
      opciones.body = JSON.stringify(cuerpo || {});
    }
    let resp;
    try {
      resp = await fetch('/api' + ruta, opciones);
    } catch (e) {
      const error = new Error('sin conexion');
      error.status = 0;
      throw error;
    }
    const datos = resp.status === 204 ? null : await resp.json().catch(() => null);
    if (!resp.ok) {
      const error = new Error('HTTP ' + resp.status);
      error.status = resp.status;
      error.payload = datos;
      throw error;
    }
    return datos;
  }

  /* ───────────────── manejo de errores ───────────────── */
  function sesionExpirada() {
    mostrar('form-solicitud', false);
    mostrar('stepper', false);
    alertar('advertencia', 'Tu sesión expiró. Inicia sesión de nuevo para continuar.',
      [enlace('Ir a iniciar sesión', 'login.html', 'btn-primario')]);
  }

  /** Muestra el error de una peticion. Devuelve true si el error ya quedo atendido. */
  function manejarError(e, accionesExtra) {
    const acciones = accionesExtra || [];
    const mensajeServidor = e && e.payload && e.payload.error ? String(e.payload.error) : null;
    switch (e && e.status) {
      case 0:
        alertar('error', 'No hay conexión con el servidor. Revisa tu red e intenta de nuevo.');
        break;
      case 401:
        sesionExpirada();
        break;
      case 403:
        alertar('error', conReferencia(
          'Acceso no permitido. Recarga la página; si el problema continúa, vuelve a iniciar sesión.', e));
        break;
      case 404:
        alertar('advertencia', conReferencia('No encontramos la información solicitada.', e), acciones);
        break;
      case 409:
        alertar('advertencia', conReferencia(
          mensajeServidor || 'La operación no se puede realizar en este momento.', e), acciones);
        break;
      default:
        alertar('error', conReferencia('No fue posible procesar tu solicitud. Intenta más tarde.', e));
    }
  }

  function enlacePanel() {
    return enlace('Ir a mi panel', AuthSession.panelFor(estado.rol, 'login.html'), 'btn-secundario');
  }

  /* ───────────────── arranque ───────────────── */
  async function iniciar() {
    Spinner.show();
    try {
      const sesion = await Http.authSession();
      if (!sesion.authenticated) {
        window.location.replace('login.html');
        return;
      }
      if (sesion.role !== 'STUDENT' && sesion.role !== 'APPLICANT') {
        window.location.replace(AuthSession.panelFor(sesion.role, 'login.html'));
        return;
      }
      estado.rol = sesion.role;
      AuthSession.bindLogout('login.html', 'alerta-formulario');
      $('nav-panel').setAttribute('href', AuthSession.panelFor(sesion.role, 'login.html'));
      $('nombre-usuario-nav').textContent = sesion.identity || 'Usuario';
      await cargarPerfil(sesion);

      const params = new URLSearchParams(window.location.search);
      const idSolicitud = params.get('modo') === 'reenvio'
        ? params.get('solicitud')
        : params.get('reenviar');
      if (/^[0-9]{1,18}$/.test(idSolicitud || '')) {
        await iniciarReenvio(idSolicitud);
      } else {
        await iniciarNuevo(params);
      }
    } catch (e) {
      manejarError(e);
    } finally {
      Spinner.hide();
    }
  }

  async function cargarPerfil(sesion) {
    try {
      const p = await api('GET', '/solicitante/perfil');
      const tipo = p.tipo || p.rol;
      const identificador = p.identificador || p.identidad;
      const nombre = p.nombreCompleto || p.nombre;
      $('info-nombre').textContent = nombre || (tipo === 'ASPIRANTE' ? 'Aspirante de nuevo ingreso' : 'Estudiante');
      $('info-control').textContent = (tipo === 'ASPIRANTE' ? 'Ficha ' : '') + (identificador || '—');
      $('info-extra').textContent = tipo === 'ASPIRANTE'
        ? 'Periodo ' + p.periodo + ' / ' + p.anio
        : (p.semestre !== null && p.semestre !== undefined ? p.semestre + '.º semestre' : '—');
      if (nombre) $('nombre-usuario-nav').textContent = nombre;
    } catch (e) {
      // La tarjeta es informativa: si falla se deja la identidad de la sesion.
      $('info-nombre').textContent = sesion.role === 'APPLICANT' ? 'Aspirante de nuevo ingreso' : 'Estudiante';
      $('info-control').textContent = sesion.identity || '—';
      $('info-extra').textContent = sesion.role === 'APPLICANT' ? 'Periodo ' + sesion.periodo + ' / ' + sesion.anio : '—';
    }
  }

  function agruparConvocatorias(lista) {
    const porId = new Map();
    (lista || []).forEach((item) => {
      if (!item || item.idConvocatoria === null || item.idConvocatoria === undefined) return;
      const clave = String(item.idConvocatoria);
      let conv = porId.get(clave);
      if (!conv) {
        const idExistente = item.idSolicitud !== undefined ? item.idSolicitud : item.idSolicitudExistente;
        conv = {
          idConvocatoria: item.idConvocatoria,
          idPeriodo: item.idPeriodo !== undefined ? item.idPeriodo : item.periodo,
          periodo: item.periodo !== undefined ? item.periodo : item.idPeriodo,
          periodoDescripcion: item.periodoDescripcion || null,
          anio: item.anio,
          fechaInicio: item.fechaInicio,
          fechaFin: item.fechaFin,
          idSolicitud: idExistente == null ? null : idExistente,
          idEstatusSolicitud: item.idEstatusSolicitud == null ? null : item.idEstatusSolicitud,
          tipos: [],
        };
        porId.set(clave, conv);
      }
      if (conv.idSolicitud == null) {
        const idExistente = item.idSolicitud !== undefined ? item.idSolicitud : item.idSolicitudExistente;
        if (idExistente != null) conv.idSolicitud = idExistente;
      }
      if (conv.idEstatusSolicitud == null && item.idEstatusSolicitud != null) {
        conv.idEstatusSolicitud = item.idEstatusSolicitud;
      }
      if (Array.isArray(item.tipos)) {
        item.tipos.forEach((t) => {
          if (t && !conv.tipos.some((x) => Number(x.idTipoSolicitud) === Number(t.idTipoSolicitud))) {
            conv.tipos.push(t);
          }
        });
      } else if (item.idTipoSolicitud != null &&
          !conv.tipos.some((x) => Number(x.idTipoSolicitud) === Number(item.idTipoSolicitud))) {
        conv.tipos.push({
          idTipoSolicitud: item.idTipoSolicitud,
          nombre: item.tipoSolicitud || item.nombre || ('Tipo ' + item.idTipoSolicitud),
          descripcion: item.tipoDescripcion || null,
          idFormulario: item.idFormulario,
        });
      }
    });
    return Array.from(porId.values());
  }

  /* ───────────────── modo nuevo ───────────────── */
  async function iniciarNuevo(params) {
    const cruda = await api('GET', '/solicitante/convocatorias');
    const lista = agruparConvocatorias(cruda);
    if (!lista.length) {
      alertar('advertencia', 'No hay una convocatoria con periodo de recepción abierto en este momento.', [enlacePanel()]);
      return;
    }
    const convParam = params && params.get('convocatoria');
    const encontrada = convParam
      ? lista.find((c) => String(c.idConvocatoria) === String(convParam))
      : null;
    estado.convocatoria = encontrada || lista[0];
    if (estado.convocatoria.idSolicitud !== null && estado.convocatoria.idSolicitud !== undefined) {
      mostrarSolicitudExistente(estado.convocatoria);
      return;
    }
    $('titulo-formulario').textContent = 'Formulario de Solicitud';
    prepararPantalla();
    const tipos = estado.convocatoria.tipos || [];
    if (!tipos.length) {
      alertar('advertencia', 'La convocatoria no tiene tipos de solicitud disponibles.', [enlacePanel()]);
      mostrar('form-solicitud', false);
      return;
    }
    const tipoParam = params && params.get('tipo');
    const tipoElegido = tipoParam
      ? tipos.find((t) => String(t.idTipoSolicitud) === String(tipoParam))
      : null;
    const seleccionado = tipoElegido || (tipos.length === 1 ? tipos[0] : null);
    renderSelectorTipo(tipos, seleccionado ? seleccionado.idTipoSolicitud : null);
    if (seleccionado) {
      await cargarFormulario(seleccionado.idTipoSolicitud, {});
      renderSelectorTipo(tipos, seleccionado.idTipoSolicitud);
    } else {
      vaciar($('instrucciones'));
      vaciar($('contenedor-preguntas'));
      $('instrucciones').appendChild(el('div', { className: 'alerta alerta-info' }, [
        el('i', { className: 'fa fa-info-circle', 'aria-hidden': 'true' }),
        el('span', { texto: 'Seleccione el tipo de solicitud para ver el formulario.' }),
      ]));
    }
  }

  function mostrarSolicitudExistente(conv) {
    const nombre = ESTATUS[conv.idEstatusSolicitud] || 'EN PROCESO';
    const folio = conv.idSolicitud;
    const reenviable = [3, 4, 6].indexOf(conv.idEstatusSolicitud) !== -1;
    $('titulo-formulario').textContent = 'Solicitud ya registrada';
    if (reenviable) {
      alertar('advertencia',
        'Tu solicitud con folio ' + folio + ' está en estado ' + nombre + '. Puedes corregirla y reenviarla mientras el periodo de recepción esté abierto.',
        [enlace('Reenviar mi solicitud', 'formulario.html?modo=reenvio&solicitud=' + encodeURIComponent(folio), 'btn-primario'), enlacePanel()]);
    } else if (conv.idEstatusSolicitud === 1) {
      alertar('info',
        'Ya enviaste tu solicitud (folio ' + folio + ', estado PENDIENTE). Mientras esté pendiente no se puede editar; si necesitas corregirla, retírala desde tu panel dentro del periodo de recepción.',
        [enlacePanel()]);
    } else {
      alertar('info', 'Ya tienes una solicitud para esta convocatoria (folio ' + folio + ', estado ' + nombre + ').', [enlacePanel()]);
    }
  }

  /* ───────────────── modo reenvio ───────────────── */
  async function iniciarReenvio(id) {
    estado.modo = 'reenvio';
    let datos;
    try {
      datos = await api('GET', '/solicitante/solicitudes/' + id + '/reenvio');
    } catch (e) {
      $('titulo-formulario').textContent = 'Reenviar solicitud';
      manejarError(e, [enlacePanel()]);
      return;
    }
    estado.solicitud = datos.solicitud;
    estado.convocatoria = datos.convocatoria;
    estado.tipoOriginal = datos.solicitud.idTipoSolicitud;
    estado.previasOriginales = mapaRespuestas(datos.solicitud.respuestas);

    $('titulo-formulario').textContent = 'Reenviar solicitud';
    $('texto-enviar').textContent = 'Reenviar Solicitud';
    $('aviso-confirmacion').textContent =
      'Al confirmar, se reemplazarán el tipo y las respuestas anteriores y tu solicitud volverá al estado PENDIENTE. Es la misma solicitud, no se crea otra.';
    prepararPantalla();
    renderBannerReenvio(datos.solicitud);
    renderSelectorTipo(estado.convocatoria.tipos, estado.tipoOriginal);
    estado.idTipo = estado.tipoOriginal;
    estado.formulario = datos.formulario;
    estado.previas = estado.previasOriginales;
    renderFormulario(datos.formulario, estado.previas);
    $('btn-siguiente').disabled = false;
  }

  function mapaRespuestas(lista) {
    const mapa = {};
    (lista || []).forEach((r) => { mapa[r.idPregunta] = r; });
    return mapa;
  }

  function renderBannerReenvio(solicitud) {
    const cont = $('banner-reenvio');
    vaciar(cont);
    const caja = el('div', { className: 'banner-reenvio' }, [
      el('div', {}, [
        el('strong', { texto: 'Solicitud ' + solicitud.idSolicitud + ' · estado actual: ' + (ESTATUS[solicitud.idEstatusSolicitud] || '—') }),
      ]),
      el('div', { texto: 'Revisa tus respuestas, corrige lo necesario y confirma el reenvío.' }),
    ]);
    if (solicitud.ultimoComentario) {
      caja.appendChild(el('div', { className: 'comentario-admin' }, [
        el('strong', { texto: 'Comentario registrado: ' }),
        document.createTextNode(solicitud.ultimoComentario),
      ]));
    }
    cont.appendChild(caja);
  }

  /* ───────────────── pantalla y formulario ───────────────── */
  function prepararPantalla() {
    mostrar('form-solicitud', true);
    mostrar('stepper', true);
    irAPaso(1);
  }

  function renderSelectorTipo(tipos, seleccionado) {
    const cont = $('selector-tipo');
    vaciar(cont);
    const grupo = el('div', { className: 'form-group', role: 'radiogroup', 'aria-labelledby': 'tipo-lbl' });
    grupo.appendChild(el('span', { className: 'etiqueta-pregunta', id: 'tipo-lbl' }, [
      'Tipo de solicitud ', el('span', { className: 'requerido', texto: '*' }),
    ]));
    tipos.forEach((t) => {
      const id = 'tipo-' + t.idTipoSolicitud;
      const radio = el('input', { type: 'radio', name: 'tipo-solicitud', id, value: t.idTipoSolicitud });
      if (t.idTipoSolicitud === seleccionado) radio.checked = true;
      radio.addEventListener('change', () => cambiarTipo(t.idTipoSolicitud));
      grupo.appendChild(el('div', { className: 'opcion-radio' }, [radio, el('label', { for: id, texto: t.nombre })]));
    });
    grupo.appendChild(el('span', { className: 'mensaje-error', id: 'tipo-error', role: 'alert' }));
    cont.appendChild(grupo);
  }

  async function cambiarTipo(idTipo) {
    if (idTipo === estado.idTipo) return;
    if (estado.formulario && leerRespuestas().length &&
        !window.confirm('Al cambiar el tipo de solicitud se borrarán las respuestas capturadas. ¿Desea continuar?')) {
      renderSelectorTipo(estado.convocatoria.tipos, estado.idTipo);
      return;
    }
    const previas = (idTipo === estado.tipoOriginal) ? estado.previasOriginales : {};
    await cargarFormulario(idTipo, previas);
    renderSelectorTipo(estado.convocatoria.tipos, estado.idTipo);
  }

  async function cargarFormulario(idTipo, previas) {
    Spinner.show();
    try {
      limpiarAlerta();
      const f = await api('GET', '/solicitante/formulario?convocatoria=' + encodeURIComponent(estado.convocatoria.idConvocatoria) +
        '&tipo=' + encodeURIComponent(idTipo));
      estado.idTipo = idTipo;
      estado.formulario = f;
      estado.previas = previas || {};
      renderFormulario(f, estado.previas);
      $('btn-siguiente').disabled = false;
    } catch (e) {
      manejarError(e);
    } finally {
      Spinner.hide();
    }
  }

  function renderFormulario(f, previas) {
    const inst = $('instrucciones');
    vaciar(inst);
    if (f.instrucciones) {
      inst.appendChild(el('div', { className: 'alerta alerta-info', style: 'margin-bottom:16px;' }, [
        el('i', { className: 'fa fa-info-circle', 'aria-hidden': 'true' }),
        el('div', { className: 'instrucciones-texto', texto: f.instrucciones }),
      ]));
    }
    const cont = $('contenedor-preguntas');
    vaciar(cont);
    const seccion = el('div', { className: 'seccion-formulario' }, [
      el('div', { className: 'seccion-titulo' }, [
        el('i', { className: 'fa fa-list-alt', 'aria-hidden': 'true' }),
        document.createTextNode(f.nombre || 'Datos de la solicitud'),
      ]),
    ]);
    (f.preguntas || []).forEach((p, i) => seccion.appendChild(crearPregunta(p, i + 1, previas[p.idPregunta])));
    cont.appendChild(seccion);
  }

  function etiqueta(p, num, base, esLabel) {
    const contenido = [num + '. ' + p.texto + ' '];
    if (p.obligatoria) contenido.push(el('span', { className: 'requerido', texto: '*' }));
    return esLabel
      ? el('label', { for: base, id: base + '-lbl' }, contenido)
      : el('span', { className: 'etiqueta-pregunta', id: base + '-lbl' }, contenido);
  }

  function autoAjustar(area) {
    if (area.scrollHeight > 0) {
      area.style.height = 'auto';
      area.style.height = area.scrollHeight + 'px';
    }
  }

  function crearPregunta(p, num, previa) {
    const base = 'p' + p.idPregunta;
    const grupo = el('div', { className: 'form-group pregunta', 'data-tipo': p.tipo, 'data-id': p.idPregunta });
    const error = el('span', { className: 'mensaje-error', id: base + '-error', role: 'alert' });

    if (p.tipo === 'ABIERTA') {
      const area = el('textarea', {
        id: base, name: base, className: 'form-control-itt', rows: 1, maxlength: MAX_TEXTO,
        'aria-describedby': base + '-error', 'aria-required': p.obligatoria ? 'true' : 'false',
      });
      area.value = previa && previa.textoRespuesta ? previa.textoRespuesta : '';
      area.addEventListener('input', () => { autoAjustar(area); limpiarError(p.idPregunta); });
      grupo.appendChild(etiqueta(p, num, base, true));
      grupo.appendChild(area);
      grupo.appendChild(error);
      setTimeout(() => autoAjustar(area), 0);
      return grupo;
    }

    // OPCION_UNICA y MIXTA: botones de opcion
    const radios = el('div', { role: 'radiogroup', 'aria-labelledby': base + '-lbl', 'aria-describedby': base + '-error' });
    const esMixta = p.tipo === 'MIXTA';
    const espCont = esMixta ? el('div', { className: 'especificacion hidden', id: base + '-esp' }) : null;
    let espArea = null;
    if (esMixta) {
      espArea = el('textarea', {
        id: base + '-txt', name: base + '-txt', className: 'form-control-itt', rows: 1, maxlength: MAX_TEXTO,
        'aria-describedby': base + '-error',
      });
      espArea.addEventListener('input', () => { autoAjustar(espArea); limpiarError(p.idPregunta); });
      espCont.appendChild(el('label', { for: base + '-txt', texto: 'Especifique' }));
      espCont.appendChild(espArea);
    }

    function actualizarEspecificacion() {
      if (!esMixta) return;
      const sel = radios.querySelector('input:checked'); // relativo al grupo: aun no esta en el documento
      const opcion = sel ? buscarOpcion(p, Number(sel.value)) : null;
      const requiere = !!(opcion && opcion.requiereEspecificacion);
      espCont.classList.toggle('hidden', !requiere);
      if (!requiere) espArea.value = '';
      else setTimeout(() => autoAjustar(espArea), 0);
    }

    (p.opciones || []).forEach((o) => {
      const id = base + '-o' + o.idOpcion;
      const radio = el('input', { type: 'radio', name: base, id, value: o.idOpcion });
      if (previa && previa.idOpcion === o.idOpcion) radio.checked = true;
      radio.addEventListener('change', () => { limpiarError(p.idPregunta); actualizarEspecificacion(); });
      radios.appendChild(el('div', { className: 'opcion-radio' }, [radio, el('label', { for: id, texto: o.texto })]));
    });

    grupo.appendChild(etiqueta(p, num, base, false));
    grupo.appendChild(radios);
    if (esMixta) {
      grupo.appendChild(espCont);
      if (previa && previa.textoRespuesta) espArea.value = previa.textoRespuesta;
      actualizarEspecificacion();
    }
    grupo.appendChild(error);
    return grupo;
  }

  function buscarOpcion(p, idOpcion) {
    return (p.opciones || []).filter((o) => o.idOpcion === idOpcion)[0] || null;
  }

  /* ───────────────── lectura y validacion ───────────────── */
  /** Respuestas capturadas; las preguntas sin respuesta no se incluyen. */
  function leerRespuestas() {
    const lista = [];
    ((estado.formulario && estado.formulario.preguntas) || []).forEach((p) => {
      const base = 'p' + p.idPregunta;
      if (p.tipo === 'ABIERTA') {
        const texto = $(base).value.trim();
        if (texto) lista.push({ idPregunta: p.idPregunta, idOpcion: null, texto });
        return;
      }
      const sel = document.querySelector('input[name="' + base + '"]:checked');
      const idOpcion = sel ? Number(sel.value) : null;
      let texto = null;
      if (p.tipo === 'MIXTA' && idOpcion !== null) {
        const opcion = buscarOpcion(p, idOpcion);
        const campo = $(base + '-txt');
        if (opcion && opcion.requiereEspecificacion && campo) texto = campo.value.trim() || null;
      }
      if (idOpcion !== null) lista.push({ idPregunta: p.idPregunta, idOpcion, texto });
    });
    return lista;
  }

  function textoInvalido(texto) {
    if (texto.length > MAX_TEXTO) return 'El texto no puede superar ' + MAX_TEXTO + ' caracteres.';
    if (CONTROL_CHARS.test(texto)) return 'El texto contiene caracteres no permitidos.';
    return null;
  }

  /** Mismas reglas que el servidor; devuelve [{idPregunta, mensaje}]. */
  function validar() {
    const errores = [];
    const porPregunta = {};
    leerRespuestas().forEach((r) => { porPregunta[r.idPregunta] = r; });
    estado.formulario.preguntas.forEach((p) => {
      const r = porPregunta[p.idPregunta];
      if (!r) {
        if (p.obligatoria) {
          errores.push({ idPregunta: p.idPregunta, mensaje: p.tipo === 'ABIERTA' ? 'Esta pregunta es obligatoria.' : 'Seleccione una opción.' });
        }
        return;
      }
      if (p.tipo === 'ABIERTA') {
        const msg = textoInvalido(r.texto);
        if (msg) errores.push({ idPregunta: p.idPregunta, mensaje: msg });
      } else if (p.tipo === 'MIXTA') {
        const opcion = buscarOpcion(p, r.idOpcion);
        if (opcion && opcion.requiereEspecificacion) {
          if (!r.texto) errores.push({ idPregunta: p.idPregunta, mensaje: 'Especifique su respuesta.' });
          else {
            const msg = textoInvalido(r.texto);
            if (msg) errores.push({ idPregunta: p.idPregunta, mensaje: msg });
          }
        }
      }
    });
    return errores;
  }

  function marcarError(idPregunta, mensaje) {
    const base = 'p' + idPregunta;
    const span = $(base + '-error');
    if (span) {
      span.textContent = mensaje;
      span.classList.add('visible');
    }
    const campo = $(base);
    if (campo) campo.classList.add('error');
    const grupo = span ? span.closest('.pregunta') : null;
    if (grupo && grupo.getAttribute('data-tipo') !== 'ABIERTA') grupo.classList.add('grupo-error');
  }

  function limpiarError(idPregunta) {
    const base = 'p' + idPregunta;
    const span = $(base + '-error');
    if (span) {
      span.textContent = '';
      span.classList.remove('visible');
    }
    const campo = $(base);
    if (campo) campo.classList.remove('error');
    const grupo = span ? span.closest('.pregunta') : null;
    if (grupo) grupo.classList.remove('grupo-error');
  }

  function limpiarErrores() {
    ((estado.formulario && estado.formulario.preguntas) || []).forEach((p) => limpiarError(p.idPregunta));
    const t = $('tipo-error');
    if (t) {
      t.textContent = '';
      t.classList.remove('visible');
    }
  }

  function enfocarPrimerError(errores) {
    const primero = errores.filter((e) => e.idPregunta !== null && e.idPregunta !== undefined)[0];
    if (!primero) return;
    const base = 'p' + primero.idPregunta;
    const objetivo = $(base) || document.querySelector('input[name="' + base + '"]');
    if (objetivo && objetivo.focus) objetivo.focus();
    if (objetivo && objetivo.scrollIntoView) objetivo.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  function aplicarErrores(errores) {
    errores.forEach((e) => {
      if (e.idPregunta !== null && e.idPregunta !== undefined) marcarError(e.idPregunta, e.mensaje);
    });
    enfocarPrimerError(errores);
  }

  /* ───────────────── pasos ───────────────── */
  function irAPaso(n) {
    estado.paso = n;
    mostrar('paso-1', n === 1);
    mostrar('paso-2', n === 2);
    mostrar('btn-anterior', n === 2);
    mostrar('btn-siguiente', n === 1);
    mostrar('btn-enviar', n === 2);
    const s1 = $('step-1');
    const s2 = $('step-2');
    s1.classList.toggle('activo', n === 1);
    s1.classList.toggle('completado', n === 2);
    s2.classList.toggle('activo', n === 2);
    $('step-linea').classList.toggle('completada', n === 2);
    if (n === 2) {
      construirResumen();
      const conf = $('confirmacion-datos');
      conf.checked = false;
      $('btn-enviar').disabled = true;
    }
  }

  function construirResumen() {
    const dl = $('resumen-solicitud');
    vaciar(dl);
    const tipo = (estado.convocatoria.tipos || []).filter((t) => t.idTipoSolicitud === estado.idTipo)[0];
    dl.appendChild(el('dt', { texto: 'Tipo de solicitud' }));
    dl.appendChild(el('dd', { texto: tipo ? tipo.nombre : '—' }));
    const respuestas = {};
    leerRespuestas().forEach((r) => { respuestas[r.idPregunta] = r; });
    estado.formulario.preguntas.forEach((p, i) => {
      dl.appendChild(el('dt', { texto: (i + 1) + '. ' + p.texto }));
      const r = respuestas[p.idPregunta];
      let valor = '';
      if (r) {
        if (p.tipo === 'ABIERTA') valor = r.texto;
        else {
          const opcion = buscarOpcion(p, r.idOpcion);
          valor = (opcion ? opcion.texto : '') + (r.texto ? ' — ' + r.texto : '');
        }
      }
      dl.appendChild(el('dd', { className: r ? '' : 'sin-respuesta', texto: r ? valor : 'Sin respuesta' }));
    });
  }

  function siguiente() {
    limpiarAlerta();
    limpiarErrores();
    if (estado.idTipo === null) {
      const t = $('tipo-error');
      if (t) {
        t.textContent = 'Seleccione el tipo de solicitud.';
        t.classList.add('visible');
      }
      return;
    }
    const errores = validar();
    if (errores.length) {
      aplicarErrores(errores);
      alertar('error', 'Revisa las preguntas marcadas antes de continuar.');
      return;
    }
    irAPaso(2);
  }

  /* ───────────────── envio ───────────────── */
  async function enviar() {
    if (estado.enviando || !$('confirmacion-datos').checked) return;
    estado.enviando = true;
    $('btn-enviar').disabled = true;
    Spinner.show();
    limpiarAlerta();
    try {
      const cuerpo = {
        idConvocatoria: estado.convocatoria.idConvocatoria,
        idTipoSolicitud: estado.idTipo,
        respuestas: leerRespuestas(),
      };
      let resultado;
      if (estado.modo === 'reenvio') {
        resultado = await api('PUT', '/solicitante/solicitudes/' + estado.solicitud.idSolicitud + '/reenvio', cuerpo);
      } else {
        resultado = await api('POST', '/solicitante/solicitudes', cuerpo);
      }
      mostrarExito(resultado);
    } catch (e) {
      errorDeEnvio(e);
    } finally {
      estado.enviando = false;
      Spinner.hide();
      if (estado.paso === 2) $('btn-enviar').disabled = !$('confirmacion-datos').checked;
    }
  }

  function errorDeEnvio(e) {
    if (e.status === 400 && e.payload) {
      irAPaso(1);
      limpiarErrores();
      const errores = e.payload.errores || [];
      aplicarErrores(errores);
      alertar('error', conReferencia(
        errores.length ? 'El servidor encontró respuestas que corregir. Revisa las preguntas marcadas.'
          : (e.payload.error || 'Las respuestas no son válidas.'), e));
      return;
    }
    manejarError(e, [enlacePanel()]);
  }

  function mostrarExito(res) {
    const reenvio = estado.modo === 'reenvio';
    mostrar('form-solicitud', false);
    mostrar('stepper', false);
    limpiarAlerta();
    $('titulo-formulario').textContent = reenvio ? 'Solicitud reenviada' : 'Solicitud enviada';
    const cont = $('zona-estado');
    vaciar(cont);
    cont.appendChild(el('div', { className: 'card-itt' }, [
      el('div', { className: 'card-itt-body exito-card' }, [
        el('i', { className: 'fa fa-check-circle exito-icono', 'aria-hidden': 'true' }),
        el('h3', { texto: reenvio ? 'Tu solicitud fue reenviada' : 'Tu solicitud fue enviada' }),
        el('div', { className: 'exito-folio', texto: 'Folio ' + res.idSolicitud + ' · Estado ' + res.estatus }),
        el('p', { texto: 'Está en espera de revisión. Puedes consultar su avance en tu panel.' }),
        el('div', { className: 'exito-acciones' }, [enlace('Ir a mi panel', AuthSession.panelFor(estado.rol, 'login.html'), 'btn-primario')]),
      ]),
    ]));
  }

  /* ───────────────── eventos ───────────────── */
  function enlazarEventos() {
    $('form-solicitud').addEventListener('submit', (ev) => ev.preventDefault());
    $('btn-siguiente').addEventListener('click', siguiente);
    $('btn-anterior').addEventListener('click', () => { limpiarAlerta(); irAPaso(1); });
    $('btn-enviar').addEventListener('click', enviar);
    $('confirmacion-datos').addEventListener('change', (ev) => {
      $('btn-enviar').disabled = !ev.target.checked || estado.enviando;
    });
  }

  enlazarEventos();
  iniciar();
})();
