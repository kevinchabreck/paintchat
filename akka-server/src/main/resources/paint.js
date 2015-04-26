/**************************************************************************
* Element initialization functions
**************************************************************************/

function createCanvas(parent, width, height){
    var canvas = {};
    canvas.node = document.createElement('canvas');
    canvas.node.id = 'canvas';
    canvas.context = canvas.node.getContext('2d');
    canvas.node.width = width;
    canvas.node.height = height;
    canvas.isDrawing = false;
    canvas.isErasing = false;
    canvas.isFocused = false;
    $(parent).append(canvas.node);
    return canvas;
}

function createPalette(){
    return $("#colorPalette").spectrum({
        showPaletteOnly: true,
        togglePaletteOnly: true,
        togglePaletteMoreText: 'more',
        togglePaletteLessText: 'less',
        clickoutFiresChange: true,
        preferredFormat: "hex",
        color: 'black',
        palette: [
            ["#000","#444","#666","#999","#ccc","#eee","#f3f3f3","#fff"],
            ["#f00","#f90","#ff0","#0f0","#0ff","#00f","#90f","#f0f"],
            ["#f4cccc","#fce5cd","#fff2cc","#d9ead3","#d0e0e3","#cfe2f3","#d9d2e9","#ead1dc"],
            ["#ea9999","#f9cb9c","#ffe599","#b6d7a8","#a2c4c9","#9fc5e8","#b4a7d6","#d5a6bd"],
            ["#e06666","#f6b26b","#ffd966","#93c47d","#76a5af","#6fa8dc","#8e7cc3","#c27ba0"],
            ["#c00","#e69138","#f1c232","#6aa84f","#45818e","#3d85c6","#674ea7","#a64d79"],
            ["#900","#b45f06","#bf9000","#38761d","#134f5c","#0b5394","#351c75","#741b47"],
            ["#600","#783f04","#7f6000","#274e13","#0c343d","#073763","#20124d","#4c1130"]
        ]
    });
}

// function createInputBox(parent){
//     var textBox = {};
//     textBox.node = document.createElement('textarea');
//     textBox.node.rows = 5;
//     textBox.node.cols = 32;
//     textBox.node.maxlength = 100;
//     textBox.node.placeholder = 'say something!';
//     wrap = "hard";
//     textBox.node.id = 'inputBox';
//     parent.appendChild(textBox.node);
//     return textBox;
// }

