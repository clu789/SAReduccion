package filters.session;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.PrintWriter;
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
import utils.GenericResponse;

public class SessionFilter implements Filter {

    // The filter configuration object we are associated with.  If
    // this value is null, this filter instance is not currently
    // configured. 
    private FilterConfig filterConfig = null;

    public SessionFilter() {
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

        //Objeto respuesta.
        GenericResponse resp = new GenericResponse();
        String headerName = "X-Requested-With";

        //Se obtienen los objetos request y response.
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        //Se obtiene la sesión.
        HttpSession sesion = httpRequest.getSession(false);
        if (sesion != null) {
            //Se obtienen las credenciales de la sesión para valdiarlas.
            Usuario usuario = (Usuario) sesion.getAttribute("usuario");
            if (usuario == null || !usuario.isAuth()) {
                resp.setStatus(GenericResponse.STATUS_CREDEN_INV);
            }
        } else {
            resp.setStatus(GenericResponse.STATUS_SESION_EXPIRO);
        }

        //Si la validación de la sesión falla:
        if (resp.getStatus() != GenericResponse.STATUS_EXITO) {

            //Se verifica si la petición es AJAX.
            String headerValue = httpRequest.getHeader(headerName);
            if (headerValue != null && "XMLHttpRequest".equals(headerValue)) {
                //Se envía la respuesta en formato JSON
                try (PrintWriter out = httpResponse.getWriter()) {
                    httpResponse.setContentType("application/json");
                    String json = new Gson().toJson(resp);
                    out.print(json);
                    out.close();
                }
            } else {
                //Si la petición no es AJAX, se envía un redirect
                httpResponse.sendRedirect(httpRequest.getContextPath());
            }

        } else {
            //Si la validación de la sesión fue exitosa, se permite el paso de la petición:
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
     * Init method for this filter
     */
    @Override
    public void init(FilterConfig filterConfig) {
        this.filterConfig = filterConfig;
    }

    @Override
    public void destroy() {
        this.filterConfig = null;
    }

}
