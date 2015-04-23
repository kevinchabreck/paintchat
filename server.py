##############################################################################
#
# @program:     server.py
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
        print "new connection: "+self.peer
        self.factory.registerConnection(self)

    # @function:    connectionLost
    # @description: handles the event of losing a connection
    def connectionLost(self, reason):
        WebSocketServerProtocol.connectionLost(self, reason)
        self.factory.unregister(self)

    def onMessage(self, data, binary):
        print "recieved <"+data+"> from "+self.peer+" ("+factory.CLIENTS[self]+")"
        (header, _, msg) = data.partition(':')
        if header in self.factory.handlers:
            self.factory.handlers[header](self, msg)
        else:
            self.sendMessage('ERROR:unrecognized header '+header)

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
        self.handlers = {
            'GETBUFFER':self.sendBuffer,
            'USERNAME':self.checkName,
            'PAINT':self.updateBuffer,
            'RESET':self.resetBuffer,
            'CHAT':self.sendChat
        }

    # @function:    registerConnection
    # @description: adds client to list of connection
    # @param:       client - the cient to add to the connections list
    def registerConnection(self, client):
        if client not in self.CLIENTS:
            print 'registered connection '+client.peer
            self.CLIENTS[client] = ''
        else:
            print 'unable to register connection'

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

    # @function: updateAll
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
    # def checkName(self, client, username):
    def checkName(self, client, data):
        username = data.lower()
        problems = []
        blacklist = set([':','(',')','{','}','<','>','\\','/','null','undefined'])
        for c in blacklist.intersection(username):
            problems.append('illegal character "'+c+'" ')
        if username.isspace() or username == '':
            problems.append('username must have at least one alphanumeric char\n')
        nameBlacklist = set(['null','undefined'])
        if username in blacklist:
            problems.append('this username is blacklisted\n')
        if problems:
            msg = 'DENIED:'
            for p in problems:
                msg+=p
            client.sendMessage(msg)
        else:
            # username = username.lower()
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
    # def updateBuffer(self, msg):
    def updateBuffer(self, client, data):
        print 'updating paint buffer'
        self.PAINTBUFFER.append(data)
        self.updateAll('PAINT:'+data)

    # def resetBuffer(self, client):
    # def resetBuffer(self, client, data):
    def resetBuffer(self, client, *_):
        print 'clearing paint buffer'
        self.PAINTBUFFER = []
        self.updateAll("RESET:"+self.CLIENTS[client])

    # @function: sendBuffer
    # @description: Sends the history of paint commands to a client.
    # @param: client - the client to send the paint buffer to.
    def sendBuffer(self, client, *_):
        print 'sending paint buffer to '+client.peer
        client.sendMessage('PAINTBUFFER:' + json.dumps(self.PAINTBUFFER))

    def sendChat(self, client, data):
        print 'recieved chat: '+data
        user = self.CLIENTS[client]
        self.updateAll('CHAT:'+user+': '+data)

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
