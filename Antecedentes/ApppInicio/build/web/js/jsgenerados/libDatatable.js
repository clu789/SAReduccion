/* 
 * Licencia AC
 * Copyright 2017 Ángel Careaga <ing.angelcareaga@gmail.com>.
 *
 * Document   : libDatatable.js
 * Created on : 16/01/2017, 03:57:01 PM
 * Description: Crea tipos de datatables para distintos usos, bisqueda, selección, modal.
 *
 */



/****************************** Función que crea DataTables ***********************************/
function creaDatatable(id) {
    $('a[data-toggle="tab"]').on('shown.bs.tab', function (e) {
        $.fn.dataTable.tables({visible: true, api: true}).columns.adjust();
    });
// Aplicando filtro
    $('#' + id + ' thead input').on('keyup change', function () {
        table
                .column($(this).parent().index() + ':visible')
                .search(this.value)
                .draw();
    });
    var table = $('#' + id).DataTable({
        select: false,
        fixedColumns: true,
        ordering: true,
        searching: true,
        "destroy": true,
        dom: 'rtlp',
        "fixedColumns": true,
        "language":
                {
                    "lengthMenu": "Mostrar _MENU_ registros por página.",
                    "zeroRecords": "Lo sentimos. No se encontraron registros.",
                    "info": "Mostrando página _PAGE_ de _PAGES_",
                    "infoEmpty": "Aún no hay registros.",
                    "infoFiltered": "(filtrados de un total de _MAX_ registros)",
                    "search": "Búsqueda",
                    "LoadingRecords": "Cargando ...",
                    "Processing": "Procesando...",
                    "SearchPlaceholder": "Comience a teclear...",
                    "paginate": {
                        "previous": "Anterior",
                        "next": "Siguiente",
                    }
                },
        "aLengthMenu": [[10, 15, 20, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100, -1], [10, 15, 20, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100, "All"]]
    });
    $(window).resize(function () {
        table.columns.adjust();
    });
    function stopPropagation(evt) {
        if (evt.stopPropagation !== undefined) {
            evt.stopPropagation();
        } else {
            evt.cancelBubble = true;
        }
    }
}

/************** Función que crea DataTables con espacio para activo y spinner ******************/

function creaDatatableActivoSpinner(id) {

    var nColumnas = $("#"+id+" tr:last td").length;
    var activo = nColumnas -2;
    var spinner = nColumnas -1;

    $('a[data-toggle="tab"]').on('shown.bs.tab', function (e) {
        $.fn.dataTable.tables({visible: true, api: true}).columns.adjust();
    });
// Aplicando filtro
    $('#' + id + ' thead input').on('keyup change', function () {
        table
                .column($(this).parent().index() + ':visible')
                .search(this.value)
                .draw();
    });
    
    var table = $('#' + id).DataTable({
        select: false,
        fixedColumns: true,
        ordering: true,
        searching: true,
        "destroy": true,
        dom: 'rtlp',
        "fixedColumns": true,
        "language":
                {
                    "lengthMenu": "Mostrar _MENU_ registros por página.",
                    "zeroRecords": "Lo sentimos. No se encontraron registros.",
                    "info": "Mostrando página _PAGE_ de _PAGES_",
                    "infoEmpty": "Aún no hay registros.",
                    "infoFiltered": "(filtrados de un total de _MAX_ registros)",
                    "search": "Búsqueda",
                    "LoadingRecords": "Cargando ...",
                    "Processing": "Procesando...",
                    "SearchPlaceholder": "Comience a teclear...",
                    "paginate": {
                        "previous": "Anterior",
                        "next": "Siguiente",
                    }
                },
        "aLengthMenu": [[10, 15, 20, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100, -1], [10, 15, 20, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100, "All"]],
        "columnDefs": [{
            "orderable": false,
            "targets": [activo, spinner]
        }]
    });
    $(window).resize(function () {
        table.columns.adjust();
    });
    function stopPropagation(evt) {
        if (evt.stopPropagation !== undefined) {
            evt.stopPropagation();
        } else {
            evt.cancelBubble = true;
        }
    }
}

/**************************** Termina **************************************************************/


/************** Función que crea DataTables con espacio para spinner ******************/

