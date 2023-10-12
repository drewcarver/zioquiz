ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.2.2"

lazy val root = (project in file("."))
  .settings(
    name := "ZioQuiz",
    resolvers += "Maven" at "https://packages.confluent.io/maven/",
    libraryDependencies += "dev.zio" %% "zio-http" % "3.0.0-RC2",
    libraryDependencies += "dev.zio" %% "zio-schema-protobuf" % "0.4.9",
    libraryDependencies += "dev.zio" %% "zio-redis" % "0.2.0"
  )
