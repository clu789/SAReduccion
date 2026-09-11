package utils;

public class GenericResponse<T> {

    private int status;
    private String mensaje;
    private T responseObject;

    //Constantes para controlar el estatus.
    public static final int STATUS_EXITO = 0;
    public static final int STATUS_SESION_EXPIRO = -100;
    public static final int STATUS_CREDEN_INV = -200;
    public static final int STATUS_ERROR_GENERAL = -300;
    public static final int NO_DISPONIBILIDAD_EMP = -400;
    //Constantes para controlar el mensaje}.
    private static final String MENSAJE_SESION_EXPIRO = "Su sesión ha expirado o sus credenciales son insuficientes.";
    private static final String MENSAJE_CREDEN_INV = "Ocurrió un error al validar sus credenciales.";
    private static final String MENSAJE_NO_DISP_EMP = "Para continuar debe asignar horario de disponibilidad al empleado";

    public GenericResponse() {
        this.status = STATUS_EXITO;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
        switch (this.status) {
            case STATUS_SESION_EXPIRO:
                this.mensaje = MENSAJE_SESION_EXPIRO;
                break;
            case STATUS_CREDEN_INV:
                this.mensaje = MENSAJE_CREDEN_INV;
                break;
            case NO_DISPONIBILIDAD_EMP:
                this.mensaje = MENSAJE_NO_DISP_EMP;

        }
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {

        if (this.status == STATUS_ERROR_GENERAL) {
            this.mensaje = mensaje;
        } else {
            this.mensaje = mensaje;
        }

    }

    public T getResponseObject() {
        return responseObject;
    }

    public void setResponseObject(T responseObject) {
        this.responseObject = responseObject;
    }

}
