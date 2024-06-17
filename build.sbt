ThisBuild / scalaVersion := "2.13.12"
ThisBuild / version      := "0.1.0"
ThisBuild / organization := "com.github.kasirgalabs"

ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

val chiselVersion = "6.2.0"

lazy val root = (project in file("."))
  .settings(
    name := "wood",
    libraryDependencies ++= Seq(
      "org.chipsalliance" %% "chisel" % chiselVersion,
      "org.scalatest" %% "scalatest" % "3.2.16" % "test",
      "edu.berkeley.cs" %% "chiseltest" % "6.0.0"
    ),
    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit",
      "-Ymacro-annotations",
      "-Wunused:imports",
      "-Yrangepos",
      "-Xfatal-warnings"
    ),
    addCompilerPlugin(("org.scalameta" % "semanticdb-scalac" % "4.9.5").cross(CrossVersion.full)),
    addCompilerPlugin(("org.chipsalliance" % "chisel-plugin" % chiselVersion).cross(CrossVersion.full))
  )
