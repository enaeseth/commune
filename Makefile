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
	commune/protocol/Response.java

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) commune/*.class commune/protocol/*.class
