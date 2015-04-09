#!/usr/bin/python

##############################################################################
#
# @program: 	paintserver.py
# @description:	a python-based server for a collaborative drawing chatroom
#
##############################################################################

from twisted.internet import reactor
from autobahn.websocket import WebSocketServerFactory, WebSocketServerProtocol, listenWS
import json

clicks = 0;

# @class: 		PaintProtocol
# @description: a protocol with event handlers for opening connections, closing
#				connections, and recieving messages from clients. Required by
#				the PaintFactory class
# @extends: 		WebSocketServerProtocol
class PaintProtocol(WebSocketServerProtocol):

	# @function: 	onOpen
	# @description:	handles the event of establishing a new connection
	def onOpen(self):
		self.factory.registerConnection(self)

	# @function:	connectionLost
	# @description: handles the event of losing a connection			
	def connectionLost(self, reason):
		WebSocketServerProtocol.connectionLost(self, reason)
		self.factory.unregister(self)

	# @function: 	onMessage
	# @description:	handles the event of recieving a message from a client.
	# 				possible message headers: PAINT, CHAT, RESET, USERNAME, 
	#				GETPAINTBUFFER
	def onMessage(self, data, binary):
		print data
		header = data.split(':')[0]
		if header == 'GETPAINTBUFFER':
			self.factory.sendPaintBuffer(self)
		elif header == 'USERNAME':
			self.factory.checkName(self, data.replace('USERNAME:','',1))
		elif self in factory.CLIENTS.keys():
			if header == 'PAINT' or header == 'RESET':
				self.factory.updateBuffer(data)
			elif header == 'CHAT':
				user = self.factory.CLIENTS[self]
				data = data.replace('CHAT:','CHAT:'+user+': ', 1)
			self.factory.updateClients(data)
		else:
			self.sendMessage('DENIED:unregistered user')
			
# @class:		PaintFactory
# @description:	manages connected clients - registers/unregisters connections,
#				broadcasts messages to connected clients. Also responsible for
#				paint buffer managment
# @extends WebSocketServerFactory
class PaintFactory(WebSocketServerFactory):

	# @function:	init
	# @description: constructor function - initializes the global 
	#				client/connection/buffer structures
	# @param:		url - the URL address to listen for connections/messages on
	def __init__(self, url):
		WebSocketServerFactory.__init__(self, url)
		self.CONNECTIONS = []
		self.CLIENTS = {}
		self.PAINTBUFFER = []

	# @function:	registerConnection
	# @description: adds client to list of connection
	# @param:		client - the cient to add to the connections list
	def registerConnection(self, client):
		if not client in self.CONNECTIONS:
			print "registered connection " + client.peerstr
			self.CONNECTIONS.append(client)

	# @function:	registerClient
	# @description: adds client to list of connection
	# @param:		client - the cient to add to the client dictionary
	def registerClient(self, client, username):
		if not client in self.CLIENTS.keys():
			self.CLIENTS[client] = username
			print "registered client " + username
			msg = 'INFO:{} has joined the chat'.format(username)
			self.updateClients(msg)
			userlist = 'USERS:' + json.dumps(self.CLIENTS.values())
			self.updateClients(userlist)

	# @function:	unregister
	# @description: removes client to list of connection
	# @param:		client - the cient to remove from the client dictionary
	def unregister(self, client):
		if client in self.CONNECTIONS:
			self.CONNECTIONS.remove(client)
			print "unregistered CONNECTION " + client.peerstr
		if client in self.CLIENTS.keys():
			user = self.CLIENTS[client]
			del self.CLIENTS[client]
			print "unregistered CLIENT " + user
			msg = 'INFO:{} has left the chat'.format(user)
			self.updateClients(msg)
			userlist = 'USERS:' + json.dumps(self.CLIENTS.values())
			self.updateClients(userlist)

	# @function: updateClients
	# @description: updates all clients with the given message
	# @param: msg - the message to send to the clients
	def updateClients(self, msg):
		for c in self.CONNECTIONS:
			c.sendMessage(msg)
			print "update *"+msg+"* sent to " + c.peerstr

	# @function: checkName
	# @description: Checks that a client's requested username is valid.
	# @param: client - the client requesting a username
	# @param: username - the requested username.
	def checkName(self, client, username):
		if ':' in username:
			client.sendMessage('DENIED:invalid character ":"')
		elif username.isspace() or username == '':
			client.sendMessage('DENIED:username must have at least one alphanumeric char')
		elif username == 'null' or username == 'undefined':
			client.sendMessage('DENIED:username cannot be null or undefined')
		elif username.lower() in [x.lower() for x in self.CLIENTS.values()]:
			client.sendMessage('DENIED:username in use')
		else:
			self.registerClient(client, username)
			client.sendMessage('ACCEPTED:')

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
	def sendUserList(self, client):
		print 'sending userlist'
		client.sendMessage('USERS:' + json.dumps(self.CLIENTS.values()))		

if __name__ == '__main__':
	print 'server is running'
	# define a factory
	factory = PaintFactory("ws://localhost:15013")
	# assign it a protocol
	factory.protocol = PaintProtocol
	# start listening
	listenWS(factory)
	reactor.run()
