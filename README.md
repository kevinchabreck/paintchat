# PaintChat
A chatroom with support for real-time collaborative drawing. Think MS Paint meets Google Docs!

[Live demo](http://draw.ws)

## Features

-  run a single server or as a cluster
-  consistent, persistant state
-  dynamically add/remove nodes at any time

## Usage

PaintChat requires a [Cassandra](http://cassandra.apache.org/) instance to run. It will use the value of your __CASSANDRA_IP__ env variable, or search locally at `127.0.0.1:9042` if __CASSANDRA_IP__ is unbound.

Launch a quick Cassandra instance with [Docker](https://www.docker.com/):

	$ docker pull cassandra && docker run -d -p 9042:9042 && export CASSANDRA_IP=$DOCKER_IP

### Run a local PaintChat instance

With SBT:

	$ sbt run

With Docker:

	$ docker run -d --net=host -e CASSANDRA_IP=$CASSANDRA_IP kevinchabreck/paintchat

Multiple instances of PaintChat can be run in parallel on the same host. They will attempt to bind to port 8080, and retry at monotonically increasing port numbers upon failure. The default number of max retry attempts is 3.

### Connect to local PaintChat instance

Point your browser at [http://localhost:8080/](http://localhost:8080/) (replace `localhost` with the ip of your Docker machine if you launched PaintChat in a container)
