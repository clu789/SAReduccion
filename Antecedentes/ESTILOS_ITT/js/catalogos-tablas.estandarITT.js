/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

var tables = [];
var spinnerHTML = '<span class="fa fa-spinner fa-spin fa-2x"></span>';
var successHTML = '<span class="fa fa-check fa-2x iconSuccess"></span>';
var errorHTML = '<span class="fa fa-times fa-2x iconError" data-placement="auto"></span>';



function crearTabla(idTabla, agregar, guardar, eliminar, update, rowsNew) {
	var b = false;
	
	var objTable = new Object();
	objTable.idTabla = idTabla;
	objTable.estado = 0;
	objTable.selecting = false;
	objTable.agregar = agregar;
	objTable.guardar = guardar;
	objTable.eliminar = eliminar;
	objTable.update = update;
	// add sorting & searching problem // LALO
	if(rowsNew === undefined){
		rowsNew = 3;
	}
	objTable.rowsNew = rowsNew;
	
	for (i in tables) {
        if (tables[i].idTabla === idTabla) {
			b = true;
			tables[i] = objTable;
			break;
		}
	}
	if(!b){
		tables.push(objTable);
	}
    
}

/* AGREGADA EDITAR*/
function crearTablaEdicion(idTabla, agregar, guardar, eliminar, update, guardarEditar, cancelarEditar, rowsNew) {
	var b = false;
	
	var objTable = new Object();
	objTable.idTabla = idTabla;
	objTable.estado = 0;
	objTable.selecting = false;
	objTable.agregar = agregar;
	objTable.guardar = guardar;
	objTable.eliminar = eliminar;
	objTable.update = update;
	objTable.guardarEditar = guardarEditar;
	objTable.cancelarEditar = cancelarEditar;
	// add sorting & searching problem // LALO
	if(rowsNew === undefined){
		rowsNew = 3;
	}
	objTable.rowsNew = rowsNew;
	
	for (i in tables) {
        if (tables[i].idTabla === idTabla) {
			b = true;
			tables[i] = objTable;
            break;
        }
    }
	if(!b){
		tables.push(objTable);
	}
}

function getTable(idTable) {
    var i = 0;
    var objTable = null;
    for (i in tables) {
        objTable = tables[i];
        if (objTable.idTabla === idTable) {
            break;
        }
    }
    return objTable;
}

function accion(table) {
    switch (table.estado) {
        case 0:
			eliminaNueva(table.idTabla);
            break;
        case 1:
            table.agregar();
            break;
        case 2:
        case 4:
            table.guardar();
            break;
            /* AGREGADA EDITAR*/
        case 5:
            table.update();
            break;
        case 6:
        case 8:
            table.guardarEditar();
    }
}

function agregarLib(idTable) {
    var table = getTable(idTable);
    var estado = 0;
    switch (table.estado) {
        case 0:
            estado = 1;
            break;
        case 1:
        case 3:
            estado = 4;
            break;
    }
    //
    if (estado !== 0) {
		// add sorting & searching problem // LALO
		var tablaDT = $('#' + idTable).DataTable();
		var news = tablaDT.$('tr.newIn').length;
		if(news >= table.rowsNew){
			addNewsInsertDataTable(tablaDT);
		}
		
        table.estado = estado;
        accion(table);
    }
}
/* AGREGADA EDITAR*/
function editarLib(idTable) {
    var table = getTable(idTable);
    var estado = 0;
    switch (table.estado) {
        case 0:
            estado = 5;
            break;
        case 5:
        case 7:
            estado = 8;
            break;
    }
    if (estado !== 0) {
        table.estado = estado;
		if(estado === 5){
			var tablaDT = $('#' + idTable).dataTable();
			tablaDT.$('tr.selected').addClass('editandoLib');
		}
        accion(table);
    }
}

function guardarLib(idTable) {
    var table = getTable(idTable);
    var estado = 0;
    switch (table.estado) {
        case 1:
        case 3:
            estado = 2;
            break;
            /* AGREGADA EDITAR*/
        case 5:
        case 7:
            estado = 6;
    }
    table.estado = estado;
    accion(table);
}

function eliminarLib(idTable) {
    var table = getTable(idTable);
    table.eliminar();
}

function eliminaNueva(idTable) {
    var tablaDT = $('#' + idTable).DataTable();
	var newRow = tablaDT.rows("#newRow").nodes().to$();
	var edita = tablaDT.rows(".editandoLib").nodes().to$();
	if (newRow.length === 1) {
		tablaDT.row("tr#newRow").remove().draw();
	}
	if(edita.length === 1) {
		var table = getTable(idTable);
		table.cancelarEditar();
		edita.find('td.successTd').html("");
		edita.removeClass('editandoLib');
	}
}

function eliminarNuevaLib(idTable) {
    var table = getTable(idTable);
    table.estado = 0;
    accion(table);
}

function selectLib(idTabla, idFila) {
    var tablaDT = $('#' + idTabla).dataTable();
    if (tablaDT.$('tr#' + idFila).hasClass('selected')) {
        tablaDT.$('tr#' + idFila).removeClass('selected');
    } else {
        tablaDT.$('tr.selected').removeClass('selected');
        tablaDT.$('tr#' + idFila).addClass('selected');
    }
}

function mostrarSpinner(idTabla, idFila) {
    $('#' + idTabla + ' tbody tr#' + idFila + ' td.successTd').css("color", "black");
    $('#' + idTabla + ' tbody tr#' + idFila + ' td.successTd').html(spinnerHTML);
}

