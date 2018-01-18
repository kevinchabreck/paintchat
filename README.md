# PaintChat

[![Build Status](https://travis-ci.org/kevinchabreck/paintchat.svg?branch=master)](https://travis-ci.org/kevinchabreck/paintchat) [![](https://badge.imagelayers.io/kevinchabreck/paintchat:latest.svg)](https://imagelayers.io/?images=kevinchabreck/paintchat:latest 'Get your own badge on imagelayers.io') [![Join the chat at https://gitter.im/kevinchabreck/paintchat](https://badges.gitter.im/kevinchabreck/paintchat.svg)](https://gitter.im/kevinchabreck/paintchat?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

A distributed chatroom with support for real-time collaborative drawing. Think MS Paint meets Google Docs!

[Live demo](http://draw.ws)

## Features

-  run as a single instance or as multiple clustered nodes
-  consistent, persistant state
-  dynamically add/remove nodes at any time

![paintchat screenshot](paintchat.png)

## Getting started

Download and install **[Docker for Mac or Windows](https://www.docker.com/products/overview)**.

### Run a local PaintChat instance

Run in this directory:

	$ docker-compose up

This will create 4 containers on your machine:
* __paintchat__: the master paintchat server node
* __paintchat-replicant__: a replicant paintchat server node
* __nginx__: an nginx load balancer
* __cassandra__: a cassandra db instance for data persistence

The `paintchat` and `paintchat-replicant` containers are clustered together.

<!-- You can add more replicant nodes to the cluster using the `docker-compose scale` command:

  $ docker-compose scale paintchat-replicant=2

This will increase the total number of nodes in the cluster to 3 (one instance of `paintchat`, and two instances of `paintchat-replicant`) -->

### Connect to the PaintChat cluster

Point your browser to [http://localhost:8080](http://localhost:8080)

_tip: open in multiple tabs for realtime collaborative drawing_