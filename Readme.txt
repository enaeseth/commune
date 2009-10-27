Commune
by Eric Naeseth

========================
Running the servent:
  1. Run "make". (Java 1.6 may be required.)
  2. Run "./run [port]". If no port is given, it defaults to 2666.
========================

I picked a pretty ambitious design for this servent, as you can probably tell
by the sheer number of classes involved in it. That was probably a mistake,
since I came quite close to running out of time, but it does mostly work.

The protocol is based largely on HTTP: the client sends the server a GET
request, the server responds with either an error response and an associated
message or an OK response and the content of the requested file. After the
client gets the response, it sends its own acknowledgement to the server saying
that it accepted the response, and then the server closes the connection.
Currently the only response messages are "OK" and "Not Found".

The server and the client both employ timeouts to make sure that the connection
isn't simply held open forever. If it's one side's turn to send something or
receive something, and that side doesn't do so within a certain number of
seconds (it varies from stage to stage), the connection is closed. When an
invalid protocol message is received by either end, it reports the error and
closes the connection to its peer.

The servent uses a non-blocking architecture. In Java, you traditionally deal
with multiple connections at once by creating threads. Each network call (e.g.,
accept(), read()) will stall until it finishes its task, so threads are
necessary so that your entire application does not stall while dealing with one
peer.

A non-blocking design only requires one network thread. Generally speaking, it
waits until one or more of its sockets is ready to do something, it handles
those sockets, and then goes back to waiting. As long as the things that happen
when a socket is ready for action take place reasonably quickly, there's no
need to create multiple threads.

Commune's Reactor class is what manages the network thread. Other parts of the
application register their sockets with the Reactor: they ask the Reactor to
watch the socket to see when it's ready to perform a particular task (accepting
a connection, reading data from a peer, etc.), and to call a method on an
object when that happens. This makes the server code quite nice: there's one
nested class for each "state" a connection can be in: first accepting the
connection, receiving the request, sending the response, and receiving the
acknowledgement. (Unfortunately, the client code is a bit messier since I was
running out of time.)
