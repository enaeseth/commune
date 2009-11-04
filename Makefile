JC = javac
.SUFFIXES: .java .class

.java.class:
	$(JC) $*.java

CLASSES = \
	commune/protocol/InvalidMessageException.java \
	commune/protocol/MessageParser.java \
	commune/protocol/Message.java \
	commune/protocol/HelloMessage.java \
	commune/protocol/RequestMessage.java \
	commune/protocol/ResponseMessage.java \
	commune/protocol/TransferStartMessage.java \
	commune/protocol/PayloadMessage.java \
	commune/protocol/AcknowledgementMessage.java \
	commune/net/Listener.java \
	commune/net/TimeoutTask.java \
	commune/net/Operation.java \
	commune/net/Reactor.java \
	commune/peer/source/AvailableResource.java \
	commune/peer/source/AvailableFile.java \
	commune/peer/source/Source.java \
	commune/peer/source/DirectorySource.java \
	commune/peer/source/ResourceManager.java \
	commune/peer/ChannelListener.java \
	commune/peer/Reactor.java \
	commune/peer/Connection.java \
	commune/peer/client/FutureTask.java \
	commune/peer/client/TransferConnection.java \
	commune/peer/client/ServerConnection.java \
	commune/peer/client/Client.java \
	commune/peer/server/TransferConnection.java \
	commune/peer/server/ClientConnection.java \
	commune/peer/server/Server.java \
	commune/Servent.java

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) commune/*.class commune/protocol/*.class commune/peer/*.class \
		commune/peer/client/*.class commune/peer/server/*.class \
		commune/peer/source/*.class
