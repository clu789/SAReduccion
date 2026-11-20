
'use strict';

const ListadoConvocatorias = {
  registros: [],
  versionDetalle: 0,
  versionEditar: 0,
  editando: null,
  guardando: false,

  async iniciar() {
    const autorizado = await AuthSession.requireRole(
      'ADMIN',
      'login.html',
      '/solicitante/login.html'
    );

    if (!autorizado) return;

    AuthSession.bindLogout('login.html', 'alertaListado');

    document.getElementById('buscarConvocatoria')
      .addEventListener('input', () => this.filtrar());

    document.getElementById('filtroAnio')
      .addEventListener('change', () => this.filtrar());

    document.getElementById('filtroEstado')
      .addEventListener('change', () => this.filtrar());

    document.getElementById('recargarConvocatorias')
      .addEventListener('click', () => this.cargar());

    document.getElementById('cuerpoConvocatorias')
      .addEventListener('click', event => {
        const boton = event.target.closest('button[data-id]');

        if (!boton) return;

        if (boton.dataset.accion === 'editar') {
          this.abrirEdicion(boton.dataset.id);
        } else {
          this.verDetalle(boton.dataset.id);
        }
      });

    const modal = document.getElementById('modalConvocatoria');

    const cerrar = () => {
      ++this.versionDetalle;
      modal.close();
    };

    document.getElementById('cerrarModalSuperior')
      .addEventListener('click', cerrar);

    document.getElementById('cerrarModalInferior')
      .addEventListener('click', cerrar);

    modal.addEventListener('close', () => {
      ++this.versionDetalle;
    });

    const modalEditar = document.getElementById('modalEditarConvocatoria');

    const cerrarEditar = () => {
      if (this.guardando) return;
      ++this.versionEditar;
      modalEditar.close();
    };

    document.getElementById('cerrarEditarSuperior')
      .addEventListener('click', cerrarEditar);

    document.getElementById('cancelarEditar')
      .addEventListener('click', cerrarEditar);

    modalEditar.addEventListener('cancel', event => {
      if (this.guardando) event.preventDefault();
    });

    modalEditar.addEventListener('close', () => {
      ++this.versionEditar;
      this.editando = null;
    });

    document.getElementById('formEditarConvocatoria')
      .addEventListener('submit', event => {
        event.preventDefault();
        this.guardarEdicion();
      });

    await this.cargar();
  },

  // Nombre del periodo académico.
  periodo(valor) {
    return Number(valor) === 1
      ? 'Agosto - Diciembre'
      : Number(valor) === 2
        ? 'Enero - Junio'
        : 'No disponible';
  },

  // Etiqueta de estado recibida desde el backend.
  etiquetaEstado(valor) {
    switch (valor) {
      case 'ACTIVA': return 'Activa';
      case 'PROXIMA': return 'Próxima';
      case 'CERRADA': return 'Cerrada';
      default: return 'Sin definir';
    }
  },

  // Crear un indicador sin insertar contenido de la API como HTML.
  indicadorEstado(valor) {
    const estadosValidos = ['ACTIVA', 'PROXIMA', 'CERRADA'];
    const estado = estadosValidos.includes(valor)
      ? valor
      : 'SIN_DEFINIR';

    const etiqueta = document.createElement('span');
    etiqueta.className =
      `convocatoria-estado convocatoria-estado--${estado.toLowerCase()}`;
    etiqueta.textContent = this.etiquetaEstado(estado);

    return etiqueta;
  },

  // Formato de fecha: YYYY-MM-DD a DD/MM/YYYY.
  fecha(valor) {
    if (!valor) return '—';

    const fecha = String(valor).slice(0, 10);
    const partes = /^(\d{4})-(\d{2})-(\d{2})$/.exec(fecha);

    return partes
      ? `${partes[3]}/${partes[2]}/${partes[1]}`
      : fecha;
  },

  // Formato de moneda mexicana.
  monto(valor) {
    if (valor == null || valor === '') {
      return 'No registrado';
    }

    const numero = Number(valor);

    return Number.isFinite(numero)
      ? numero.toLocaleString('es-MX', {
          style: 'currency',
          currency: 'MXN'
        })
      : 'No disponible';
  },

  // Mostrar u ocultar alertas del listado.
  alerta(mensaje) {
    const el = document.getElementById('alertaListado');

    el.textContent = mensaje;
    el.classList.toggle('d-none', !mensaje);
  },

  // Obtener convocatorias desde el backend.
  async cargar() {
    const cuerpo = document.getElementById(
      'cuerpoConvocatorias'
    );

    cuerpo.innerHTML = `
      <tr>
        <td colspan="9" class="text-center py-4">
          Cargando convocatorias...
        </td>
      </tr>
    `;

    this.alerta('');

    try {
      this.registros = await ConvocatoriasAdmin.listar();

      const filtro = document.getElementById('filtroAnio');
      const elegido = filtro.value;

      const anios = [
        ...new Set(
          this.registros.map(c => String(c.anio))
        )
      ].sort((a, b) => Number(b) - Number(a));

      filtro.replaceChildren(
        new Option('Todos los años', '')
      );

      anios.forEach(anio => {
        filtro.add(new Option(anio, anio));
      });

      filtro.value = anios.includes(elegido)
        ? elegido
        : '';

      this.filtrar();

    } catch (error) {
      this.registros = [];

      cuerpo.innerHTML = `
        <tr>
          <td colspan="9" class="text-center py-4">
            No fue posible cargar las convocatorias.
          </td>
        </tr>
      `;

      document.getElementById(
        'contadorConvocatorias'
      ).textContent = '—';

      this.alerta(
        error.message || 'Error al consultar convocatorias.'
      );

      console.error('Listado de convocatorias:', error);
    }
  },

  // Filtrar por búsqueda, año y estado.
  filtrar() {
    const texto = document.getElementById(
      'buscarConvocatoria'
    ).value.trim().toLocaleLowerCase('es-MX');

    const anio = document.getElementById(
      'filtroAnio'
    ).value;

    const estado = document.getElementById(
      'filtroEstado'
    ).value;

    const registros = this.registros.filter(c => {
      const coincideAnio =
        !anio || String(c.anio) === anio;

      const busqueda = `
        ${c.idConvocatoria}
        ${c.anio}
        ${c.periodo}
        ${this.periodo(c.periodo)}
      `.toLocaleLowerCase('es-MX');

      const coincideEstado =
        !estado || (c.estado || 'SIN_DEFINIR') === estado;

      return coincideAnio &&
        coincideEstado &&
        busqueda.includes(texto);
    });

    const cuerpo = document.getElementById(
      'cuerpoConvocatorias'
    );

    cuerpo.replaceChildren();

    for (const c of registros) {
      const fila = document.createElement('tr');

      const valores = [
        c.idConvocatoria,
        this.periodo(c.periodo),
        c.anio,
        this.fecha(c.fechaInicio),
        this.fecha(c.fechaFin),
        this.fecha(c.fechaLimiteAutorizacion),
        this.fecha(c.fechaLimiteAplicacion)
      ];

      valores.forEach(valor => {
        const celda = document.createElement('td');

        celda.textContent = String(valor ?? '—');
        fila.appendChild(celda);
      });

      // Columna de estado.
      const celdaEstado = document.createElement('td');
      celdaEstado.appendChild(
        this.indicadorEstado(c.estado)
      );

      fila.appendChild(celdaEstado);

      // Acciones de consulta y edición.
      const acciones = document.createElement('td');
      acciones.className = 'acciones-convocatoria';

      const boton = document.createElement('button');
      boton.type = 'button';
      boton.className = 'btn-blue-itt';
      boton.dataset.id = String(c.idConvocatoria);
      boton.dataset.accion = 'consultar';
      boton.title = 'Consultar convocatoria';

      boton.innerHTML = `
        <i class="fa-solid fa-eye"></i>
        Ver
      `;

      acciones.appendChild(boton);

      const editar = document.createElement('button');
      editar.type = 'button';
      editar.className = 'btn-gray-itt';
      editar.dataset.id = String(c.idConvocatoria);
      editar.dataset.accion = 'editar';
      editar.title = 'Editar fechas o montos de la convocatoria';
      editar.innerHTML =
        '<i class="fa-solid fa-pen-to-square"></i> Editar';

      acciones.appendChild(editar);
      fila.appendChild(acciones);
      cuerpo.appendChild(fila);
    }

    if (!registros.length) {
      const fila = document.createElement('tr');
      const celda = document.createElement('td');

      celda.colSpan = 9;
      celda.className = 'text-center py-4';

      celda.textContent =
        'No se encontraron convocatorias con esos filtros.';

      fila.appendChild(celda);
      cuerpo.appendChild(fila);
    }

    document.getElementById(
      'contadorConvocatorias'
    ).textContent = `${registros.length} registro(s)`;
  },

  // Consultar detalle y montos de una convocatoria.
  async verDetalle(id) {
    const modal = document.getElementById(
      'modalConvocatoria'
    );

    const version = ++this.versionDetalle;

    document.getElementById(
      'tituloModalConvocatoria'
    ).textContent = `Convocatoria #${id}`;

    document.getElementById(
      'alertaDetalle'
    ).classList.add('d-none');

    const campos = [
      'detPeriodo',
      'detAnio',
      'detInicio',
      'detFin',
      'detAutorizacion',
      'detAplicacion',
      'detTrabajador',
      'detHomologado'
    ];

    campos.forEach(nombre => {
      document.getElementById(nombre).textContent =
        'Cargando...';
    });

    const estadoDetalle = document.getElementById(
      'detEstado'
    );

    estadoDetalle.textContent = 'Cargando...';

    if (!modal.open) {
      modal.showModal();
    }

    try {
      const c = await ConvocatoriasAdmin.consultar(id);

      if (
        version !== this.versionDetalle ||
        !modal.open
      ) {
        return;
      }

      const montos = Array.isArray(c.montos)
        ? c.montos
        : [];

      const trabajador = montos.find(
        m => Number(m.idTipoSolicitud) === 1
      );

      const homologado = montos.find(
        m => Number(m.idTipoSolicitud) === 2
      );

      const datos = {
        detPeriodo: this.periodo(c.periodo),
        detAnio: c.anio,
        detInicio: this.fecha(c.fechaInicio),
        detFin: this.fecha(c.fechaFin),
        detAutorizacion: this.fecha(
          c.fechaLimiteAutorizacion
        ),
        detAplicacion: this.fecha(
          c.fechaLimiteAplicacion
        ),
        detTrabajador: this.monto(
          trabajador?.montoDeduccion
        ),
        detHomologado: this.monto(
          homologado?.montoDeduccion
        )
      };

      Object.entries(datos).forEach(([nombre, valor]) => {
        document.getElementById(nombre).textContent =
          String(valor ?? '—');
      });

      // Mostrar estado en el modal.
      estadoDetalle.replaceChildren(
        this.indicadorEstado(c.estado)
      );

    } catch (error) {
      if (
        version !== this.versionDetalle ||
        !modal.open
      ) {
        return;
      }

      const alerta = document.getElementById(
        'alertaDetalle'
      );

      alerta.textContent =
        error.message ||
        'No se pudo consultar la convocatoria.';

      alerta.classList.remove('d-none');

      console.error('Detalle de convocatoria:', error);
    }
  },

  // Mostrar alertas dentro del formulario de edición.
  avisoEdicion(mensaje, exito = false) {
    const error = document.getElementById('alertaEditar');
    const correcto = document.getElementById('exitoEditar');

    error.classList.add('d-none');
    correcto.classList.add('d-none');

    if (!mensaje) return;

    const destino = exito ? correcto : error;
    destino.textContent = mensaje;
    destino.classList.remove('d-none');
  },

  // Consultar la convocatoria y precargar el formulario.
  async abrirEdicion(id) {
    const modal = document.getElementById(
      'modalEditarConvocatoria'
    );

    const version = ++this.versionEditar;
    this.editando = null;
    this.avisoEdicion('');

    document.getElementById(
      'tituloEditarConvocatoria'
    ).textContent = `Editar convocatoria #${id}`;

    const guardar = document.getElementById('guardarEditar');
    guardar.disabled = true;
    guardar.textContent = 'Cargando...';

    if (!modal.open) modal.showModal();

    try {
      const c = await ConvocatoriasAdmin.consultar(id);

      if (version !== this.versionEditar || !modal.open) {
        return;
      }

      const montos = Array.isArray(c.montos) ? c.montos : [];

      const trabajador = montos.find(
        m => Number(m.idTipoSolicitud) === 1
      );

      const homologado = montos.find(
        m => Number(m.idTipoSolicitud) === 2
      );

      if (
        !trabajador ||
        !homologado ||
        trabajador.montoDeduccion == null ||
        homologado.montoDeduccion == null
      ) {
        throw new Error(
          'No se encontraron ambos montos oficiales para esta convocatoria.'
        );
      }

      this.editando = {
        id: Number(id),
        original: c
      };

      document.getElementById('editarPeriodo').textContent =
        this.periodo(c.periodo);

      document.getElementById('editarAnio').textContent =
        String(c.anio);

      document.getElementById('editarEstado').replaceChildren(
        this.indicadorEstado(c.estado)
      );

      const valores = {
        editarInicio: c.fechaInicio,
        editarFin: c.fechaFin,
        editarAutorizacion: c.fechaLimiteAutorizacion,
        editarAplicacion: c.fechaLimiteAplicacion,
        editarTrabajador: trabajador.montoDeduccion,
        editarHomologado: homologado.montoDeduccion
      };

      Object.entries(valores).forEach(([campo, valor]) => {
        const esMonto = [
          'editarTrabajador',
          'editarHomologado'
        ].includes(campo);

        document.getElementById(campo).value = esMonto
          ? String(valor ?? '')
          : String(valor ?? '').slice(0, 10);
      });

      const fechas = c.puedeEditarFechas === true;
      const montosEditables = c.puedeEditarMontos === true;

      document.getElementById(
        'grupoEditarFechas'
      ).disabled = !fechas;

      document.getElementById(
        'grupoEditarMontos'
      ).disabled = !montosEditables;

      document.getElementById(
        'avisoEditarFechas'
      ).textContent = fechas
        ? 'Puedes modificar estas fechas.'
        : 'La edición de fechas no está permitida para esta convocatoria.';

      document.getElementById(
        'avisoEditarMontos'
      ).textContent = montosEditables
        ? 'Puedes modificar los montos oficiales.'
        : 'La edición de montos no está permitida para esta convocatoria.';

      guardar.disabled = !fechas && !montosEditables;
      guardar.innerHTML =
        '<i class="fa-solid fa-floppy-disk"></i> Guardar cambios';

      if (!fechas && !montosEditables) {
        this.avisoEdicion(
          'Esta convocatoria no admite modificaciones actualmente.'
        );
      }

    } catch (error) {
      if (version !== this.versionEditar || !modal.open) {
        return;
      }

      this.avisoEdicion(
        error.message || 'No fue posible cargar los datos de edición.'
      );

      guardar.disabled = true;
      guardar.textContent = 'Guardar cambios';
    }
  },

  // Validar y enviar la actualización al backend.
  async guardarEdicion() {
    if (this.guardando || !this.editando) return;

    const formulario = document.getElementById(
      'formEditarConvocatoria'
    );

    const { id, original } = this.editando;

    const permisoFechas = original.puedeEditarFechas === true;
    const permisoMontos = original.puedeEditarMontos === true;

    if (!permisoFechas && !permisoMontos) return;

    this.avisoEdicion('');

    const montos = original.montos || [];

    const montoOriginal = tipo =>
      montos.find(
        m => Number(m.idTipoSolicitud) === tipo
      )?.montoDeduccion;

    const valor = campo =>
      document.getElementById(campo).value;

    const datos = {
      fechaInicio: permisoFechas
        ? valor('editarInicio')
        : original.fechaInicio,

      fechaFin: permisoFechas
        ? valor('editarFin')
        : original.fechaFin,

      fechaLimiteAutorizacion: permisoFechas
        ? valor('editarAutorizacion')
        : original.fechaLimiteAutorizacion,

      fechaLimiteAplicacion: permisoFechas
        ? valor('editarAplicacion')
        : original.fechaLimiteAplicacion,

      montoTrabajador: permisoMontos
        ? Number(valor('editarTrabajador'))
        : Number(montoOriginal(1)),

      montoHomologado: permisoMontos
        ? Number(valor('editarHomologado'))
        : Number(montoOriginal(2))
    };

    if (!formulario.checkValidity()) {
      formulario.reportValidity();
      this.avisoEdicion(
        'Revisa los campos obligatorios y los límites permitidos.'
      );
      return;
    }

    const fechas = [
      datos.fechaInicio,
      datos.fechaFin,
      datos.fechaLimiteAutorizacion,
      datos.fechaLimiteAplicacion
    ];

    if (
      fechas.some(f => !/^\d{4}-\d{2}-\d{2}$/.test(f)) ||
      fechas.some((f, i) => i > 0 && f < fechas[i - 1])
    ) {
      this.avisoEdicion(
        'Las fechas deben mantener el orden: inicio, fin, autorización y aplicación.'
      );
      return;
    }

    if (
      [
        datos.montoTrabajador,
        datos.montoHomologado
      ].some(m =>
        !Number.isFinite(m) ||
        m < 0 ||
        m >= 100000000 ||
        Math.round(m * 100) !== m * 100
      )
    ) {
      this.avisoEdicion(
        'Los montos deben ser no negativos, menores a 100000000 y con máximo dos decimales.'
      );
      return;
    }

    const fechasOriginales = [
      original.fechaInicio,
      original.fechaFin,
      original.fechaLimiteAutorizacion,
      original.fechaLimiteAplicacion
    ];

    const sinCambios =
      fechas.every((f, i) => f === fechasOriginales[i]) &&
      datos.montoTrabajador === Number(montoOriginal(1)) &&
      datos.montoHomologado === Number(montoOriginal(2));

    if (sinCambios) {
      this.avisoEdicion('No hay cambios por guardar.', true);
      return;
    }

    const version = this.versionEditar;
    const boton = document.getElementById('guardarEditar');

    this.guardando = true;
    boton.disabled = true;
    boton.textContent = 'Guardando...';

    try {
      const resultado = await Http.put(
        `/admin/convocatorias/${id}`,
        datos
      );

      if (version !== this.versionEditar) return;

      this.avisoEdicion(
        resultado?.mensaje ||
        'Convocatoria actualizada correctamente.',
        true
      );

      // Recargar listado para reflejar las nuevas fechas.
      await this.cargar();

      if (version === this.versionEditar) {
        document.getElementById(
          'modalEditarConvocatoria'
        ).close();

        this.editando = null;
        this.alerta('');

        const aviso = document.getElementById('exitoListado');

        aviso.textContent =
          resultado?.mensaje ||
          'Convocatoria actualizada correctamente.';

        aviso.classList.remove('d-none');
      }

    } catch (error) {
      if (version === this.versionEditar) {
        this.avisoEdicion(
          error.message || 'No se pudo guardar la convocatoria.'
        );
      }

    } finally {
      this.guardando = false;

      boton.disabled =
        !this.editando ||
        (!permisoFechas && !permisoMontos);

      boton.innerHTML =
        '<i class="fa-solid fa-floppy-disk"></i> Guardar cambios';
    }
  }
};

document.addEventListener('DOMContentLoaded', () => {
  if (
    document.body.id === 'pagina-listado-convocatorias'
  ) {
    ListadoConvocatorias.iniciar();
  }
});
