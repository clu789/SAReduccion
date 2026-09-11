/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

$( document ).ready(function() {
    $("#pageLoaderc").css("display", "none");
    $("#pageLoader").css("display", "none");
    $('ul.tabs li a:first').addClass('activetabs');
    $('.row').hide();
    $('.row:first').show();
    
    $('ul.tabs li a').click(function(){
        $('ul.tabs li a').removeClass('activetabs');
        $(this).addClass('activetabs');
        $('.row').hide(); 
        
        var activeTab = $(this).attr('href');
        $(activeTab).show();
        return false;
    });
});

$("#process-periodo").on("click",function(){
    var mensaje = "El siguiente proceso ejecutará el cierre de semestre: <br/><br/>";
        mensaje += "¿Desea continuar?";
        bootBoxConfirm('<i class="fa fa-lg fa-info-circle iconoInfo"></i> Confirmación requerida', mensaje, procesarcierre);
    
});

var estado=document.getElementById('accion-cierre');

function procesarcierre(){
    
     $("#pageLoader").css("display", "block");
     
     setTimeout(()=>{
         
     estado.textContent= "Actualizar estatus de alumnos egresados a egresados sin certificado";
     
     },2000);
     
     $.ajax({
            type: "GET",
            async: true,
            url: "../../Procesar_Cierre_Srv",
            dataType: "json",
            
            success: function (response) {
               
                if (response.status === 0) {
                barra("20");
                estado.textContent= "Actualizar estatus de baja temporal a baja definitiva"; 
                bajaT();
            }else{
                
                bootBoxAlert('<i class="fa fa-lg fa-check-circle iconoCorrecto"></i> Error', "No se pudo realizar la petición. Consulte al administrador del sistema.");
                $("#pageLoader").css("display", "none");
            }
            
            },
            error: function (XMLHttpRequest, textStatus, errorThrown) {
                bootBoxAlert('<i class="fa fa-lg fa-times-circle iconoError"></i> Error', "Ocurrió un error al realizar la petición:<br/>" +
                        "Estatus: " + textStatus + "<br/>" +
                        "Error: " + errorThrown + "<br/>" +
                        "XML"+XMLHttpRequest+"<br/>"+
                        "Por favor, notifique a su administrador de este error. <br/>");
                $("#pageLoader").css("display", "none");
            }

        });
      
    
     
    }

function bajaT(){
    
    $.ajax({
        type: "GET",
            async: true,
            url: "../../Procesar_Cierre_BTBD_Srv",
            dataType: "json",
        
            success: function (response) {
                if (response.status === 0) {
                barra("40");
                estado.textContent= "Incrementar semestre por cierre";
                
                SemestreC();
                
            }else{
                bootBoxAlert('<i class="fa fa-lg fa-check-circle iconoCorrecto"></i> Error', "No se pudo realizar la petición. Consulte al administrador del sistema.");
                $("#pageLoader").css("display", "none");
            }
            },
            error: function (XMLHttpRequest, textStatus, errorThrown) {
                bootBoxAlert('<i class="fa fa-lg fa-times-circle iconoError"></i> Error', "Ocurrió un error al realizar la petición:<br/>" +
                        "Estatus: " + textStatus + "<br/>" +
                        "Error: " + errorThrown + "<br/>" +
                        "XML"+XMLHttpRequest+"<br/>"+
                        "Por favor, notifique a su administrador de este error. <br/>");
                $("#pageLoader").css("display", "none");
            }
     });
}



function SemestreC(){
    $.ajax({
        type: "GET",
            async: true,
            url: "../../Procesar_Cierre_SEM_Srv",
            dataType: "json",
        
            success: function (response) {
                if (response.status === 0) {
                barra("80");
                
                estado.textContent= "Actualizar estatus de no inscrito a baja definitiva"; 
                BajaD();
                
                
            }else{
                bootBoxAlert('<i class="fa fa-lg fa-check-circle iconoCorrecto"></i> Error', "No se pudo realizar la petición. Consulte al administrador del sistema.");
                $("#pageLoader").css("display", "none");
            }
            },
            error: function (XMLHttpRequest, textStatus, errorThrown) {
                bootBoxAlert('<i class="fa fa-lg fa-times-circle iconoError"></i> Error', "Ocurrió un error al realizar la petición:<br/>" +
                        "Estatus: " + textStatus + "<br/>" +
                        "Error: " + errorThrown + "<br/>" +
                        "XML"+XMLHttpRequest+"<br/>"+
                        "Por favor, notifique a su administrador de este error. <br/>");
                $("#pageLoader").css("display", "none");
            }
     });
}

