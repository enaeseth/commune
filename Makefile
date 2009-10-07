JC = javac
JFLAGS = -g -deprecation
.SUFFIXES: .java .class

.java.class:
	$(JC) $(JFLAGS) $*.java

CLASSES = \
	commune/protocol/Request.java

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) commune/*.class commune/protocol/*.class