function mostrarExitoCheck(idTabla, idFila) {
    $('#' + idTabla + ' tbody tr#' + idFila + ' td.successTd').html(successHTML);
    var $td = $('#' + idTabla + ' tbody tr#' + idFila + ' td.successTd');
    setTimeout(function () {
        $td.fadeOut("slow", function () {
            $td.html('');
            $td.show();
        });
    }, 1500);
    $('#' + idTabla + ' tbody tr#' + idFila + ' td.successTd').removeAttr("data-original-title");
    $('#' + idTabla + ' tbody tr#' + idFila + ' td.successTd').removeAttr("title");
}

function limpiarSuccessTd(idTabla){
	var tablaDT = $('#' + idTabla).DataTable();
	tablaDT.$('td.successTd').each(function (index) {
		$(this).html('');
	});
}

function mostrarErrorCheck(idTabla, idFila, mensaje) {
    $('#' + idTabla + ' tbody tr#' + idFila + ' td.successTd').html(errorHTML);

    $('#' + idTabla + ' tbody tr#' + idFila + ' td span.iconError').prop("data-message-hover", mensaje);
    $('#' + idTabla + ' tbody tr#' + idFila + ' td span.iconError').hover(
            function () {
                var mensaje1 = $('#' + idTabla + ' tbody tr#' + idFila + ' td span.iconError').prop("data-message-hover");
                $('#messageLibTables').html("<b>Error:</b><br>" + mensaje1);
                $('#messageLibTables').show();
            }, function () {
				$('#messageLibTables').hide();
			}
	);
}

function colocarHoverInfoLib(idIcon, message) {
    $('span#' + idIcon).hover(
            function () {
                $('#messageLibInfo').html("<b>Información</b><br>" + message);
                $('#messageLibInfo').show();
            }, function () {
        $('#messageLibInfo').hide();
    });
}
function colocarHoverErrorLib(idIcon, message) {
    $('span#' + idIcon).hover(
            function () {
                $('#messageLibTables').html("<b>Información</b><br>" + message);
                $('#messageLibTables').show();
            }, function () {
        $('#messageLibTables').hide();
    });
}

function ocultarSpinner(idTabla, idFila) {
    $('#' + idTabla + ' tbody tr#' + idFila + ' td.successTd').html('');
}

function mostrarExito(idTabla, idFila) {
    var table = getTable(idTabla);
    var estado = table.estado;
	var tablaDT = $('#' + idTabla).dataTable();
    switch (table.estado) {
        case 2:
            estado = 0;
            break;
        case 4:
            estado = 1;
            break;

            /* AGREGADA EDITAR*/
        case 6:
			tablaDT.$('tr.editandoLib').removeClass('editandoLib');
            estado = 0;
            break;
        case 8:
            tablaDT.$('tr.editandoLib').removeClass('editandoLib');
            estado = 5;
            break;

    }
	tablaDT.$('tr#'+idFila).addClass('newIn');
    table.estado = estado;
	limpiarSuccessTd(idTabla);
    mostrarExitoCheck(idTabla, idFila);
    accion(table);
}
/* Se agrego el parametro para el idFila ya que es necesario saber el id de la fila.*/
function mostrarError(idTabla, idFila, mensaje) { // idFile = "" para agregar
    var table = getTable(idTabla);
    var estado = table.estado;
    if (idFila === "") {
        idFila = "newRow";
    }
    switch (table.estado) {
        case 2:
        case 4:
            estado = 3;
            break;
            /* AGREGADA EDITAR*/
        case 6:
        case 8:
            estado = 7;
            break;
    }
    table.estado = estado;
	limpiarSuccessTd(idTabla);
    mostrarErrorCheck(idTabla, idFila, mensaje);
    mensajeError(mensaje);
}
function mensajeError(mensaje) {
    bootbox.dialog({
        title: '<i class="fa fa-times-circle fa-lg iconoError"></i>  <b>Aviso</b>',
        message: mensaje,
        buttons: {
            success: {
                label: "OK",
                className: "btn-primary"
            }
        }
    });
}

function ocultarIconoSorting() {
    $("th.successTd").removeClass("sorting");
}

function addNewsDataTable(table){
	table.$('tr.newIn').find('td').each(function (index) {
		var cell = table.cell(this);
		cell.data($(this).html());
	});
	table.$('tr.newIn').removeClass('newIn');
}

function addNewsInsertDataTable(table){
	table.$('tr.newIn').first().find('td').each(function (index) {
		var cell = table.cell(this);
		cell.data($(this).html());
	});
	table.$('tr.newIn').first().removeClass('newIn');
}

function addClickSortNew(table){
	var filaSeleccionada = table.$('tr.newIn').length;
	if(filaSeleccionada > 0){
		table.on('click', 'th', function() {
			addNewsDataTable(table);
		});
	}
}

function filtradoColumna(table, nombre_tabla) {
    var i = 0;
    var j = 0;
    table.columns().every(function () {
        var that = this;
        $('#' + nombre_tabla + '-colSearch' + (++i)).each(function () {
            $(this).removeAttr("disabled");
        });
        $('#' + nombre_tabla + '-colSearch' + (++j)).on('keyup change', function () {
			addNewsDataTable(table);
            that
                    .search(this.value)
                    .draw();
        });
    });
	addClickSortNew(table);
}

function RemoverFiltrado(table, nombre_tabla) {
    var i = 0;
    var j = 0;
	var objTable = getTable(nombre_tabla);
    table.columns().every(function () {
		if(objTable.estado === 0){
			this.search("").draw();
		}
        $('#' + nombre_tabla + '-colSearch' + (++i)).each(function () {
            $(this).val("");
            $(this).prop("disabled", true);
        });
        $('#' + nombre_tabla + '-colSearch' + (++j)).off('keyup change');
    });
}