function BajaD(){
     $.ajax({
        type: "GET",
            async: true,
            url: "../../Procesar_Cierre_NIABD_Srv",
            dataType: "json",
        
            success: function (response) {
                if (response.status === 0) {
                barra("60");
               estado.textContent= "Asignar créditos maximos y mínimos"; 
                CreditosM();
                
            }else{
                bootBoxAlert('<i class="fa fa-lg fa-check-circle iconoCorrecto"></i> Error', "No se pudo realizar la petición. Consulte al administrador del sistema.");
                $("#pageLoader").css("display", "none");
            }
            },
            error: function (XMLHttpRequest, textStatus, errorThrown) {
                bootBoxAlert('<i class="fa fa-lg fa-times-circle iconoError"></i> Error', "Ocurrió un error al realizar la petición:<br/>" +
                        "Estatus: " + textStatus + "<br/>" +
                        "Error: " + errorThrown + "<br/>" +
                        "XML"+XMLHttpRequest+"<br/>"+
                        "Por favor, notifique a su administrador de este error. <br/>");
                $("#pageLoader").css("display", "none");
            }
     });
}

function CreditosM(){
     $.ajax({
        type: "GET",
            async: true,
            url: "../../Procesar_Cierre_CRED_Srv",
            dataType: "json",
        
            success: function (response) {
                if (response.status === 0) {
                barra("100");
                estado.textContent= "Terminando proceso"; 
                setTimeout(pageloader,2000);
            }else{
                bootBoxAlert('<i class="fa fa-lg fa-check-circle iconoCorrecto"></i> Error', "No se pudo realizar la petición. Consulte al administrador del sistema.");
                $("#pageLoader").css("display", "none");
            }
            },
            error: function (XMLHttpRequest, textStatus, errorThrown) {
                bootBoxAlert('<i class="fa fa-lg fa-times-circle iconoError"></i> Error', "Ocurrió un error al realizar la petición:<br/>" +
                        "Estatus: " + textStatus + "<br/>" +
                        "Error: " + errorThrown + "<br/>" +
                        "XML"+XMLHttpRequest+"<br/>"+
                        "Por favor, notifique a su administrador de este error. <br/>");
                $("#pageLoader").css("display", "none");
            }
     });
}

function barra(valor){
    const progreso=document.getElementById('progreso');
    const porcentaje=document.getElementById('porcentaje');
    valor = valor + "";
    progreso.style.width = valor+'%';
    porcentaje.textContent= valor+"%";
}

var pageloader = function(){
    bootBoxAlert('<i class="fa fa-lg fa-check-circle iconoCorrecto"></i> Éxito en la operación', "El proceso fué realizado con éxito.");
    $("#pageLoader").css("display", "none");
};

$("#process-egresados").on("click",function(){
    var mensaje = "El siguiente proceso ejecutará el cambio de estatus de egresado sin certificado a egresado con certificado: <br/><br/>";
        mensaje += "¿Desea continuar?";
        bootBoxConfirm('<i class="fa fa-lg fa-info-circle iconoInfo"></i> Confirmación requerida', mensaje, estatusegresado);
    
});

