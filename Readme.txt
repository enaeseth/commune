========================
Running the servent:
  1. Run "make". (Java 1.6 shouldn't be needed anymore.)
  2. Run "./run [port]". If no port is given, it defaults to 2666.
========================

If I could go back in time, I would stop myself from doing any of this non-blocking stuff. It is an absolutely tremendous pain to debug.

As stipulated by Amy, payload messages sent from the server to the client have a sequence number giving the offset (in bytes) of the requested file at which the start of the data in the message is located. Upon receipt of a payload message, the client sends an acknowledgement of the sequence number of the last in-order message it received.

I ended up using a window size of 400 bytes (4 messages at a time). With lower values, the socket timeout comes into play more often as the probability that a dropped packet will be the last one in a window increases. (When the last packet is dropped, there is no packet sent after it to indicate to the client that something was missing.) Values much larger than 6 messages cause things to go a bit crazy. Again, why non-blocking? What was I thinking? Also, increasing the size of each message had a much larger impact on transfer time than increasing the number of messages out at once.

A file transfer is set up in the following way:
1. The client sends a request to the server.
2. The server responds, giving a status code and message for the request and some meta-information about the file (size and type) if the request was successful.
3. If the server responds with "OK", the client begins listening for UDP datagrams on a port and requests that the server send it the file over that port.
4. The server sends data until the entire file has been acknowledged.
