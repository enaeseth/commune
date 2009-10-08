JC = javac
JFLAGS = -g -deprecation
.SUFFIXES: .java .class

.java.class:
	$(JC) $(JFLAGS) $*.java

CLASSES = \
	commune/protocol/InvalidMessageException.java \
	commune/protocol/Message.java \
	commune/protocol/InvalidRequestException.java \
	commune/protocol/Request.java \
	commune/protocol/InvalidResponseException.java \
	commune/protocol/Response.java \
	commune/peer/server/AvailableResource.java \
	commune/peer/server/AvailableFile.java \
	commune/peer/server/Source.java \
	commune/peer/server/DirectorySource.java \
	commune/peer/server/ResourceServer.java

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) commune/*.class commune/protocol/*.class