function creaDatatableSpinner(id) {

    var nColumnas = $("#"+id+" tr:last td").length;

    var spinner = nColumnas -1;

    $('a[data-toggle="tab"]').on('shown.bs.tab', function (e) {
        $.fn.dataTable.tables({visible: true, api: true}).columns.adjust();
    });
// Aplicando filtro
    $('#' + id + ' thead input').on('keyup change', function () {
        table
                .column($(this).parent().index() + ':visible')
                .search(this.value)
                .draw();
    });
    var table = $('#' + id).DataTable({
        select: false,
        fixedColumns: true,
        ordering: true,
        searching: true,
        "destroy": true,
        dom: 'rtlp',
        "fixedColumns": true,
        "language":
                {
                    "lengthMenu": "Mostrar _MENU_ registros por página.",
                    "zeroRecords": "Lo sentimos. No se encontraron registros.",
                    "info": "Mostrando página _PAGE_ de _PAGES_",
                    "infoEmpty": "Aún no hay registros.",
                    "infoFiltered": "(filtrados de un total de _MAX_ registros)",
                    "search": "Búsqueda",
                    "LoadingRecords": "Cargando ...",
                    "Processing": "Procesando...",
                    "SearchPlaceholder": "Comience a teclear...",
                    "paginate": {
                        "previous": "Anterior",
                        "next": "Siguiente",
                    }
                },
        "aLengthMenu": [[10, 15, 20, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100, -1], [10, 15, 20, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100, "All"]],
        "columnDefs": [{
            "orderable": false,
            "targets": [spinner]
        }],
    });
    $(window).resize(function () {
        table.columns.adjust();
    });
    function stopPropagation(evt) {
        if (evt.stopPropagation !== undefined) {
            evt.stopPropagation();
        } else {
            evt.cancelBubble = true;
        }
    }
}

/**************************** Termina **************************************************************/


/****************************** Función que crea DataTables con Scroll ***********************************/
function creaDatatableScroll(id) {
    $('a[data-toggle="tab"]').on('shown.bs.tab', function (e) {
        $.fn.dataTable.tables({visible: true, api: true}).columns.adjust();
    });
// Aplicando filtro
    $('#' + id + ' thead input').on('keyup change', function () {
        table
                .column($(this).parent().index() + ':visible')
                .search(this.value)
                .draw();
    });
    var table = $('#' + id).DataTable({
        select: false,
        info: false,
        fixedColumns: true,
        paging: false,
        bFilter: false,
        ordering: true,
        searching: true,
        dom: 'rtlp',
        sScrollY: "200px",
        scrollCollapse: true,
        "info": false,
        "destroy": true,
        "fixedColumns": true,
        "language":
                {
                    "lengthMenu": "Mostrar _MENU_ registros por página.",
                    "zeroRecords": "Lo sentimos. No se encontraron registros.",
                    "info": "Mostrando página _PAGE_ de _PAGES_",
                    "infoEmpty": "Aún no hay registros.",
                    "infoFiltered": "(filtrados de un total de _MAX_ registros)",
                    "search": "Búsqueda",
                    "LoadingRecords": "Cargando ...",
                    "Processing": "Procesando...",
                    "SearchPlaceholder": "Comience a teclear...",
                    "paginate": {
                        "previous": "Anterior",
                        "next": "Siguiente",
                    }
                }
    }).columns.adjust();

    $(window).resize(function () {
        table.columns.adjust();
    });

    function stopPropagation(evt) {
        if (evt.stopPropagation !== undefined) {
            evt.stopPropagation();
        } else {
            evt.cancelBubble = true;
        }
    }
}


