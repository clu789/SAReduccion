
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
         <link rel="stylesheet" href="/CDN-ITT/css/base.estandarITT.css"/>
        <link rel="stylesheet" href="/CDN-ITT/css/bootstrap-datepicker.estandarITT.css"/>
        <link rel="stylesheet" href="/CDN-ITT/css/botones.estandarITT.css"/>
        <link rel="stylesheet" href="/CDN-ITT/css/catalogos-modal.estandarITT.css"/>
        <link rel="stylesheet" href="/CDN-ITT/css/catalogos-tablas.estandarITT.css"/>
        <link rel="stylesheet" href="/CDN-ITT/css/font-awesome.estandarITT.css"/>
        <link rel="stylesheet" href="/CDN-ITT/css/general.estandarITT.css"/>
        <link rel="stylesheet" href="/CDN-ITT/css/jquery-ui.estandarITT.css"/>
        <link rel="stylesheet" href="/CDN-ITT/css/spinner.estandarITT.css"/>
        <link rel="stylesheet" href="/CDN-ITT/css/tablas.estandarITT.css"/>

        <link href="css/cssgenerados/RegistroParametros.css" rel="stylesheet">

        <link rel="icon" href="../../../img/logo-final.png">
        <title>Acceso denegado</title>
    </head>
    
    <body>
        <%@include file="../../templates/header.jsp" %>
         <div class="container">
            <%@include file="../../templates/navbar.jsp" %>
            <div class="panel panel-primary">
                <div class="panel-heading">
                    <h1 class="panel-title">Acceso denegado</h1>
                </div>

        <div class="panel-body">
            <div class="col-md-2"></div>
            <div class="col-md-8" align="center">
                <p class="mensajeDatos">Acceso Denegado</p>
                <div class="panel panel-default">
                    <div class="panel-body">
                        <p class="errorReporte">${mensajeAcceso}</p>
                        <br/>
                        <a href="/ITTLogin/apps" class="btn btn-lg btn-primary">Iniciar Sesión.</a>
                    </div>
                </div>
                        <p class="mensajeDatos">Por favor, vuelva a iniciar sesión.<br/>
                            Si los problemas persisten consulte a su administrador.</p>        
            </div>
         <div class="col-md-2"></div>
        </div>
            </div>
 
            
            
            
        </div>

        <%@include file="../../templates/footer.jsp"%>

        <script       src="/CDN-ITT/js/jquery.estandarITT.js"></script>
        <script       src="/CDN-ITT/js/jquery-ui.estandarITT.js"></script>
        <script       src="/CDN-ITT/js/base.estandarITT.js"></script>
        <script       src="/CDN-ITT/js/jquery.dataTables.estandarITT.js"></script>
        <script async src="/CDN-ITT/js/tablas.estandarITT.js"></script>
        <script async src="/CDN-ITT/js/catalogos-tablas.estandarITT.js"></script>
        <script       src="/CDN-ITT/js/bootstrap-datepicker.estandarITT.js"></script>
        <script       src="/CDN-ITT/js/bootstrap-datepicker.es.estandarITT.js"></script>

        <script async src="js/jsgenerados/RegistroParametros.js"></script>

    

    </body>
</html>