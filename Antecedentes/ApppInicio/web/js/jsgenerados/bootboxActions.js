/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

//Función que genera un alert personalizado con bootbox.
//Recibe como parámetros el título y cuerpo del mensaje.

function bootBoxAlert(titulo, mensaje) {
    bootbox.dialog({
        title: titulo,
        message: '<div class="row" style="text-align: justify">' +
                '<div class=col-md-12>' +
                '<h3 class=panel-title>' + mensaje + '</h3>' +
                '</div>' +
                '</div>',
        buttons: {
            main: {
                label: "Aceptar",
                className: "btn-primary"
            }
        }
    });
}

//Función que crea un diálogo de confirmación personalizado en bootbox.
//Sus parámetros son: el título del diálogo, el mensaje de confirmación y una función a ejecutar cuando el usuario oprima Aceptar o Cancelar.
//En su declaración, esta función recibe un booleano: el resultado de Aceptar o Cancelar.
function bootBoxConfirm(titulo, mensaje,callbackFunc,callbeforeFunc) {
    
    bootbox.dialog({
        title: titulo,
        message: '<div class="row" style="text-align: justify">' +
                '<div class=col-md-12>' +
                '<h3 class=panel-title>' + mensaje + '</h3>' +
                '</div>' +
                '</div>',
        onEscape: null,
        buttons: {
            //Botón cancelar del diálogo.
            danger: {
                label: "Cancelar",
                class: "btn-default"
                
            },
            //Botón aceptar del diálogo.
            main: {
                label: "Aceptar",
                className: "btn-primary",
                callback: function () {
                    callbackFunc();
                }

            }          
        }
    });

}


