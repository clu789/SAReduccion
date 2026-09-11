package filters.access;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import pojo.Usuario;
import utils.Utilidades;

public class AccessFilter implements Filter {

    // The filter configuration object we are associated with.  If
    // this value is null, this filter instance is not currently
    // configured. 
    private FilterConfig filterConfig = null;

    public AccessFilter() {
    }

    /**
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @param chain The filter chain we are processing
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain)
            throws IOException, ServletException {

        //Variable que controla el resultado del filtro.
        boolean sendRedirect = false;
        String mensaje = "";

        String redirect = "/app/modulo/Home.do";
        //String redirect = "/app/modulo/procesarCierre.do";
        //String redirect = "/app/modulo/verificarCierre.do";

        //Se obtienen los objetos Http.
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        //Se obtiene la sesión.
        HttpSession sesion = httpRequest.getSession();

        String token = httpRequest.getParameter("d");

        if (token != null) {
            Usuario user = (Usuario) sesion.getAttribute("usuario");
            if (user != null && user.isAuth()) {

                sendRedirect = true;

            } else {
                user = Utilidades.validateCredentials(token);
                if (user != null) {
                    sendRedirect = true;
                    sesion.setAttribute("usuario", user);
                } else {
                    mensaje = "Ocurrió un error al validar sus credenciales";
                }
            }

        } else {
            mensaje = "Su sesión ha expirado o sus credenciales son insuficientes";
        }

        if (sendRedirect) {
            httpResponse.sendRedirect(httpRequest.getContextPath() + redirect);

        } else {
            //Este mensaje aparecerá en la página de error configurada. Si el mensaje en EL
            //es diferente a ${mensajeAcceso} es importante cambiarlo aquí.
            httpRequest.setAttribute("mensajeAcceso", mensaje);
            chain.doFilter(request, response);
        }

    }

    /**
     * Return the filter configuration object for this filter.
     */
    public FilterConfig getFilterConfig() {
        return (this.filterConfig);
    }

    /**
     * Set the filter configuration object for this filter.
     *
     * @param filterConfig The filter configuration object
     */
    public void setFilterConfig(FilterConfig filterConfig) {
        this.filterConfig = filterConfig;
    }

    /**
     * Destroy method for this filter
     */
    public void destroy() {
    }

    /**
     * Init method for this filter
     */
    public void init(FilterConfig filterConfig) {
        this.filterConfig = filterConfig;
    }
}
