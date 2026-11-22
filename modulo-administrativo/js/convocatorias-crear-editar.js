'use strict';

(function () {
  if (!document.body || document.body.id !== 'pagina-convocatorias-admin') return;

  function mostrarAlerta(mensaje, tipo = 'danger') {
    const alerta = document.getElementById('alertaValidacion');
    if (!alerta) return;

    const tiposPermitidos = ['success', 'danger', 'warning', 'info'];
    const tipoSeguro = tiposPermitidos.includes(tipo) ? tipo : 'danger';

    alerta.className = `alert alert-${tipoSeguro} mt-3 mb-2 small`;
    alerta.textContent = mensaje;
    alerta.classList.remove('d-none');

    alerta.setAttribute(
      'role',
      tipoSeguro === 'danger' ? 'alert' : 'status'
    );

    alerta.scrollIntoView({
      behavior: 'smooth',
      block: 'nearest',
    });
  }

  function ocultarAlerta() {
    const alerta = document.getElementById('alertaValidacion');
    if (alerta) alerta.classList.add('d-none');
  }

  function restablecerBotonGuardar(btnGuardar, deshabilitado) {
    if (!btnGuardar) return;
    btnGuardar.disabled = deshabilitado;
    const icono = document.createElement('i');
    icono.className = 'fa-solid fa-floppy-disk';
    icono.setAttribute('aria-hidden', 'true');
    btnGuardar.replaceChildren(icono, document.createTextNode(' Guardar convocatoria'));
  }

  const btnLimpiar = document.getElementById('btnLimpiarConvocatoria');
  if (btnLimpiar) {
    btnLimpiar.addEventListener('click', ocultarAlerta);
  }

  const formConvocatoria = document.getElementById('formConvocatoria');
  if (!formConvocatoria) return;

  formConvocatoria.addEventListener('submit', async function (e) {
    e.preventDefault();
    ocultarAlerta();

    const btnGuardar = document.getElementById('btnGuardarConvocatoria');
    if (!btnGuardar || btnGuardar.disabled) return;

    const periodoTexto = document.getElementById('periodo').value;
    const anioTexto = document.getElementById('anio').value.trim();
    const fechaInicio = document.getElementById('fechaInicio').value;
    const fechaFin = document.getElementById('fechaFin').value;
    const fechaAutorizacion = document.getElementById('fechaAutorizacion').value;
    const fechaAplicacion = document.getElementById('fechaAplicacion').value;
    const montoInternoTexto = document.getElementById('montoInterno').value.trim();
    const montoHomologadoTexto = document.getElementById('montoHomologado').value.trim();

    if (!periodoTexto || !anioTexto || !fechaInicio || !fechaFin ||
        !fechaAutorizacion || !fechaAplicacion ||
        !montoInternoTexto || !montoHomologadoTexto) {
      mostrarAlerta('Todos los campos son obligatorios. Complete la información antes de guardar.');
      return;
    }

    const periodo = Number(periodoTexto);
    const anio = Number(anioTexto);
    const montoTrabajador = Number(montoInternoTexto);
    const montoHomologado = Number(montoHomologadoTexto);

    if (![1, 2].includes(periodo)) {
      mostrarAlerta('Seleccione un periodo académico válido.');
      return;
    }

    if (!Number.isInteger(anio) || anio < 2000 || anio > 2200) {
      mostrarAlerta('El año académico debe ser un número entero entre 2000 y 2200.');
      return;
    }

    if (fechaInicio > fechaFin ||
        fechaFin > fechaAutorizacion ||
        fechaAutorizacion > fechaAplicacion) {
      mostrarAlerta(
        'Orden de fechas inválido. Debe cumplirse: Inicio de solicitudes ≤ Fin de solicitudes ≤ Límite de autorización ≤ Límite de aplicación.'
      );
      return;
    }

    const hoy = new Date();
    const fechaHoy = [
      hoy.getFullYear(),
      String(hoy.getMonth() + 1).padStart(2, '0'),
      String(hoy.getDate()).padStart(2, '0'),
    ].join('-');

    if (fechaAutorizacion < fechaHoy) {
      mostrarAlerta('La fecha límite de autorización no puede ser anterior a la fecha actual.');
      return;
    }

    const montos = [
      { nombre: 'Personal interno', valor: montoTrabajador, texto: montoInternoTexto },
      { nombre: 'Personal homologado', valor: montoHomologado, texto: montoHomologadoTexto },
    ];

    for (const monto of montos) {
      if (!Number.isFinite(monto.valor) ||
          monto.valor < 0 ||
          monto.valor >= 100000000 ||
          !/^\d+(\.\d{1,2})?$/.test(monto.texto)) {
        mostrarAlerta(
          `El monto de ${monto.nombre} debe ser positivo o cero, menor a 100,000,000 y tener como máximo dos decimales.`
        );
        return;
      }
    }

    const datos = {
      periodo,
      anio,
      fechaInicio,
      fechaFin,
      fechaLimiteAutorizacion: fechaAutorizacion,
      fechaLimiteAplicacion: fechaAplicacion,
      montoTrabajador,
      montoHomologado,
    };

    btnGuardar.disabled = true;
    btnGuardar.textContent = 'Guardando convocatoria...';
    let errorSesionInvalida = false;

    try {
      const respuesta = await Http.post('/admin/convocatorias', datos);
      mostrarAlerta(
        `Convocatoria creada correctamente. Identificador: ${respuesta.idConvocatoria}.`,
        'success'
      );
      formConvocatoria.reset();
    } catch (error) {
      errorSesionInvalida = error.status === 401 || error.status === 403;
      if (error.status === 401) {
        mostrarAlerta('Su sesión ha expirado. Inicie sesión nuevamente.');
      } else if (error.status === 403) {
        mostrarAlerta('No tiene autorización para realizar esta operación o la sesión de seguridad ha expirado.');
      } else {
        mostrarAlerta(error.message || 'No fue posible registrar la convocatoria.');
      }
    } finally {
      restablecerBotonGuardar(btnGuardar, errorSesionInvalida);
    }
  });
})();
