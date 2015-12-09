[![](https://badge.imagelayers.io/kevinchabreck/paintchat:latest.svg)](https://imagelayers.io/?images=kevinchabreck/paintchat:latest 'Get your own badge on imagelayers.io')

# PaintChat
A chatroom with support for real-time collaborative drawing. Think MS Paint meets Google Docs!

[Live demo](http://draw.ws)

## Features

-  run a single server or as a cluster
-  consistent, persistant state
-  dynamically add/remove nodes at any time

## Usage

PaintChat requires a [Cassandra](http://cassandra.apache.org/) server instance to run. Launch a quick instance with [Docker](https://www.docker.com/) using the [official Cassandra image](https://hub.docker.com/_/cassandra/):

	$ docker run -d -p 9042:9042 cassandra:2.2.3

Export the IP of your cassandra instance to __CASSANDRA_IP__ (PaintChat will use `127.0.0.1:9042` by default if unbound)

	$ export CASSANDRA_IP=[your Docker machine's IP]

### Run a local PaintChat instance

With Docker:

	$ docker run -it --net=host -e CASSANDRA_IP=$CASSANDRA_IP kevinchabreck/paintchat

With SBT:

	$ sbt run

Multiple instances of PaintChat can be run in parallel on the same host. They will attempt to bind to port 8080, and retry at monotonically increasing port numbers upon failure. The default number of max retry attempts is 3.

### Connect to local PaintChat instance

Point your browser to [http://DOCKERHOST:8080/](http://DOCKERHOST:8080/) (replace `DOCKERHOST` with the ip or hostname of your Docker machine)
