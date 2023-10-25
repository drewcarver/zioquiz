import sbt._

ThisBuild / scalaVersion := "3.3.1"

val dependencies = Seq(
  "dev.zio" %% "zio-http" % "3.0.0-RC2",
  "dev.zio" %% "zio-kafka" % "2.1.3",
  "dev.zio" %% "zio-schema-protobuf" % "0.4.9",
  "dev.zio" %% "zio-redis" % "0.2.0",
  "dev.optics" %% "monocle-core" % "3.2.0"
)

lazy val common = project.settings(libraryDependencies ++= dependencies)

lazy val quizclient =
  project.dependsOn(common).settings(libraryDependencies ++= dependencies)
lazy val quizrunner =
  project.dependsOn(common).settings(libraryDependencies ++= dependencies)

lazy val runAllProjects = taskKey[Unit]("Run all projects")

runAllProjects := Def
  .sequential(
    (quizclient / Compile / run).toTask(""),
    (quizrunner / Compile / run).toTask("")
  )
  .value

// Define an alias for the aggregated run task
addCommandAlias("runAll", ";quizclient/run ;quizrunner/run")
