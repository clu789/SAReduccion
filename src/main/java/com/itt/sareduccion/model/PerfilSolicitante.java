package com.itt.sareduccion.model;

/** Datos de perfil que el panel muestra al estudiante o aspirante autenticado. */
public final class PerfilSolicitante {

    private final String tipo;
    private final String identificador;
    private final String nombre;
    private final String primerApellido;
    private final String segundoApellido;
    private final String carrera;
    private final Integer semestre;
    private final Integer periodo;
    private final String periodoDescripcion;
    private final Integer anio;

    public PerfilSolicitante(String tipo, String identificador, String nombre, String primerApellido,
            String segundoApellido, String carrera, Integer semestre, Integer periodo,
            String periodoDescripcion, Integer anio) {
        this.tipo = tipo;
        this.identificador = identificador;
        this.nombre = nombre;
        this.primerApellido = primerApellido;
        this.segundoApellido = segundoApellido;
        this.carrera = carrera;
        this.semestre = semestre;
        this.periodo = periodo;
        this.periodoDescripcion = periodoDescripcion;
        this.anio = anio;
    }

    public String getTipo() {
        return tipo;
    }

    public String getIdentificador() {
        return identificador;
    }

    public String getNombre() {
        return nombre;
    }

    public String getPrimerApellido() {
        return primerApellido;
    }

    public String getSegundoApellido() {
        return segundoApellido;
    }

    public String getNombreCompleto() {
        StringBuilder completo = new StringBuilder();
        for (String parte : new String[] { nombre, primerApellido, segundoApellido }) {
            if (parte != null && !parte.trim().isEmpty()) {
                if (completo.length() > 0) {
                    completo.append(' ');
                }
                completo.append(parte.trim());
            }
        }
        return completo.toString();
    }

    public String getCarrera() {
        return carrera;
    }

    public Integer getSemestre() {
        return semestre;
    }

    public Integer getPeriodo() {
        return periodo;
    }

    public String getPeriodoDescripcion() {
        return periodoDescripcion;
    }

    public Integer getAnio() {
        return anio;
    }
}