/****************************** Función que crea DataTables con Scroll ***********************************/
function creaDatatableScrollActivoSpinner(id) {

     var nColumnas = $("#"+id+" tr:last td").length;

    var activo = nColumnas -2;
    var spinner = nColumnas -1;

    $('a[data-toggle="tab"]').on('shown.bs.tab', function (e) {
        $.fn.dataTable.tables({visible: true, api: true}).columns.adjust();
    });
// Aplicando filtro
    $('#' + id + ' thead input').on('keyup change', function () {
        table
                .column($(this).parent().index() + ':visible')
                .search(this.value)
                .draw();
    });
    var table = $('#' + id).DataTable({
        select: false,
        info: false,
        fixedColumns: true,
        paging: false,
        bFilter: false,
        ordering: true,
        searching: true,
        dom: 'rtlp',
        sScrollY: "200px",
        scrollCollapse: true,
        "info": false,
        "destroy": true,
        "fixedColumns": true,
        "language":
                {
                    "lengthMenu": "Mostrar _MENU_ registros por página.",
                    "zeroRecords": "Lo sentimos. No se encontraron registros.",
                    "info": "Mostrando página _PAGE_ de _PAGES_",
                    "infoEmpty": "Aún no hay registros.",
                    "infoFiltered": "(filtrados de un total de _MAX_ registros)",
                    "search": "Búsqueda",
                    "LoadingRecords": "Cargando ...",
                    "Processing": "Procesando...",
                    "SearchPlaceholder": "Comience a teclear...",
                    "paginate": {
                        "previous": "Anterior",
                        "next": "Siguiente",
                    }
                },
        "columnDefs": [{
            "orderable": false,
            "targets": [activo, spinner]
        }]
    }).columns.adjust();

    $(window).resize(function () {
        table.columns.adjust();
    });
    function stopPropagation(evt) {
        if (evt.stopPropagation !== undefined) {
            evt.stopPropagation();
        } else {
            evt.cancelBubble = true;
        }
    }
}

/****************************** Función que crea DataTables con Scroll ***********************************/
function creaDatatableScrollSpinner(id) {

     var nColumnas = $("#"+id+" tr:last td").length;

    var spinner = nColumnas -1;

    $('a[data-toggle="tab"]').on('shown.bs.tab', function (e) {
        $.fn.dataTable.tables({visible: true, api: true}).columns.adjust();
    });
// Aplicando filtro
    $('#' + id + ' thead input').on('keyup change', function () {
        table
                .column($(this).parent().index() + ':visible')
                .search(this.value)
                .draw();
    });
    var table = $('#' + id).DataTable({
        select: false,
        info: false,
        fixedColumns: true,
        paging: false,
        bFilter: false,
        ordering: true,
        searching: true,
        sScrollY: "400px",
        scrollCollapse: true,
        "destroy": true,
        dom: 'Bfrtip',
        "language":
                {
                    "lengthMenu": "Mostrar _MENU_ registros por página.",
                    "zeroRecords": "No se encontraron coincidencias",
                    "info": "Mostrando página _PAGE_ de _PAGES_",
                    "infoEmpty": "Aún no hay registros.",
                    "infoFiltered": "(filtrados de un total de _MAX_ registros)",
                    "search": "Buscar : ",
                    "LoadingRecords": "Cargando ...",
                    "Processing": "Procesando...",
                    "SearchPlaceholder": "Comience a teclear...",
                    "paginate": {
                        "previous": "Anterior",
                        "next": "Siguiente",
                    }
                },
        "responsive" : {
          details : {
              display:
              $.fn.dataTable.Responsive.display.childRowInmediate,
              type : ''
          }  
        },
        "columnDefs": [{
            "orderable": false,
            "targets": [spinner]
        }]
    }).columns.adjust();

    $(window).resize(function () {
        table.columns.adjust();
    });
    function stopPropagation(evt) {
        if (evt.stopPropagation !== undefined) {
            evt.stopPropagation();
        } else {
            evt.cancelBubble = true;
        }
    }
}
/****************************** Función que crea DataTables para Modal ***********************************/
function creaDatatableModal(id) {
    $('a[data-toggle="tab"]').on('shown.bs.tab', function (e) {
        $.fn.dataTable.tables({visible: true, api: true}).columns.adjust();
    });
// Aplicando filtro
    $('#' + id + ' thead input').on('keyup change', function () {
        table
                .column($(this).parent().index() + ':visible')
                .search(this.value)
                .draw();
    });
    var table = $('#' + id).DataTable({
        "iDisplayLength": 5,
        select: false,
        fixedColumns: true,
        ordering: true,
        searching: true,
        "destroy": true,
        dom: 'rtp',
        "fixedColumns": true,
        "language":
                {
                    "lengthMenu": "Mostrar _MENU_ registros por página.",
                    "zeroRecords": "Lo sentimos. No se encontraron registros.",
                    "info": "Mostrando página _PAGE_ de _PAGES_",
                    "infoEmpty": "Aún no hay registros.",
                    "infoFiltered": "(filtrados de un total de _MAX_ registros)",
                    "search": "Búsqueda",
                    "LoadingRecords": "Cargando ...",
                    "Processing": "Procesando...",
                    "SearchPlaceholder": "Comience a teclear...",
                    "paginate": {
                        "previous": "Anterior",
                        "next": "Siguiente",
                    }
                }
    });
    $(window).resize(function () {
        table.columns.adjust();
    });
    function stopPropagation(evt) {
        if (evt.stopPropagation !== undefined) {
            evt.stopPropagation();
        } else {
            evt.cancelBubble = true;
        }
    }
}



