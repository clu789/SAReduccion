
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<nav class="navbar navbar-default" role="navigation">
    <div class="container-fluid">
        <!-- Brand and toggle get grouped for better mobile display -->
        <div class="navbar-header">
            <button type="button" class="navbar-toggle collapsed" data-toggle="collapse" data-target="#bs-example-navbar-collapse-1">
                <span class="sr-only">Toggle navigation</span>
                <span class="icon-bar"></span>
                <span class="icon-bar"></span>
                <span class="icon-bar"></span>
            </button>
            <a class="navbar-brand" href="#" id="hastaAqui">Nombre de Aplicación</a>
        </div>

        <div class="collapse navbar-collapse" id="bs-example-navbar-collapse-1">            
            <ul class="nav navbar-nav navbar-right">                            
                <li class="dropdown">
                    <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false">
                        <i class="fa fa-gear"></i>
                        Opciones
                        <b class="caret"></b>
                    </a>
                    <ul class="dropdown-menu" role="menu">
                        <li id="home"><a href="../../app/modulo/Home.do"><i class="fa fa-home"></i>
                                Home</a></li>                                                                                                                                                      
                        <li><a href="../../app/sesion/logOut?opc=app"><i class="fa fa-sitemap"></i>
                                Regresar</a></li>                                                                                                                                                
                        <li><a href="../../app/sesion/logOut?opc=log"><i class="fa fa-sign-out"></i>
                                Salir</a></li>                                                                                                                                           
            </ul>
        </div> <!-- /.navbar-collapse --> 
    </div><!-- /.container-fluid -->
</nav>