function estatusegresado(){
    $("#pageLoaderc").css("display", "block");
    $.ajax({
        type: "GET",
            async: true,
            url: "../../CambioEstatus_ES_EC_Srv",
            dataType: "json",
        
            success: function (response) {
                if (response.status === 0) {
                bootBoxAlert('<i class="fa fa-lg fa-check-circle iconoCorrecto"></i> Éxito en la operación', "El proceso fué realizado con éxito.");
                $("#pageLoaderc").css("display", "none");
            }else{
                bootBoxAlert('<i class="fa fa-lg fa-check-circle iconoCorrecto"></i> Error', "No se pudo realizar la petición. Consulte al administrador del sistema.");
                $("#pageLoaderc").css("display", "none");
            }
            },
            error: function (XMLHttpRequest, textStatus, errorThrown) {
                bootBoxAlert('<i class="fa fa-lg fa-times-circle iconoError"></i> Error', "Ocurrió un error al realizar la petición:<br/>" +
                        "Estatus: " + textStatus + "<br/>" +
                        "Error: " + errorThrown + "<br/>" +
                        "XML"+XMLHttpRequest+"<br/>"+
                        "Por favor, notifique a su administrador de este error. <br/>");
                $("#pageLoaderc").css("display", "none");
            }
     });
}

$("#process-status").on("click",function(){
    var mensaje = "El siguiente proceso ejecutará el cambio de estatus de inscrito a no inscrito: <br/><br/>";
        mensaje += "¿Desea continuar?";
        bootBoxConfirm('<i class="fa fa-lg fa-info-circle iconoInfo"></i> Confirmación requerida', mensaje, estatusinscrito);
    
});

function estatusinscrito(){
    $("#pageLoaderc").css("display", "block");
    $.ajax({
        type: "GET",
            async: true,
            url: "../../CambioEstat_IN_NI",
            dataType: "json",
        
            success: function (response) {
                if (response.status === 0) {
                bootBoxAlert('<i class="fa fa-lg fa-check-circle iconoCorrecto"></i> Éxito en la operación', "El proceso fué realizado con éxito.");
                $("#pageLoaderc").css("display", "none");
            }else{
                bootBoxAlert('<i class="fa fa-lg fa-check-circle iconoCorrecto"></i> Error', "No se pudo realizar la petición. Consulte al administrador del sistema.");
                $("#pageLoaderc").css("display", "none");
            }
            },
            error: function (XMLHttpRequest, textStatus, errorThrown) {
                bootBoxAlert('<i class="fa fa-lg fa-times-circle iconoError"></i> Error', "Ocurrió un error al realizar la petición:<br/>" +
                        "Estatus: " + textStatus + "<br/>" +
                        "Error: " + errorThrown + "<br/>" +
                        "XML"+XMLHttpRequest+"<br/>"+
                        "Por favor, notifique a su administrador de este error. <br/>");
                $("#pageLoaderc").css("display", "none");
            }
     });
}



$("#process-sincert").on("click",function(){
    var mensaje = "El siguiente proceso ejecutará el cambio de estatus a Egresado sin Certificado: <br/><br/>";
        mensaje += "¿Desea continuar?";
        bootBoxConfirm('<i class="fa fa-lg fa-info-circle iconoInfo"></i> Confirmación requerida', mensaje, estatuSinCertificado);
    
});

function estatuSinCertificado(){
    $("#pageLoaderc").css("display", "block");
    $.ajax({
        type: "GET",
            async: true,
            url: "../../CambioEstat_ES",
            dataType: "json",
        
            success: function (response) {
                if (response.status === 0) {
                bootBoxAlert('<i class="fa fa-lg fa-check-circle iconoCorrecto"></i> Éxito en la operación', "El proceso fué realizado con éxito.");
                $("#pageLoaderc").css("display", "none");
            }else{
                bootBoxAlert('<i class="fa fa-lg fa-check-circle iconoCorrecto"></i> Error', "No se pudo realizar la petición. Consulte al administrador del sistema.");
                $("#pageLoaderc").css("display", "none");
            }
            },
            error: function (XMLHttpRequest, textStatus, errorThrown) {
                bootBoxAlert('<i class="fa fa-lg fa-times-circle iconoError"></i> Error', "Ocurrió un error al realizar la petición:<br/>" +
                        "Estatus: " + textStatus + "<br/>" +
                        "Error: " + errorThrown + "<br/>" +
                        "XML"+XMLHttpRequest+"<br/>"+
                        "Por favor, notifique a su administrador de este error. <br/>");
                $("#pageLoaderc").css("display", "none");
            }
     });
}