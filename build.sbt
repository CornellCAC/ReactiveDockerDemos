name := "ReactiveDockerDemos"

version := "1.0"

scalaVersion := "2.11.8"

resolvers ++= Seq(
  Resolver.mavenLocal,
  Resolver.sonatypeRepo("snapshots"),
  Resolver.typesafeRepo("releases")
)

libraryDependencies += "edu.cornell.cac" %% "reactive-docker" % "0.1-SNAPSHOT"