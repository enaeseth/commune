Commune
=======

Commune is a peer-to-peer file transfer protocol, created for 
[Carleton College](http://www.carleton.edu/)'s Computer Networks course.

This repository contains the reference implementation of the Commune protocol.
Commune has a full [protocol specification][spec], written in the style of
RFC's. The protocol is extendable; peers indicate the set of optional protocol
facilities they support in their User-Agent string, which is sent during
the protocol's handshake and retransmitted by its peer exchange mechanism.

The reference implementation attempts to be a modern Java application. It uses
the `java.nio` package to serve an arbitrary number of connections from a
single thread. Running it requires Java 1.5 or newer.

The implementation code is organized into a number of packages:

  - `commune.net`: low-level code for performing asynchronous networking
  - `commune.protocol`: classes that encode the protocol's messages, and who
    know how to create and unpack their binary representations to and from
    buffers
  - `commune.source`: classes and interfaces for locating and making available
    resources to other peers
  - `commune.peer`: all the business logic of implementing the protocol.
  
[spec]: http://code.naeseth.com/cs/commune_spec.pdf

Running Commune
---------------

To run the reference implementation, first compile it by executing `make` or
running `javac commune/Commune.java` from the main directory.

After that, run `java commune.Commune`. An interactive prompt will be
presented; type `help` for a list of available commands.

License
-------

The Commune reference implementation is made available under the terms of the
MIT License.

Copyright © 2009 [Eric Naeseth][copyright_holder]

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

[copyright_holder]: http://github.com/enaeseth/
