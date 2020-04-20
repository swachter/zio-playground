name := "zio-playground"

version := "0.1"

scalaVersion := "2.13.1"

val zioConfigVersion = "1.0.0-RC15"
val doobieVersion    = "0.9.0"

libraryDependencies += "dev.zio" %% "zio" % "1.0.0-RC18-2"

libraryDependencies += "dev.zio" %% "zio-config"          % zioConfigVersion
libraryDependencies += "dev.zio" %% "zio-config-magnolia" % zioConfigVersion
libraryDependencies += "dev.zio" %% "zio-config-refined"  % zioConfigVersion
libraryDependencies += "dev.zio" %% "zio-config-typesafe" % zioConfigVersion

libraryDependencies += "org.tpolecat" %% "doobie-core" % doobieVersion
libraryDependencies += "org.tpolecat" %% "doobie-h2"   % doobieVersion

libraryDependencies += "dev.zio" %% "zio-interop-cats" % "2.0.0.0-RC12"
