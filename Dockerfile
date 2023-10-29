FROM docker.io/sbtscala/scala-sbt:eclipse-temurin-jammy-8u352-b08_1.8.2_2.12.17

WORKDIR /app

COPY . /app

RUN sbt compile

EXPOSE 8090

CMD ["sbt", "run"]