function init(container, width, height) {

    /**************************************************************************
    * 'global' variables and element initialization
    **************************************************************************/

    var canvas = createCanvas(container, width, height);
    var ctx = canvas.context;
    var rect = canvas.node.getBoundingClientRect();
    // var resetButton = createResetButton(document.getElementById('buttonSpace'));
    // var inputBox = createInputBox(document.getElementById('textEntrySpace'));
    // createSlider();
    createPalette();
    var username = null;
    var oldX = null;
    var oldY = null;
    var fillColor = 'black';
    // var linewidth = $('#sizeSlider').val();
    var linewidth = 6;

    /**************************************************************************
    * Canvas manipulation
    **************************************************************************/

    ctx.draw = function(x1, y1, x2, y2, width, color) {
        if((x1 == x2)&&(y1 == y2)){
            this.fillStyle = color;
            this.beginPath();
            this.arc(x1, y1, width/2, 0, 2*Math.PI, false);
            this.fill();
            this.closePath();
        } else {
            this.lineJoin = 'round';
            this.strokeStyle = color;
            this.lineWidth = width;
            this.beginPath();
            this.moveTo(x1, y1);
            this.lineTo(x2, y2);
            this.closePath()
            this.stroke();
        }
    };

    ctx.clear = function() {
        ctx.fillStyle = "#eee";
        ctx.fillRect(0, 0, width, height);
    };

    /**************************************************************************
    * Canvas, chat box, and reset button event handlers
    **************************************************************************/

    function draw(e) {
        if(canvas.isDrawing && canvas.isFocused){
            e.preventDefault();
            var color = fillColor;
            var size = linewidth;
            if(canvas.isErasing){
                color = "#eee";
                size = 20;
            }
            if(typeof e.originalEvent.touches != 'undefined'){
                e = e.originalEvent.touches[0];
            }
            var newX = e.pageX - rect.left;
            var newY = e.pageY - rect.top;
            ws.send('PAINT:'+oldX+' '+oldY+' '+newX+' '+newY+' '+size+' '+color);
        }
    }

    function move(e) {
        // e.preventDefault();
        if(typeof e.originalEvent.touches != 'undefined'){
            e = e.originalEvent.touches[0];
        }
        oldX = e.pageX - rect.left;
        oldY = e.pageY - rect.top;
        console.log("move(): X: "+oldX+" Y: "+oldY);
        console.log("e.pageX: "+e.pageX+" e.pageY: "+e.pageY);
        console.log("rect.left: "+rect.left+" rect.top: "+rect.top);
    }

    function start(e) {
        e.preventDefault();
        if(typeof e.originalEvent.touches != 'undefined'){
            e = e.originalEvent.touches[0];
        }
        oldX = e.pageX - rect.left;
        oldY = e.pageY - rect.top;
        // $("#toolSpace").hide();
        canvas.isDrawing = true;
    }

    function stop(e) {
        // $("#toolSpace").show();
        canvas.isDrawing = false;
    }

    function focus(e) {
        canvas.isFocused = true;
    }

    function unfocus(e) {
        if(canvas.isDrawing){
            draw(e);
        }
        canvas.isFocused = false;
    }

    function sendReset(e) {
        if ((e.type == "click")||((e.type == "keypress") && (e.which == 114))){
            ws.send('RESET:');
        }
    }

    function changeColor(e, color) {
        canvas.isErasing = false;
        fillColor = color;
        $("#colorPalette").css("color", color);
        $("#colorPalette").spectrum("hide");
    }

    function incSize(e){
        if (linewidth < 40){
            linewidth+=4;
            // $("#colorPalette").css('font-size',"+=1");
            // $("#colorPalette").animate({fontSize:"+=1"});
        }
    }

    function decSize(e){
        if (linewidth > 4){
            linewidth-=4;
            // $("#colorPalette").css('font-size',"-=1");
            // $("#colorPalette").animate({fontSize:"-=1"});
        }
    }

    function startErase(e){
        canvas.isErasing = true;
    }

    $('#canvas').on('mousemove touchmove', draw);
    $('#canvas').on('mousedown touchstart', start);
    $('#canvas').on('mousedown touchstart', draw);
    $('#canvas').on('mouseup touchend', stop);
    $('#canvas').on('mouseover mousein touchstart', focus);
    $('#canvas').on('mouseout touchend touchleave', unfocus);
    $('#canvas').on('tap', focus);
    $('#canvas').on('tap', start);
    $('#canvas').on('tap', draw);
    $('#canvas').on('tap', stop);
    $('#canvas').on('tap', unfocus);

    $('#sizePlus').on('click tap', incSize);
    $('#sizeMinus').on('click tap', decSize);
    $('#selectEraser').on('click tap', startErase);
    $('#resetCanvas').on('click tap', sendReset);

    $(document).on('mousemove touchmove', move);
    // $(document).on('mousedown touchstart', start);
    $(document).on('mousedown', start);
    $(document).on('mouseup touchend', stop);
    $(document).on('keypress', sendReset);

    $("#colorPalette").on('change.spectrum', changeColor);

    // resetButton.node.onclick = function(e) {
    //     ws.send('RESET:');
    //     $(resetButton.node).blur();
    // };

    // inputBox.node.onclick = function(e){
    //     inputBox.node.placeholder = '';
    // };

    // inputBox.node.onkeypress = function(e) {
    //     if (e.keyCode == 13){
    //         sendText();
    //     }
    // };

    /**************************************************************************
    * WebSocket event handlers
    **************************************************************************/

    // functions for handling different headers
    // possible message headers:
    // PAINT, CHAT, INFO, RESET, ACCEPTED, DENIED, PAINTBUFFER, USERS

    function paint(e) {
        var params = e.data.split(':');
        var arr = params[1].split(' ');
        params = arr.splice(0,5);
        params.push(arr.join(' '));
        ctx.draw(params[0], params[1], params[2], params[3], params[4], params[5]);
    }

    function chat(e) {
        // var msg = e.data.replace('CHAT:','');
        // msg = '<b>' + msg.replace(':','</b>:');
        // var messageSpace = document.getElementById("messagesSpace");
        // messageSpace.innerHTML += msg + '</br></br>';
        // messageSpace.scrollTop = messageSpace.scrollHeight;
        // messageSpace.focus();
    }

    function info(e) {
        var info = e.data.replace('INFO:','');
        alertify.log("user "+info);
    }

    function reset(e) {
        var username = e.data.replace('RESET:','');
        alertify.log("user "+username+" has reset the drawing board!");
        ctx.clear();
    }

    function selfreset(e) {
        alertify.log("You have reset the drawing board!");
        ctx.clear();
    }

    function accepted(e) {
        var username = e.data.replace('ACCEPTED:','');
        alertify.success('connected to server as "'+username+'"');
        var title = username;
        if (window.location.protocol === "file:") {
            title += " - local";
        } else {
            title += ' - draw.ws';
        }
        document.title = title;
    }

    function denied(e) {
        var reason = e.data.replace('DENIED:','');
        var msg = "Unable to connect with that username! ";
        msg +=    "Reason: "+reason+". ";
        msg +=    "Enter new username";
        alertify.prompt(msg, function (e, username) {
            if (e) {
                ws.send('USERNAME:'+username);
            } else {
                ws.send('USERNAME:anonymous');
            }
        }, "anonymous");
    }

    function paintbuffer(e) {
        var params = e.data.split(':');
        var paintbuffer = JSON.parse(params[1]);
        for(var i in paintbuffer){
            arr = paintbuffer[i].split(' ');
            params = arr.splice(0,5);
            params.push(arr.join(' '));
            ctx.draw(params[0], params[1], params[2], params[3], params[4], params[5]);
        }
    }

    function users(e) {
        // var params = e.data.split(':');
        // var userlist = JSON.parse(params[1]);
        // var userlistSpace = document.getElementById("userlistSpace");
        // ul = '</br><b>USERS</b></br>';
        // ul += '<i>'+userlist.length+' user(s) online</i><hr>';
        // for(var i in userlist){
        //     ul += userlist[i] + '</br>';
        // }
        // userlistSpace.innerHTML = ul;
    }

    function error(e) {
        var error = e.data.replace('ERROR:','');
        alertify.log("server error: "+error);
    }

    // determine websocket URI
    // var port = "9001"
    var port = "8080"
    var wsuri;
    if (window.location.protocol === "file:") {
       wsuri = "ws://localhost:"+port;
    } else {
       wsuri = "ws://"+window.location.hostname+":"+port;
    }

    // open websocket
    var ws = null;
    if ("WebSocket" in window) {
       ws = new WebSocket(wsuri);
    } else if ("MozWebSocket" in window) {
       ws = new MozWebSocket(wsuri);
    } else {
       alertify.error("Browser does not support WebSocket!");
    }

    if(ws){
        ws.onopen = function() {
            ws.send('GETBUFFER:');
            alertify.prompt("Enter your username", function (e, username) {
                if (e) {
                    ws.send('USERNAME:' + username);
                } else {
                    ws.send('USERNAME:anonymous');
                }
            }, "anonymous");
        };

        ws.onclose = function() {
            alertify.error('server shut down');
        };

        var handlers = {
            'PAINT': paint,
            'CHAT': chat,
            'INFO': info,
            'RESET': reset,
            'SRESET': selfreset,
            'ACCEPTED': accepted,
            'DENIED': denied,
            'PAINTBUFFER': paintbuffer,
            'USERS': users,
            'ERROR': error
        }

        ws.onmessage = function(e) {
            // alertify.log("server: "+e.data);
            var params = e.data.split(':');
            var header = params[0];
            handlers[header](e);
        };
    }

    /**************************************************************************
    * jQuery animation functions
    **************************************************************************/

    // $(".colorSpace").hover( function(){
    //     $(this).animate({ height: "45", width: "45" }, "fast");
    // }, function(){
    //     $(this).animate({ height: "40", width: "40" }, "fast");
    // });

    // $(".colorSpace").click( function(){
    //     fillColor = $(this).css('background-color');
    //     fillBoxes();
    // });

    // $(".brushSpace").hover( function(){
    //     var size = parseFloat($(this).attr('id'));
    //     $(this).animate({ height: size + 5, width: size + 5 }, "fast");
    // }, function(){
    //     var size = parseFloat($(this).attr('id'));
    //     $(this).animate({ height: size, width: size }, "fast");
    // });

    // $(".brushSpace").click( function(){
    //     linewidth = parseFloat($(this).attr('id'));
    // });

    /**************************************************************************
    * miscellaneous helper functions
    **************************************************************************/

    // function fillBoxes(){
    //     $('.brushSpace').css('background-color', fillColor);
    // }

    // function sendText(e){
    //     ws.send('CHAT:' + inputBox.node.value);
    //     inputBox.node.value = '';
    // }

    ctx.clear();
}

window.onload = function(){
    var container = document.getElementById('canvasSpace');
    init(container, 700, 394); // 16 x 9 canvas
}