'use strict';

const ListadoConvocatorias = {
  registros: [],
  versionDetalle: 0,

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

    document.getElementById('recargarConvocatorias')
      .addEventListener('click', () => this.cargar());

    document.getElementById('cuerpoConvocatorias')
      .addEventListener('click', event => {
        const boton = event.target.closest('button[data-id]');

        if (boton) {
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

  // Mostrar u ocultar alertas.
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
        <td colspan="8" class="text-center py-4">
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
          <td colspan="8" class="text-center py-4">
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

  filtrar() {
    const texto = document.getElementById(
      'buscarConvocatoria'
    ).value.trim().toLocaleLowerCase('es-MX');

    const anio = document.getElementById(
      'filtroAnio'
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

      return coincideAnio && busqueda.includes(texto);
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

      const acciones = document.createElement('td');
      const boton = document.createElement('button');

      boton.type = 'button';
      boton.className = 'btn-blue-itt';
      boton.dataset.id = String(c.idConvocatoria);
      boton.title = 'Consultar convocatoria';

      boton.innerHTML = `
        <i class="fa-solid fa-eye"></i>
        Consultar
      `;

      acciones.appendChild(boton);
      fila.appendChild(acciones);
      cuerpo.appendChild(fila);
    }

    if (!registros.length) {
      const fila = document.createElement('tr');
      const celda = document.createElement('td');

      celda.colSpan = 8;
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
  }
};

document.addEventListener('DOMContentLoaded', () => {
  if (
    document.body.id === 'pagina-listado-convocatorias'
  ) {
    ListadoConvocatorias.iniciar();
  }
});
