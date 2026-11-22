/**
 * itt-security.js — Cliente común de seguridad de SAReducción (Equipo 1).
 *
 * Incluir en cualquier pantalla, antes del script propio del módulo:
 *   <link rel="stylesheet" href="/compartido/css/itt-security.css" />
 *   <script src="/compartido/js/itt-security.js"></script>
 *
 * Expone el objeto global ITTSecurity:
 *   ITTSecurity.fetch(url, opciones)   fetch con CSRF, cookies y manejo de 401/403
 *   ITTSecurity.json(url, opciones)    igual, pero devuelve el JSON y lanza error si falla
 *   ITTSecurity.sesion()               estado de la sesión (GET /api/auth/me)
 *   ITTSecurity.protegerPagina(roles)  exige sesión y rol al cargar una pantalla
 *   ITTSecurity.logout()               cierra la sesión y regresa al inicio de sesión
 *   ITTSecurity.Captcha.montar(el)     widget de captcha con alternativa accesible
 *   ITTSecurity.Navegacion.montar(el)  barra de navegación con menú por rol
 *   ITTSecurity.Inactividad.iniciar()  aviso y cierre por inactividad
 *
 * No tiene dependencias y no modifica window.fetch.
 */
(function (global) {
  'use strict';

  var API = '/api';
  var LOGIN = {
    solicitante: '/solicitante/login.html',
    administrativo: '/administrativo/login.html',
  };
  var METODOS_MUTABLES = ['POST', 'PUT', 'PATCH', 'DELETE'];
  var MENSAJES = {
    SESSION_EXPIRED: 'Su sesión expiró por inactividad. Será dirigido al inicio de sesión.',
    NOT_AUTHENTICATED: 'Debe iniciar sesión para continuar.',
    ACCESS_DENIED: 'No tiene permiso para realizar esta operación.',
  };

  var estado = { csrf: null, sesion: null, consulta: null, redirigiendo: false };

  /* ───────────── Utilidades ───────────── */

  function crear(tag, atributos, texto) {
    var el = document.createElement(tag);
    Object.keys(atributos || {}).forEach(function (k) { el.setAttribute(k, atributos[k]); });
    if (texto != null) el.textContent = texto;
    return el;
  }

  function loginSegunRuta() {
    return global.location.pathname.indexOf('/administrativo/') === 0 ? LOGIN.administrativo : LOGIN.solicitante;
  }

  /** Solo se aceptan redirecciones a rutas internas del sitio. */
  function rutaSegura(ruta, alterna) {
    return typeof ruta === 'string' && /^\/(?!\/)[\w\-./]*$/.test(ruta) ? ruta : alterna;
  }

  /* ───────────── Aviso accesible ───────────── */

  function aviso(mensaje, tipo) {
    var caja = document.getElementById('itt-sec-aviso');
    if (!caja) {
      caja = crear('div', { id: 'itt-sec-aviso', role: 'alert', 'aria-live': 'assertive' });
      document.body.appendChild(caja);
    }
    caja.className = 'itt-sec-aviso itt-sec-aviso--' + (tipo || 'info');
    caja.textContent = mensaje;
    caja.hidden = false;
    return caja;
  }

  function avisarYRedirigir(mensaje, destino) {
    if (estado.redirigiendo) return;
    estado.redirigiendo = true;
    aviso(mensaje, 'alerta');
    global.setTimeout(function () { global.location.replace(destino); }, 2500);
  }

  /* ───────────── Sesión y CSRF ───────────── */

  /**
   * Consulta la sesión. Sin sesión el backend responde 401 con el token CSRF.
   * Las llamadas simultáneas comparten una sola petición: si dos peticiones
   * salieran a la vez sin cookie, el servidor crearía dos sesiones distintas y
   * el token CSRF o el captcha de una no valdrían para la otra.
   */
  function sesion() {
    if (estado.consulta) return estado.consulta;
    var consulta = global.fetch(API + '/auth/me', {
      credentials: 'same-origin', headers: { Accept: 'application/json' },
    }).then(function (resp) {
      return resp.json().catch(function () { return {}; }).then(function (datos) {
        if (resp.status !== 200 && resp.status !== 401) {
          var error = new Error('Servicio de autenticación no disponible');
          error.status = resp.status;
          throw error;
        }
        if (datos.csrfToken) estado.csrf = datos.csrfToken;
        estado.sesion = datos;
        return datos;
      });
    });
    estado.consulta = consulta;
    var liberar = function () { estado.consulta = null; };
    consulta.then(liberar, liberar);
    return consulta;
  }

  function tokenCsrf() {
    return estado.csrf ? Promise.resolve(estado.csrf) : sesion().then(function () { return estado.csrf; });
  }

  function manejarNoAutorizado(resp, datos) {
    var codigo = datos && datos.error;
    if (resp.status === 401 && (codigo === 'SESSION_EXPIRED' || codigo === 'NOT_AUTHENTICATED')) {
      avisarYRedirigir(MENSAJES[codigo], rutaSegura(datos.redirect, loginSegunRuta()));
    } else if (resp.status === 403 && codigo === 'ACCESS_DENIED') {
      aviso(datos.message || MENSAJES.ACCESS_DENIED, 'alerta');
    }
  }

  /**
   * fetch para la API. Agrega cookies de sesión y, en POST/PUT/PATCH/DELETE,
   * la cabecera X-CSRF-TOKEN. Si el token caducó (403 CSRF_INVALID) lo renueva
   * y reintenta una vez. Ante 401/403 estándar avisa y redirige.
   * Opción propia: { sinAviso: true } desactiva el aviso automático.
   */
  function fetchSeguro(url, opciones, reintento) {
    var opts = Object.assign({ method: 'GET', credentials: 'same-origin' }, opciones || {});
    var metodo = String(opts.method).toUpperCase();
    var cabeceras = new Headers(opts.headers || {});
    if (!cabeceras.has('Accept')) cabeceras.set('Accept', 'application/json');
    if (opts.body != null && typeof opts.body === 'object' && !(opts.body instanceof FormData)) {
      opts.body = JSON.stringify(opts.body);
    }
    if (typeof opts.body === 'string' && !cabeceras.has('Content-Type')) {
      cabeceras.set('Content-Type', 'application/json');
    }
    var mutable = METODOS_MUTABLES.indexOf(metodo) !== -1;
    var preparar = mutable ? tokenCsrf() : Promise.resolve(null);

    return preparar.then(function (token) {
      if (token) cabeceras.set('X-CSRF-TOKEN', token);
      opts.headers = cabeceras;
      return global.fetch(url, opts);
    }).then(function (resp) {
      if (resp.status !== 401 && resp.status !== 403) return resp;
      return resp.clone().json().catch(function () { return {}; }).then(function (datos) {
        if (datos.csrfToken) estado.csrf = datos.csrfToken;
        if (resp.status === 403 && datos.error === 'CSRF_INVALID' && mutable && !reintento) {
          estado.csrf = null;
          return fetchSeguro(url, opciones, true);
        }
        if (!opts.sinAviso) manejarNoAutorizado(resp, datos);
        return resp;
      });
    });
  }

  /** Como fetch(), pero devuelve el JSON; si la respuesta no es 2xx lanza un Error con .status y .payload. */
  function json(url, opciones) {
    return fetchSeguro(url, opciones).then(function (resp) {
      if (resp.status === 204) return null;
      return resp.json().catch(function () { return {}; }).then(function (datos) {
        if (datos && datos.csrfToken) estado.csrf = datos.csrfToken;
        if (!resp.ok) {
          var error = new Error((datos && (datos.message || datos.error)) || 'No se pudo completar la solicitud.');
          error.status = resp.status;
          error.code = datos && datos.error;
          error.payload = datos;
          throw error;
        }
        return datos;
      });
    });
  }

  function logout(destino) {
    return fetchSeguro(API + '/auth/logout', { method: 'POST', body: '{}', sinAviso: true })
      .catch(function () { return null; })
      .then(function () {
        estado.csrf = null;
        estado.sesion = null;
        global.location.replace(rutaSegura(destino, loginSegunRuta()));
      });
  }

  /**
   * Exige sesión activa con uno de los roles indicados ('STUDENT', 'APPLICANT', 'ADMIN').
   * Sin sesión redirige al inicio de sesión; con otro rol, a la pantalla de ese rol.
   * Devuelve la sesión, o null si se redirigió.
   */
  function protegerPagina(roles, opciones) {
    var permitidos = [].concat(roles || []);
    var login = (opciones && opciones.login) || loginSegunRuta();
    return sesion().then(function (datos) {
      if (!datos.authenticated) {
        global.location.replace(login);
        return null;
      }
      if (permitidos.length && permitidos.indexOf(datos.role) === -1) {
        global.location.replace(rutaSegura(datos.home, login));
        return null;
      }
      if (datos.sessionTimeoutSeconds) Inactividad.iniciar(datos.sessionTimeoutSeconds);
      return datos;
    }).catch(function () {
      global.location.replace(login);
      return null;
    });
  }

  /* ───────────── Captcha ───────────── */

  var Captcha = {
    /**
     * Dibuja el captcha dentro de `contenedor` (elemento o selector).
     * Devuelve { valor(), recargar(), limpiar(), activo() }.
     */
    montar: function (contenedor) {
      var raiz = typeof contenedor === 'string' ? document.querySelector(contenedor) : contenedor;
      if (!raiz) return null;
      var idBase = 'itt-captcha-' + Math.random().toString(36).slice(2, 8);
      var modo = 'imagen';
      var activo = true;

      raiz.classList.add('itt-captcha');
      raiz.textContent = '';
      var etiqueta = crear('label', { 'for': idBase + '-campo', 'class': 'itt-captcha__etiqueta' },
        'Verificación de seguridad ');
      etiqueta.appendChild(crear('span', { 'class': 'requerido', 'aria-hidden': 'true' }, '*'));
      var zona = crear('div', { 'class': 'itt-captcha__desafio', id: idBase + '-desafio', 'aria-live': 'polite' });
      var acciones = crear('div', { 'class': 'itt-captcha__acciones' });
      var btnRecargar = crear('button', { type: 'button', 'class': 'itt-captcha__boton' }, 'Cambiar desafío');
      var btnModo = crear('button', { type: 'button', 'class': 'itt-captcha__boton' });
      var campo = crear('input', {
        type: 'text', id: idBase + '-campo', 'class': 'form-control-itt itt-captcha__campo',
        autocomplete: 'off', autocapitalize: 'characters', spellcheck: 'false', maxlength: '8',
        'aria-describedby': idBase + '-desafio', 'aria-required': 'true',
      });
      acciones.appendChild(btnRecargar);
      acciones.appendChild(btnModo);
      [etiqueta, zona, acciones, campo].forEach(function (el) { raiz.appendChild(el); });

      function pintar(desafio) {
        zona.textContent = '';
        activo = desafio.enabled !== false;
        raiz.hidden = !activo;
        if (!activo) return;
        if (desafio.tipo === 'accesible') {
          zona.appendChild(crear('p', { 'class': 'itt-captcha__pregunta' }, desafio.pregunta));
          campo.setAttribute('inputmode', 'numeric');
          campo.setAttribute('placeholder', 'Resultado');
          btnModo.textContent = 'Usar imagen';
        } else {
          zona.appendChild(crear('img', {
            src: desafio.imagen, width: '220', height: '80', 'class': 'itt-captcha__imagen',
            alt: 'Imagen con ' + (desafio.longitud || 5) + ' caracteres. Si no puede verla, elija «Usar pregunta accesible».',
          }));
          campo.removeAttribute('inputmode');
          campo.setAttribute('placeholder', 'Caracteres de la imagen');
          btnModo.textContent = 'Usar pregunta accesible';
        }
      }

      function recargar() {
        campo.value = '';
        var tipo = modo === 'accesible' ? 'accesible' : 'imagen';
        // Primero se asegura la cookie de sesión: el desafío se guarda en ella.
        return tokenCsrf().catch(function () { return null; }).then(function () {
          return global.fetch(API + '/auth/captcha?tipo=' + tipo, {
            credentials: 'same-origin', headers: { Accept: 'application/json' },
          });
        }).then(function (resp) {
          if (!resp.ok) throw new Error('captcha');
          return resp.json();
        }).then(pintar).catch(function () {
          zona.textContent = 'No se pudo cargar la verificación. Use «Cambiar desafío» para reintentar.';
        });
      }

      btnRecargar.addEventListener('click', function () { recargar().then(function () { campo.focus(); }); });
      btnModo.addEventListener('click', function () {
        modo = modo === 'imagen' ? 'accesible' : 'imagen';
        recargar().then(function () { campo.focus(); });
      });
      recargar();

      return {
        valor: function () { return campo.value.trim(); },
        recargar: recargar,
        limpiar: function () { campo.value = ''; },
        activo: function () { return activo; },
        enfocar: function () { campo.focus(); },
      };
    },
  };

  /* ───────────── Navegación compartida ───────────── */

  var Navegacion = {
    vistas: {
      STUDENT: [
        { titulo: 'Mi solicitud', href: '/solicitante/panel-estatus.html' },
        { titulo: 'Nueva solicitud', href: '/solicitante/formulario.html' },
      ],
      APPLICANT: [
        { titulo: 'Panel de aspirante', href: '/solicitante/panel-aspirante.html' },
        { titulo: 'Nueva solicitud', href: '/solicitante/formulario.html' },
      ],
      ADMIN: [
        { titulo: 'Panel administrativo', href: '/administrativo/panel-admin.html' },
        { titulo: 'Convocatorias', href: '/administrativo/convocatorias-admin.html' },
        { titulo: 'Nueva o editar convocatoria', href: '/administrativo/convocatorias-crear-editar.html' },
        { titulo: 'Historial de convocatorias', href: '/administrativo/historial-convocatorias.html' },
      ],
    },
    etiquetas: { STUDENT: 'Estudiante', APPLICANT: 'Aspirante', ADMIN: 'Administrador' },

    /** Permite a cada equipo agregar sus pantallas al menú: registrar('ADMIN', {titulo, href}). */
    registrar: function (rol, vista) {
      (this.vistas[rol] = this.vistas[rol] || []).push(vista);
    },

    /** Dibuja la barra en `contenedor` para la sesión dada (o la consulta si se omite). */
    montar: function (contenedor, datosSesion) {
      var raiz = typeof contenedor === 'string' ? document.querySelector(contenedor) : contenedor;
      if (!raiz) return Promise.resolve(null);
      var self = this;
      var obtener = datosSesion ? Promise.resolve(datosSesion) : sesion();
      return obtener.then(function (datos) {
        if (!datos || !datos.authenticated) { raiz.hidden = true; return null; }
        self.pintar(raiz, datos);
        return datos;
      }).catch(function () { raiz.hidden = true; return null; });
    },

    pintar: function (raiz, datos) {
      var vistas = this.vistas[datos.role] || [];
      var idMenu = 'itt-nav-menu-' + Math.random().toString(36).slice(2, 8);
      raiz.textContent = '';
      raiz.hidden = false;
      raiz.classList.add('itt-nav');
      raiz.setAttribute('role', 'navigation');
      raiz.setAttribute('aria-label', 'Navegación del sistema');

      var boton = crear('button', {
        type: 'button', 'class': 'itt-nav__boton', 'aria-haspopup': 'true',
        'aria-expanded': 'false', 'aria-controls': idMenu,
      }, 'Vistas ▾');
      var lista = crear('ul', { id: idMenu, 'class': 'itt-nav__menu', hidden: '' });
      vistas.forEach(function (vista) {
        var li = crear('li');
        var enlace = crear('a', { href: vista.href }, vista.titulo);
        if (global.location.pathname === vista.href) enlace.setAttribute('aria-current', 'page');
        li.appendChild(enlace);
        lista.appendChild(li);
      });

      var usuario = crear('span', { 'class': 'itt-nav__usuario' },
        (this.etiquetas[datos.role] || datos.role) + ': ' + (datos.identity || datos.username || ''));
      var salir = crear('button', { type: 'button', 'class': 'itt-nav__salir' }, 'Cerrar sesión');
      salir.addEventListener('click', function () { logout(); });

      function abrir(valor) {
        lista.hidden = !valor;
        boton.setAttribute('aria-expanded', String(valor));
      }
      boton.addEventListener('click', function () { abrir(lista.hidden); });
      raiz.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && !lista.hidden) { abrir(false); boton.focus(); }
      });
      document.addEventListener('click', function (e) { if (!raiz.contains(e.target)) abrir(false); });

      var grupo = crear('div', { 'class': 'itt-nav__grupo' });
      grupo.appendChild(boton);
      grupo.appendChild(lista);
      raiz.appendChild(grupo);
      raiz.appendChild(usuario);
      raiz.appendChild(salir);
    },
  };

  /* ───────────── Expiración por inactividad ───────────── */

  var Inactividad = {
    temporizadorAviso: null,
    temporizadorCierre: null,
    segundos: 0,

    /**
     * Avisa un minuto antes de que la sesión expire por inactividad y, al
     * cumplirse el plazo, la cierra. La actividad en pantalla (teclado, ratón,
     * toque) reinicia la cuenta y renueva la sesión en el servidor.
     */
    iniciar: function (segundos) {
      if (!segundos || segundos < 120) return;
      this.segundos = segundos;
      var self = this;
      var ultimoPing = Date.now();
      function actividad() {
        self.reprogramar();
        // Mantiene viva la sesión del servidor mientras la persona trabaja (máx. un aviso cada 5 min).
        if (Date.now() - ultimoPing > 300000) {
          ultimoPing = Date.now();
          sesion().catch(function () { /* se detectará en la siguiente petición */ });
        }
      }
      ['click', 'keydown', 'pointerdown', 'scroll'].forEach(function (evento) {
        document.addEventListener(evento, actividad, { passive: true });
      });
      this.reprogramar();
    },

    reprogramar: function () {
      var self = this;
      global.clearTimeout(this.temporizadorAviso);
      global.clearTimeout(this.temporizadorCierre);
      var caja = document.getElementById('itt-sec-aviso');
      if (caja && !estado.redirigiendo) caja.hidden = true;
      this.temporizadorAviso = global.setTimeout(function () {
        aviso('Su sesión se cerrará en un minuto por inactividad. Realice cualquier acción para continuar.', 'alerta');
      }, (this.segundos - 60) * 1000);
      this.temporizadorCierre = global.setTimeout(function () {
        avisarYRedirigir(MENSAJES.SESSION_EXPIRED, loginSegunRuta());
        fetchSeguro(API + '/auth/logout', { method: 'POST', body: '{}', sinAviso: true }).catch(function () {});
      }, self.segundos * 1000);
    },
  };

  /* ───────────── Montaje automático ─────────────
     <div data-itt-nav></div>      barra de navegación
     <div data-itt-captcha></div>  captcha (accesible con ITTSecurity.captchaDe(el)) */
  var captchas = new Map();
  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-itt-captcha]').forEach(function (el) {
      captchas.set(el, Captcha.montar(el));
    });
    var navs = document.querySelectorAll('[data-itt-nav]');
    if (navs.length) {
      sesion().then(function (datos) {
        navs.forEach(function (el) { Navegacion.montar(el, datos); });
      }).catch(function () { /* sin sesión no se muestra la barra */ });
    }
  });

  global.ITTSecurity = {
    fetch: fetchSeguro,
    json: json,
    sesion: sesion,
    csrfToken: tokenCsrf,
    protegerPagina: protegerPagina,
    logout: logout,
    aviso: aviso,
    Captcha: Captcha,
    captchaDe: function (el) {
      var nodo = typeof el === 'string' ? document.querySelector(el) : el;
      return captchas.get(nodo) || null;
    },
    Navegacion: Navegacion,
    Inactividad: Inactividad,
  };
})(window);
