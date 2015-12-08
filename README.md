# PaintChat
A chatroom with support for real-time collaborative drawing. Think MS Paint meets Google Docs!

[Live demo](http://draw.ws)

## Features

-  Can run a single server or as a cluster
-  Consistent, persistant state
-  Dynamically add/remove nodes

## Usage

PaintChat requires a Cassandra cluster to run. It will use the value of your __CASSANDRA_IP__ env variable, or search locally at `127.0.0.1:9042` if __CASSANDRA_IP__ is unbound.

