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

    def __init__(self):
        super(BroadcastClientProtocol, self).__init__()
        self.setTrackTimings(True)
        self.trackTimings = True
        self.msgId = 0
        self.interval = 0.5
        self.record = []

    def sendHello(self):

        if self.msgId < 3:
            self.sendMessage("PAINT:" + str(clients[self])+" 285 249 285 6 black".encode('utf8'))
            reactor.callLater(self.interval, self.sendHello)
            self.msgId = self.msgId + 1
        else:
            print "Client ", clients[self], " Finished Sending Messages.\nResults:"
            for s in self.record:
                print s
            print "\n****************** Client ", clients[self], " Done ******************\n"

    def onOpen(self):
        self.trackedTimings = Timings()
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
        header = "Client " +  str(clients[self]) +"\n"
        timerecord = str(self.trackedTimings) + "\n"
        payload = payload.decode('utf8')
        record = header + timerecord + payload

        self.record.append(record)



if __name__ == '__main__':

    # if len(sys.argv) < 2:
    #     print("Need the WebSocket server address, i.e. ws://localhost:9000")
    #     sys.exit(1)

    # clients = {}

    for i in range(2):
        # clients[i] = WebSocketClientFactory("ws://localhost:9001")
        # clients[i].protocol = BroadcastClientProtocol

        # f = WebSocketClientFactory("ws://localhost:9001")
        # f.protocol = BroadcastClientProtocol
        # clients[f.protocol] = i
        # connectWS(f)

        f = WebSocketClientFactory("ws://localhost:9001")
        f.protocol = BroadcastClientProtocol
        connectWS(f)
        # f2 = WebSocketClientFactory("ws://localhost:9001")
        # f2.protocol = BroadcastClientProtocol
        # connectWS(f2)

    reactor.run()