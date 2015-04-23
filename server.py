##############################################################################
#
# @program:     paintserver.py
# @description: a python-based server for a collaborative drawing chatroom
#
##############################################################################

from twisted.internet import reactor
from autobahn.twisted.websocket import WebSocketServerFactory, WebSocketServerProtocol, listenWS
import json

clicks = 0;

# @class:       PaintProtocol
# @description: a protocol with event handlers for opening connections, closing
#               connections, and recieving messages from clients. Required by
#               the PaintFactory class
# @extends:     WebSocketServerProtocol
class PaintProtocol(WebSocketServerProtocol):

    # @function:    onOpen
    # @description: handles the event of establishing a new connection
    def onOpen(self):
        print "WebSocket connection open."
        self.factory.registerConnection(self)

    # @function:    connectionLost
    # @description: handles the event of losing a connection
    def connectionLost(self, reason):
        WebSocketServerProtocol.connectionLost(self, reason)
        self.factory.unregister(self)

    # @function:    onMessage
    # @description: handles the event of recieving a message from a client.
    #               possible message headers: PAINT, CHAT, RESET, USERNAME,
    #               GETPAINTBUFFER
    def onMessage(self, data, binary):
        print data
        header = data.split(':')[0]
        if header == 'GETPAINTBUFFER':
            self.factory.sendPaintBuffer(self)
        elif header == 'USERNAME':
            self.factory.checkName(self, data.replace('USERNAME:','',1))
        elif self in factory.CLIENTS:
            if header == 'PAINT' or header == 'RESET':
                self.factory.updateBuffer(data)
            elif header == 'CHAT':
                user = self.factory.CLIENTS[self]
                data = data.replace('CHAT:','CHAT:'+user+': ', 1)
            self.factory.updateAll(data)
        else:
            self.sendMessage('DENIED:unregistered user')

# @class:       PaintFactory
# @description: manages connected clients - registers/unregisters connections,
#               broadcasts messages to connected clients. Also responsible for
#               paint buffer managment
# @extends WebSocketServerFactory
class PaintFactory(WebSocketServerFactory):

    # @function:    init
    # @description: constructor function - initializes the global
    #               client/connection/buffer structures
    # @param:       url - the URL address to listen for connections/messages on
    def __init__(self, url):
        WebSocketServerFactory.__init__(self, url)
        self.CLIENTS = {}
        self.USERNAMES = {}
        self.PAINTBUFFER = []

    # @function:    registerConnection
    # @description: adds client to list of connection
    # @param:       client - the cient to add to the connections list
    def registerConnection(self, client):
        if client not in self.CLIENTS:
            print "registered connection " + client.peer
            self.CLIENTS[client] = ""
        else:
            print "unable to register connection"

    # @function:    registerClient
    # @description: adds client to list of connection
    # @param:       client - the cient to add to the client dictionary
    def newUser(self, client, username):
        if client in self.CLIENTS:
            self.updateAllExcept(client, 'INFO:'+username+' has joined')
            self.updateAll('USERS:'+str(len(self.USERNAMES)))
            self.CLIENTS[client] = username
            print "registered client "+username
        else:
            print "unrecognized client"

    # @function:    unregister
    # @description: removes client to list of connection
    # @param:       client - the cient to remove from the client dictionary
    def unregister(self, client):
        # print "unregistering"
        # print "self.CLIENTS:"
        # print self.CLIENTS
        # print "self.USERNAMES"
        # print self.USERNAMES
        if client in self.CLIENTS:
            username = self.CLIENTS[client]
            del self.CLIENTS[client]
            u = username.split("(")[0]
            self.USERNAMES[u][0]-=1
            if self.USERNAMES[u][0] <= 0:
                del self.USERNAMES[u]
            print "unregistered connection "+client.peer+" ("+username+")"
            self.updateAll('INFO:'+username+' has left')
            self.updateAll('USERS:'+str(len(self.CLIENTS)))

    # @function: updateClients
    # @description: updates all clients with the given message
    # @param: msg - the message to send to the clients
    # def updateClients(self, msg):
    def updateAll(self, msg):
        for c in self.CLIENTS:
            c.sendMessage(msg)
            print "update <"+msg+"> sent to "+c.peer+" ("+self.CLIENTS[c]+")"

    def updateAllExcept(self, client, msg):
        for c in self.CLIENTS:
            if c != client:
                c.sendMessage(msg)
                print "update <"+msg+"> sent to "+c.peer+" ("+self.CLIENTS[c]+")"

    # @function: checkName
    # @description: Checks that a client's requested username is valid.
    # @param: client - the client requesting a username
    # @param: username - the requested username.
    def checkName(self, client, username):
        username = username.lower()
        if ':' in username:
            client.sendMessage('DENIED:invalid character ":"')
        elif '(' in username:
            client.sendMessage('DENIED:invalid character "("')
        elif ')' in username:
            client.sendMessage('DENIED:invalid character ")"')
        elif username.isspace() or username == '':
            client.sendMessage('DENIED:username must have at least one alphanumeric char')
        elif username == 'null' or username == 'undefined':
            client.sendMessage('DENIED:username cannot be null or undefined')
        else:
            username = username.lower()
            if username in self.USERNAMES:
                print "duplicate name"
                self.USERNAMES[username][0]+= 1
                self.USERNAMES[username][1]+= 1
                username+="("+str(self.USERNAMES[username][1])+")"
            else:
                self.USERNAMES[username] = [1,1]
            self.newUser(client, username)
            client.sendMessage('ACCEPTED:'+username)

    # @function: updateBuffer
    # @description: Update the paintbuffer with a paint message
    # @param: msg - the paint message to use
    def updateBuffer(self, msg):
        if msg == 'RESET:':
            self.PAINTBUFFER = []
        else:
            self.PAINTBUFFER.append(msg.replace('PAINT:', ''))
            print 'added ' + msg.replace('PAINT:', '') + ' to buffer'

    # @function: sendPaintBuffer
    # @description: Sends the history of paint commands to a client.
    # @param: client - the client to send the paint buffer to.
    def sendPaintBuffer(self, client):
        print 'sending paint buffer'
        client.sendMessage('PAINTBUFFER:' + json.dumps(self.PAINTBUFFER))

    # @function: sendUserList
    # @description: Sends the list of current users to a client.
    # @param: client - the client to set the list of users to.
    # def sendUserCount(self, client):
    #     print 'sending usercount'
    #     client.sendMessage('USERS:'+str(len(self.USERNAMES)))

if __name__ == '__main__':
    port = '9001'
    print 'starting server on port '+port
    # define a factory
    factory = PaintFactory("ws://localhost:"+port)
    # assign it a protocol
    factory.protocol = PaintProtocol
    # start listening
    factory.setProtocolOptions(allowHixie76=True)
    listenWS(factory)
    reactor.run()
