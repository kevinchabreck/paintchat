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
    canvas.isFocused = false;
    $(parent).prepend(canvas.node);
    // $(parent).append(canvas.node);
    return canvas;
}

function createSizeSlider(){
    return $('#sizeSlider').noUiSlider({
        start: [6],
        range: {
            'min': 1,
            'max': 40
        }
    });
}

// function createResetButton(parent){
//     var button = {};
//     button.node = document.createElement('input');
//     button.node.type = 'submit';
//     button.node.id = 'reset';
//     button.node.value = "Reset!";
//     parent.appendChild(button.node);
//     return button;
// }

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
    var sizeSlider = createSizeSlider();
    var username = null;
    var oldX = null;
    var oldY = null;
    var fillColor = 'black';
    var linewidth = 5;

    /**************************************************************************
    * Canvas manipulation
    **************************************************************************/

    ctx.draw = function(x1, y1, x2, y2, linewidth, color) {
        this.lineJoin = "round";
        this.strokeStyle = color;
        this.beginPath();
        this.moveTo(x1, y1);
        this.lineTo(x2, y2);
        this.lineWidth = linewidth;
        this.closePath()
        this.stroke();
    };

    ctx.clear = function() {
        ctx.fillStyle = "#eee";
        ctx.fillRect(0, 0, width, height);
    };

    /**************************************************************************
    * Canvas, chat box, and reset button event handlers
    **************************************************************************/

    function move(e) {
        if(canvas.isDrawing){
            e.preventDefault();
            var newX = e.pageX - rect.left;
            var newY = e.pageY - rect.top;
            // console.log("newX: "+newX+" newY: "+newY);
            // console.log("e.pageX: "+e.pageX+" e.pageY: "+e.pageY);
            // console.log("this.offsetLeft: "+this.offsetLeft+" this.offsetTop: "+this.offsetTop);
            ws.send('PAINT:'+oldX+' '+oldY+' '+newX+' '+newY+' '+linewidth+' '+fillColor);
            oldX = newX;
            oldY = newY;
        }
    }

    function start(e) {
        e.preventDefault();
        oldX = e.pageX - rect.left;
        oldY = e.pageY - rect.top;
        canvas.isDrawing = true;
    }

    function stop(e) {
        canvas.isDrawing = false;
    }

    function focus(e) {
        oldX = e.pageX - rect.left;
        oldY = e.pageY - rect.top;
        canvas.isFocused = true;
    }

    function unfocus(e) {
        canvas.isFocused = false;
    }

    canvas.node.onmousemove = move
    canvas.node.onmousedown = start
    canvas.node.onmouseup = stop
    canvas.node.onmouseover = focus
    canvas.node.onmouseout = unfocus

    canvas.node.ontouchmove = move
    canvas.node.ontouchstart = start
    canvas.node.ontouchend = stop

    $('#sizeSlider').on('slide', function(e){
        linewidth = $("#sizeSlider").val();
    });

    $(document).on('mousedown', start);
    $(document).on('mouseup', stop);

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
        // var msg = e.data.replace('INFO:','');
        // msg = '<i>'+msg+'</i>';
        // var messageSpace = document.getElementById("messagesSpace");
        // messageSpace.innerHTML += msg + '</br></br>';
        // messageSpace.scrollTop = messageSpace.scrollHeight;
        // messageSpace.focus();
    }

    function reset(e) {
        ctx.clear();
    }

    function accepted(e) {
        var title = username;
        if (window.location.protocol === "file:") {
            title += " - local";
        } else {
            title += ' - draw.ws';
        }
        document.title = title;
        ws.send('GETPAINTBUFFER:');
    }

    function denied(e) {
        var reason = e.data.replace('DENIED:','');
        username = window.prompt("Denied!\nReason: "+reason+"\nEnter new username");
        ws.send('USERNAME:' + username);
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

    // determine websocket URI
    var wsuri;
    if (window.location.protocol === "file:") {
       wsuri = "ws://localhost:9000";
    } else {
       wsuri = "ws://" + window.location.hostname + ":9000";
    }

    // open websocket
    var ws = null;
    if ("WebSocket" in window) {
       ws = new WebSocket(wsuri);
    } else if ("MozWebSocket" in window) {
       ws = new MozWebSocket(wsuri);
    } else {
       alert("Browser does not support WebSocket!");
       window.location = "http://autobahn.ws/unsupportedbrowser";
    }

    if(ws){
        ws.onopen = function() {
            username = window.prompt("Enter your username");
            ws.send('USERNAME:' + username);
        };

        ws.onclose = function() {
            alert('server shut down');
        };

        var handlers = {
            'PAINT': paint,
            'CHAT': chat,
            'INFO': info,
            'RESET': reset,
            'ACCEPTED': accepted,
            'DENIED': denied,
            'PAINTBUFFER': paintbuffer,
            'USERS': users
        }

        ws.onmessage = function(e) {
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