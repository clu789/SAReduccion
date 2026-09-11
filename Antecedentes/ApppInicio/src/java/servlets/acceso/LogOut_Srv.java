package servlets.acceso;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LogOut_Srv extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String opc = request.getParameter("opc");
        HttpSession sesion = request.getSession(false);

        String redirect = "/ITTLogin";

        if (sesion != null) {
            sesion.invalidate();
        }

        switch (opc) {
            case "app":
                redirect += "/apps";
                break;
            case "log":
                redirect += "/";
                break;
        }

        response.sendRedirect(redirect);
    }

}
