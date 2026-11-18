package com.itt.sareduccion.model;

/**
 * Datos para la tarjeta del solicitante en el formulario. Un estudiante trae
 * nombre, carrera y semestre; un aspirante solo ficha, periodo y anio.
 */
public final class PerfilFormulario {

    private final String rol;
    private final String identidad;
    private final String nombre;
    private final String carrera;
    private final Integer semestre;
    private final Integer periodo;
    private final Integer anio;

    public PerfilFormulario(String rol, String identidad, String nombre, String carrera, Integer semestre,
            Integer periodo, Integer anio) {
        this.rol = rol;
        this.identidad = identidad;
        this.nombre = nombre;
        this.carrera = carrera;
        this.semestre = semestre;
        this.periodo = periodo;
        this.anio = anio;
    }

    /** ESTUDIANTE o ASPIRANTE. */
    public String getRol() {
        return rol;
    }

    /** Numero de control (estudiante) o ficha (aspirante). */
    public String getIdentidad() {
        return identidad;
    }

    public String getNombre() {
        return nombre;
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

    public Integer getAnio() {
        return anio;
    }
}
