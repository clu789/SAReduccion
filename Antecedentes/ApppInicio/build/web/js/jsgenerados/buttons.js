
$( document ).ready(function() {
    toggleAlumnosSpinner(false);
    $("#pageLoaderc").css("display", "none");
    
});

$('.jp-date').datepicker({

    format: "dd/mm/yyyy",
    language: "es",
    daysOfWeekHighlighted: "0,6",
    calendarWeeks: true

});

function toggleAlumnosSpinner(show) {
    if (show) {
        
        $("#alumnos-container").hide();
        $("#spinner-container").show();
    } else {
        
        $("#alumnos-container").show();
        $("#spinner-container").hide();
    }
}



$("#process-cierre").on("click", function () {
    var tdFecha = document.getElementById("tdFecha").value;
    if (tdFecha.length === 0) {
        bootBoxAlert('<i class="fa fa-lg fa-times-circle iconoError"></i> Error', "Debe seleccionar una fecha");
    } else {
    
        var mensaje = "La información solicitada se mostrará con la fecha: <br/><br/>";
        mensaje += tdFecha + "<br/><br/>";
        mensaje += "¿Desea continuar?";
        bootBoxConfirm('<i class="fa fa-lg fa-info-circle iconoInfo"></i> Confirmación requerida', mensaje, verificarcierre);
    }
});


function verificarcierre() {
    toggleAlumnosSpinner(true);
    var fechaC = $("#tdFecha").val();

    $.ajax({
        async: true,
        data: {fechaC: fechaC},
        dataType: "json",
        type: "POST",
        url: "../../app/modulo/Verificar_Logs_Cierre_Srv",
        success: function (response) {
            
            if (response.status === 0) {
               console.log(response.status);
               
                logsTable(response.responseObject);
             
            } else {
               // prepareTableLogsError();
                bootBoxAlert('<i class="fa fa-lg fa-times-circle iconoError"></i> Error', response.mensaje);
                toggleAlumnosSpinner(false);
            }
            toggleAlumnosSpinner(false);
        },
        error: function (XMLHttpRequest, textStatus, errorThrown) {
            bootBoxAlert('<i class="fa fa-lg fa-times-circle iconoError"></i> Error', "Ocurrió un error al realizar la petición:<br/>" +
                    "Estatus: " + textStatus + "<br/>" +
                    "Error: " + errorThrown + "<br/>" +
                    "Por favor, notifique a su administrador de este error. <br/>");
            toggleAlumnosSpinner(false);

        }
    });
   
}


function logsTable(responseObject) {
    $("#tabla-logs > tbody").empty();
    
    var tamGrupos = responseObject.length;
    
    if (tamGrupos !== 0) {
        $('#tabla-logs').DataTable().destroy();
        $("#tabla-logs > tbody").empty();
        for (i = 0; i < tamGrupos; i++) { //cuenta la cantidad de registros
            var nuevafila = "<tr>" +
                    "<td>" + responseObject[i].Fecha + "</td>" +
                    "<td>" + responseObject[i].Estatus + "</td>" +
                    "<td>" + responseObject[i].Periodo + "</td>" +
                    "<td>" + responseObject[i].Anio + "</td>" +
                    "<td>" + responseObject[i].Usuario + "</td>" +
                    "</tr>";
            $("#tabla-logs").append(nuevafila);
            
           
        }
        
    } else {
        $('#tabla-logs').DataTable().destroy();
        $("#tabla-logs > tbody").empty();
        var nuevafila ="<tr>"+
                "<td>" + "No existen datos" + "</td>"+
                "<td>" + "No existen datos" + "</td>"+
                "<td>" + "No existen datos" + "</td>"+
                "<td>" + "No existen datos" + "</td>"+
                "<td>" + "No existen datos" + "</td>"+
                "</tr>";
        //var nuevafila = "<tr class=\"noAdeudo\"><td colspan=\"7\">" + " No existen datos para mostrar" + "</td></tr>";
        $("#tabla-logs").append(nuevafila);
        
    }
    
   var table = $("#tabla-logs").DataTable(TABLE_LOG_CONFIG);
}

$("#btnExcel").on("click", function () {
        $("#formatInp").val("XLS");
        var fechaC = $("#tdFecha").val();
        $("#reportForm").submit();
    });



var TABLE_LOG_CONFIG = {
    "language": {
        "paginate": {
            "next": "Siguiente",
            "previous": "Anterior"
        },
        "search": "Buscar",
        "zeroRecords": "No hay coincidencias",
        "lengthMenu": "Mostrar _MENU_ resultados"
    },
    "responsive": {
        "details": {
            "display": $.fn.dataTable.Responsive.display.childRowImmediate,
            "type": ''
        }
    },
    "order": [[0, "asc"]],
    "columnDefs": [
        {"orderable": false, "targets": [4]}
    ],
    "info": false,
    "dom": "rtlp"
};

$("#btnDeleteLogs").on("click", function () {
    
   
        var mensaje = "El siguiente procedimiento eliminará la información generada en el proceso de cierre <br/><br/>";
        mensaje += "¿Desea continuar?";
        bootBoxConfirm('<i class="fa fa-lg fa-info-circle iconoInfo"></i> Confirmación requerida', mensaje, truncarlogs);
    });
    
    function truncarlogs() {
    $("#pageLoaderc").css("display", "block");
    
    $.ajax({
        async: true,
        dataType: "json",
        type: "GET",
        url: "../../TruncCierreLogs_Srv",
        success: function (response) {
            
            if (response.status === 0) {
               
              bootBoxAlert('<i class="fa fa-lg fa-check-circle iconoCorrecto"></i> Éxito en la operación', "El proceso se realizó con exito"); 
              $("#pageLoaderc").css("display", "none");
              verificarcierre();
            } else {
               bootBoxAlert('<i class="fa fa-lg fa-times-circle iconoError"></i> Error', response.mensaje);
               $("#pageLoaderc").css("display", "none");
            }
            $("#pageLoaderc").css("display", "none");
        },
        error: function (XMLHttpRequest, textStatus, errorThrown) {
            bootBoxAlert('<i class="fa fa-lg fa-times-circle iconoError"></i> Error', "Ocurrió un error al realizar la petición:<br/>" +
                    "Estatus: " + textStatus + "<br/>" +
                    "Error: " + errorThrown + "<br/>" +
                    "Por favor, notifique a su administrador de este error. <br/>");
            $("#pageLoaderc").css("display", "none");

        }
    });
   
}