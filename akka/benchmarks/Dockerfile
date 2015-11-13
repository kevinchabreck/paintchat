FROM 1science/sbt:0.13.8-oracle-jre-8
MAINTAINER John McCrone

# Builds an image containing Paintchat Benchmarks

# get required sbt version
COPY project/build.properties project/build.properties
COPY project/assembly.sbt project/assembly.sbt
RUN sbt update

# build compiler-interface for scala 11.7
RUN echo 'scalaVersion := "2.11.7"' > build.sbt && \
    mkdir -p src/main/scala && \
    touch src/main/scala/tmp.scala && \
    sbt compile && \
    rm -rf src build.sbt

# download dependencies
COPY build.sbt build.sbt
RUN sbt compile

# build project once without config file
COPY src/main/scala/ src/main/scala/
RUN sbt compile

# rebuild with config
COPY src/main/resources/application.conf src/main/resources/application.conf
RUN sbt compile
