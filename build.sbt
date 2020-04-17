name := "zio-playground"

version := "0.1"

scalaVersion := "2.13.1"

val zioConfigVersion = "1.0.0-RC15"

libraryDependencies += "dev.zio" %% "zio" % "1.0.0-RC18-2"

libraryDependencies += "dev.zio" %% "zio-config" % zioConfigVersion
libraryDependencies += "dev.zio" %% "zio-config-magnolia" % zioConfigVersion
libraryDependencies += "dev.zio" %% "zio-config-refined" % zioConfigVersion
libraryDependencies += "dev.zio" %% "zio-config-typesafe" % zioConfigVersion