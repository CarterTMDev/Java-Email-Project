JFLAGS = -g
JC = javac

.SUFFIXES: .java .class

.java.class:
	$(JC) $(JFLAGS) $*.java

CLASSES = \
		  Server.java \
		  ServerThread.java \
		  HTTP.java \
		  SMTP.java \
		  Login.java \
		  Domain.java

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class