/*********************************************/


/****************************** Función que crea DataTables para Modal ***********************************/
function creaDatatableModalActivoSpinner(id) {

    var nColumnas = $("#"+id+" tr:last td").length;

    var activo = nColumnas -2;
    var spinner = nColumnas -1;


    $('a[data-toggle="tab"]').on('shown.bs.tab', function (e) {
        $.fn.dataTable.tables({visible: true, api: true}).columns.adjust();
    });
// Aplicando filtro
    $('#' + id + ' thead input').on('keyup change', function () {
        table
                .column($(this).parent().index() + ':visible')
                .search(this.value)
                .draw();
    });
    var table = $('#' + id).DataTable({
        "iDisplayLength": 5,
        select: false,
        fixedColumns: true,
        ordering: true,
        searching: true,
        "destroy": true,
        dom: 'rtp',
        "fixedColumns": true,
        "language":
                {
                    "lengthMenu": "Mostrar _MENU_ registros por página.",
                    "zeroRecords": "Lo sentimos. No se encontraron registros.",
                    "info": "Mostrando página _PAGE_ de _PAGES_",
                    "infoEmpty": "Aún no hay registros.",
                    "infoFiltered": "(filtrados de un total de _MAX_ registros)",
                    "search": "Búsqueda",
                    "LoadingRecords": "Cargando ...",
                    "Processing": "Procesando...",
                    "SearchPlaceholder": "Comience a teclear...",
                    "paginate": {
                        "previous": "Anterior",
                        "next": "Siguiente",
                    }
                },
        "columnDefs": [{
            "orderable": false,
            "targets": [activo, spinner]
        }]
    });
    $(window).resize(function () {
        table.columns.adjust();
    });
    function stopPropagation(evt) {
        if (evt.stopPropagation !== undefined) {
            evt.stopPropagation();
        } else {
            evt.cancelBubble = true;
        }
    }
}

/****************************************/


/****************************** Función que crea DataTables para Modal ***********************************/
function creaDatatableModalSpinner(id) {

    var nColumnas = $("#"+id+" tr:last td").length;
    var spinner = nColumnas -1;

    $('a[data-toggle="tab"]').on('shown.bs.tab', function (e) {
        $.fn.dataTable.tables({visible: true, api: true}).columns.adjust();
    });
// Aplicando filtro
    $('#' + id + ' thead input').on('keyup change', function () {
        table
                .column($(this).parent().index() + ':visible')
                .search(this.value)
                .draw();
    });
    var table = $('#' + id).DataTable({
        "iDisplayLength": 5,
        select: false,
        fixedColumns: true,
        ordering: true,
        searching: true,
        "destroy": true,
        dom: 'rtp',
        "fixedColumns": true,
        "language":
                {
                    "lengthMenu": "Mostrar _MENU_ registros por página.",
                    "zeroRecords": "Lo sentimos. No se encontraron registros.",
                    "info": "Mostrando página _PAGE_ de _PAGES_",
                    "infoEmpty": "Aún no hay registros.",
                    "infoFiltered": "(filtrados de un total de _MAX_ registros)",
                    "search": "Búsqueda",
                    "LoadingRecords": "Cargando ...",
                    "Processing": "Procesando...",
                    "SearchPlaceholder": "Comience a teclear...",
                    "paginate": {
                        "previous": "Anterior",
                        "next": "Siguiente",
                    }
                },
        "columnDefs": [{
            "orderable": false,
            "targets": [spinner]
        }]
    });
    $(window).resize(function () {
        table.columns.adjust();
    });
    function stopPropagation(evt) {
        if (evt.stopPropagation !== undefined) {
            evt.stopPropagation();
        } else {
            evt.cancelBubble = true;
        }
    }
}

/************************** Termina creación DataTables **************************************/