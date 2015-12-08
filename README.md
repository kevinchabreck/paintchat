# PaintChat
A chatroom with support for real-time collaborative drawing. Think MS Paint meets Google Docs!

[Live demo](http://draw.ws)

## Features

-  run a single server or as a cluster
-  consistent, persistant state
-  dynamically add/remove nodes at any time

## Usage

PaintChat requires a Cassandra cluster to run. It will use the value of your __CASSANDRA_IP__ env variable, or search locally at `127.0.0.1:9042` if __CASSANDRA_IP__ is unbound.

