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

function init(container, width, height) {

  /**************************************************************************
  * 'global' variables and element initialization
  **************************************************************************/

  var canvas = createCanvas(container, width, height);
  var ctx = canvas.context;
  var rect = canvas.node.getBoundingClientRect();
  createPalette();
  var username = null;
  var oldX = null;
  var oldY = null;
  var fillColor = 'black';
  var linewidth = 6;
  var isTyping = true;

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
  }

  function start(e) {
    var id = e.target.id
    if((id=="chatInput")||(id=="sendChatButton")){

    } else{
      e.preventDefault();
      if(typeof e.originalEvent.touches != 'undefined'){
        e = e.originalEvent.touches[0];
      }
      oldX = e.pageX - rect.left;
      oldY = e.pageY - rect.top;
      canvas.isDrawing = true;
    }
  }

  function stop(e) {
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
    ws.send('RESET:');
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
    }
  }

  function decSize(e){
    if (linewidth > 4){
      linewidth-=4;
    }
  }

  function useBrush(e){
    $('#colorPalette').addClass("active").siblings().removeClass("active");
    canvas.isErasing = false;
  }

  function startErase(e){
    $('#selectEraser').addClass("active").siblings().removeClass("active");
    canvas.isErasing = true;
  }

  function toggleChat(e){
    $('#chatSpace').slideToggle("fast");
    $('#chatInput').focus();
  }

  function toggleUser(e){
    $('#usersgroup').slideToggle("fast");
  }

  function sendChat(e){
    if((e.type == "click") || $('#chatInput').is(':focus')){
      msg = $('#chatInput').val();
      ws.send('CHAT:'+msg);
      $('#chatInput').val("");
    }
  }

  function keypress(e){
    if(e.which == 114){ // "r"
      // sendReset(e);
    } else if(e.which == 13) { // "enter"
      sendChat(e);
    }
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

  $('#colorPalette').on('click tap', useBrush);
  $('#sizePlus').on('click tap', incSize);
  $('#sizeMinus').on('click tap', decSize);
  $('#selectEraser').on('click tap', startErase);
  $('#selectChat').on('click tap', toggleChat);
  $('#selectUsers').on('click tap', toggleUser);
  $('#resetCanvas').on('click tap', sendReset);

  $(document).on('mousemove touchmove', move);
  $(document).on('mousedown', start);
  $(document).on('mouseup touchend', stop);
  $(document).on('keypress', keypress);

  $("#colorPalette").on('change.spectrum', changeColor);
  $("#sendChatButton").on("click", sendChat);

  $('.btn-group [title]').tooltip({
    container: 'body',
    delay: 5
  });

  /**************************************************************************
  * WebSocket event handlers
  **************************************************************************/

  function paint(e) {
    var params = e.data.split(':');
    var arr = params[1].split(' ');
    params = arr.splice(0,6);
    params.push(arr.join(' '));
    ctx.draw(params[0], params[1], params[2], params[3], params[4], params[5]);
  }

  function chat(e) {
    var m = e.data
    var sender = m.split(':',2)[1];
    var msg = m.substring(m.indexOf(":", m.indexOf(":")+1)+1);
    noty({
      text: '<b>'+sender+'</b>: '+msg,
      layout: 'topRight',
      type: 'information',
      animation: {
        open: 'animated bounceInRight',
        close: 'animated fadeOut'
      },
      timeout: 4000,
      maxVisible: 0,
      theme: 'relax'
    });
  }

  function info(e) {
    var username = e.data.split(":")[1].split(" ")[0];
    var msg = e.data.split(":")[1].split(" ")[2];
    if (msg == "joined"){
      createUserIcon(username);
    }else{
      deleteUserIcon(username);
    }

    var info = e.data.replace('INFO:','');
    noty({
      text: "user "+info,
      layout: 'topRight',
      type: 'warning',
      animation: {
        open: 'animated bounceInRight',
        close: 'animated fadeOut'
      },
      timeout: 4000,
      maxVisible: 0,
      theme: 'relax'
    });
  }

  function reset(e) {
    var username = e.data.replace('RESET:','');
    noty({
      text: "user "+username+" has reset the drawing board!",
      layout: 'topRight',
      type: 'warning',
      animation: {
        open: 'animated bounceInRight',
        close: 'animated fadeOut'
      },
      timeout: 4000,
      maxVisible: 0,
      theme: 'relax'
    });
    ctx.clear();
  }

  function selfreset(e) {
    noty({
      text: "You have reset the drawing board!",
      layout: 'topRight',
      type: 'warning',
      animation: {
        open: 'animated bounceInRight',
        close: 'animated fadeOut'
      },
      timeout: 4000,
      maxVisible: 0,
      theme: 'relax'
    });
    ctx.clear();
  }

  // var usercount = 0;
  function accepted(e) {
    // document.getElementById("Usersbadge").innerHTML = usercount;
    var username = e.data.replace('ACCEPTED:','');
    createUserIcon(username); 
    noty({
      text: 'connected to server as "'+username+'"',
      layout: 'top',
      type: 'success',
      animation: {
        open: 'animated bounceInDown',
        close: 'animated fadeOut'
      },
      timeout: 4000,
      theme: 'relax'
    });
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
    alertify.prompt(msg, function (e, name) {
      if (e) {
        ws.send('USERNAME:'+name);
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
      params = arr.splice(0,6);
      params.push(arr.join(' '));
      ctx.draw(params[0], params[1], params[2], params[3], params[4], params[5]);
    }
  }

  function usercoutupdate(e) {
    var usercount = e.data.split(':')[1];
    document.getElementById("Usersbadge").innerHTML = usercount;
  }


  function userlist(e) {
    var users = e.data.split(':')[1].split(" ");
    if (users != ''){
      for(var i in users){
          createUserIcon(users[i]);
      }
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



  function servererror(e) {
    noty({
      text: "server error: "+e.message,
      layout: 'top',
      type: 'error',
      animation: {
        open: 'animated bounceInDown',
        close: 'animated fadeOut'
      },
      timeout: 0,
      maxVisible: 0,
      theme: 'relax'
    });
  }

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
    'USERCOUNT': usercoutupdate,
    'USERLIST': userlist,
    'ERROR': servererror
  }

  var wsuri = window.location.href.replace("http", "ws");
  console.log("wsuri: "+wsuri)

  // open websocket
  var ws = null;
  if ("WebSocket" in window) {
     ws = new WebSocket(wsuri);
  } else if ("MozWebSocket" in window) {
     ws = new MozWebSocket(wsuri);
  } else {
     servererror({message:"Browser does not support WebSocket!"});
  }

  if(ws){
    ws.onopen = function() {
      ws.send('GETBUFFER:');
      ws.send('GETUSERLIST:');
      alertify.prompt("Enter your username", function (e, username) {
        if (e) {
          ws.send('USERNAME:' + username);
        } else {
          ws.send('USERNAME:anonymous');
        }
      }, "anonymous");
    };

    ws.onclose = function(e) {
      var msg = '';
      if (e.code == 1006){
        msg = 'Could not reach WebSocket server at '+wsuri
      } else {
        msg = 'Server disconnected: '+e.code+' - reason: '+e.reason
      }
      e.message = msg;
      servererror(e);
    };

    ws.onmessage = function(e) {
      var params = e.data.split(':');
      var header = params[0];
      handlers[header](e);
    };
  }

  ctx.clear();
}

function deleteUserIcon(username){

  $("#"+username).remove();
}

function createUserIcon(username){

    $("#usersgroup").append($("<canvas/>")
            .attr({id: username, width: 35, height: 35})
            .tooltip({title: username, placement: "top"})
            .jdenticon(md5(username))
            );

}

window.onload = function(){
  var container = document.getElementById('canvasSpace');
  init(container, 700, 394); // 16 x 9 canvas
}