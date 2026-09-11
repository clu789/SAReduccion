package servlets.acceso;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import mx.edu.ittoluca.logutils.Logger;

public class MantenerSesion_Srv extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession();
        int miliseconds = (session.getMaxInactiveInterval() / 60) * 60000;
        try (PrintWriter writer = response.getWriter()) {
            writer.print(miliseconds);
            writer.flush();
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Registrar en el log de la aplicación.
        Logger log = new Logger();
        // El nombre del archivo debera llevar un indicador para que sea distinto, este archivo sera unico para registrar las veces que renovo sesión el usuario.
        log.registrarError(3, "Sesión renovada", "Ejemplo_Session_renovacion_sesion", "session", "usuario");
    }

}
