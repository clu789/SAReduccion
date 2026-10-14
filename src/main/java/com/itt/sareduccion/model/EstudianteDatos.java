package com.itt.sareduccion.model;

public final class EstudianteDatos {

	private final String numeroControl;
	private final String nombre;
	private final String primerApellido;
	private final String segundoApellido;
	private final String carrera;
	private final Integer semestre;

	public EstudianteDatos(String numeroControl, String nombre, String primerApellido,
			String segundoApellido, String carrera, Integer semestre) {
		this.numeroControl = numeroControl;
		this.nombre = nombre;
		this.primerApellido = primerApellido;
		this.segundoApellido = segundoApellido;
		this.carrera = carrera;
		this.semestre = semestre;
	}

	public String getNumeroControl() {
		return numeroControl;
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

	public String getCarrera() {
		return carrera;
	}

	public Integer getSemestre() {
		return semestre;
	}
}