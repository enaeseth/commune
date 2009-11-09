JC = javac
.SUFFIXES: .java .class

.java.class:
	$(JC) $*.java

CLASSES = \
	commune/peer/Peer.java \
	commune/protocol/InvalidMessageException.java \
	commune/protocol/MessageParser.java \
	commune/protocol/Message.java \
	commune/protocol/HelloMessage.java \
	commune/protocol/RequestMessage.java \
	commune/protocol/ResponseMessage.java \
	commune/protocol/PayloadMessage.java \
	commune/protocol/PeerExchangeMessage.java \
	commune/net/Listener.java \
	commune/net/TimeoutTask.java \
	commune/net/Operation.java \
	commune/net/Reactor.java \
	commune/peer/Receiver.java \
	commune/peer/MessageBroker.java \
	commune/source/AvailableResource.java \
	commune/source/AvailableFile.java \
	commune/source/Source.java \
	commune/source/DirectorySource.java \
	commune/source/ResourceManager.java \
	commune/peer/server/Server.java \
	commune/peer/server/ClientConnection.java \
	commune/peer/client/ServerConnection.java \
	commune/peer/client/Client.java \
	commune/peer/client/FutureTask.java \
	commune/peer/client/ServerConnection.java \
	commune/Servent.java

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) commune/*.class commune/protocol/*.class commune/peer/*.class \
		commune/peer/client/*.class commune/peer/server/*.class \
		commune/source/*.class
