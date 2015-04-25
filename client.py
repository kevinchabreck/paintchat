import sys
from twisted.internet import reactor
from autobahn.twisted.websocket import WebSocketClientFactory, \
    WebSocketClientProtocol, \
    connectWS
from autobahn.websocket.protocol import Timings, WebSocketProtocol

clients = {}
counter = 0

class BroadcastClientProtocol(WebSocketClientProtocol):

    """
    Simple client that connects to a WebSocket server, send a HELLO
    message every 2 seconds and print everything it receives.
    """

    # def __init__(self):
    #     # super(WebSocketClientProtocol, self).setTrackTimings(True)
    #     self.setTrackTimings(True)

    def sendHello(self):
        self.sendMessage("PAINT:" + str(clients[self])+" 285 249 285 6 black".encode('utf8'))
        reactor.callLater(2, self.sendHello)

    def onOpen(self):
        self.sendMessage("GETBUFFER:")
        global counter
        # print "*************counter is now: ", counter, " ***********"
        clients[self] = counter
        # print "*************Mapping added ***********"
        self.sendMessage('USERNAME:' + "client_"+ str(counter))
        counter = counter + 1
        self.sendHello()

    def onMessage(self, payload, isBinary):
        # if not isBinary:
        self.trackedTimings.diff("sendMessage", "onMessage", format=True)
        print("Text message received: {}".format(payload.decode('utf8')))


if __name__ == '__main__':

    # if len(sys.argv) < 2:
    #     print("Need the WebSocket server address, i.e. ws://localhost:9000")
    #     sys.exit(1)

    # clients = {}

    for i in range(1):
        # clients[i] = WebSocketClientFactory("ws://localhost:9001")
        # clients[i].protocol = BroadcastClientProtocol

        # f = WebSocketClientFactory("ws://localhost:9001")
        # f.protocol = BroadcastClientProtocol
        # clients[f.protocol] = i
        # connectWS(f)

        f = WebSocketClientFactory("ws://localhost:9001")
        f.protocol = BroadcastClientProtocol
        f.trackTimings=True
        connectWS(f)

    reactor.run()