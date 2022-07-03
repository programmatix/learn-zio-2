ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.1.3"

lazy val root = (project in file("."))
  .settings(
    name := "learn-zio-2"
  )

libraryDependencies += "dev.zio" %% "zio" % "2.0.0-RC6"
libraryDependencies += "dev.zio" %% "zio-streams" % "2.0.0-RC6"