Eric Naeseth
P2P Neighbor Discovery (CS 331)
===============================

To run the servent, first compile it by running "make". The servent now takes a
few command-line options: -p sets the listening port, and -l sets the soft
limit on the number of connections the servent can have open. After that,
specify any peers you want to connect to as host:port. (If no port is given,
the default listening port of 2375 will be assumed).
By default, the connection limit is 3, which just seemed like a good number:
not too many connections, but not so few as to risk being disconnected from all
peers when some go down.

Peers are represented in the new commune.peer.Peer class. Every peer generates
a random 64-bit ID when it starts up, and it sends this ID in the "hello"
message it uses to establish a connection. This ID uniquely identifies a peer
throughout the network, which (among other things), is a good way to prevent
peers from connecting to themselves when they get a discovery message for that
peer.

This version of the protocol adds a new "peer exchange" message. When a peer
gets a new "hello" message from a peer that has added "(PEX)" to its user agent
string, the receiving peer responds with its own "hello" (as before) and then
also sends a peer exchange message with its response flag set to FALSE, and
attaches the list of peers to it. Each entry in the peer list includes the
peer's unique ID, address and port, user-agent, and age: the number of
milliseconds that have elapsed since a message was last received from that
peer.

When a peer gets an exchange message with a FALSE response flag, it responds
with its own peer exchange message. Since it's a response to another message,
it sets this one's response flag to TRUE. It assembles a list of all its known
peers, removes all of the peers that were just received in the first exchange,
and sends this list back as the response.

To facilitate discovery, peers now send the port number they are listening on
as part of the "hello" message. Without this, when some peer A connects to some
peer B, B would not be able to exchange A with anyone, as B would only see the
random, non-listening port that A used to open the connection.

Servents check all connections' health (and maintain a coherent view of the
network) by periodically sending peer exchange messages across each open
connections. It starts an exchange with a new connection every (rand(10, 25) /
len(connections)) seconds.
