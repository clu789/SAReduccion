<%-- 
    Document   : error_reporte_view
    Created on : 12/06/2018, 10:12:14 AM
    Author     : JuanManuel
--%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <link href="/CDN-ITT/css/base.estandarITT.css" rel ="stylesheet">
        <link href="/CDN-ITT/css/general.estandarITT.css" rel ="stylesheet">
        <link href="/CDN-ITT/css/jquery-ui.estandarITT.css" rel ="stylesheet">
        <link href="/CDN-ITT/css/font-awesome.estandarITT.css" rel ="stylesheet">
        <link href="/CDN-ITT/css/spinner.estandarITT.css" rel ="stylesheet">
        <title>Administrador</title>
    </head>
    <body>
        <%@ include file="/views/templates/header.jsp" %>    
        <%@ include file="/views/templates/navbarError.jsp" %>
        <div class="container">
            <div class="panel panel-primary">
                <div class="panel-heading">
                    <div class="panel-title">
                        <b>Error</b>
                    </div>
                </div>
                <div class="panel-body">

                    <div class="col-md-2">
                    </div>
                    <div class="col-md-8" align="center">  
                        <h2>${mensajeError}</h2>
                        <p class="mensajeDatos"> 
                            Error al generar el Reporte solicitado. <br/>

                            Por favor, inténtelo de nuevo.
                            Si el error persiste consulte al administrador.
                        </p>           
                    </div>
                    <div class="col-md-2"></div>
                </div>
            </div>
        </div>
        <%@ include file="/views/templates/footer.jsp" %>
        <script src ="\CDN-ITT\js\jquery.estandarITT.js"></script>
        <script src ="\CDN-ITT\js\base.estandarITT.js"></script>
        <script src ="\CDN-ITT\js\spinner.estandarITT.js"></script>

    </body>
</html>