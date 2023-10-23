import sbt._

val root = project
    .in(file("."))
    .settings(
        name := "zioquiz",
        scalaVersion := "3.3.1",
        libraryDependencies ++= Seq(
            "dev.zio" %% "zio-http" % "3.0.0-RC2",
            "dev.zio" %% "zio-kafka" % "2.1.3",
            "dev.zio" %% "zio-schema-protobuf" % "0.4.9",
            "dev.zio" %% "zio-redis" % "0.2.0",
            "dev.optics" %% "monocle-core" % "3.2.0"
        )
    )
