package eu.swdev.zioplayground

import zio.config._
import zio.config.magnolia.DeriveConfigDescriptor._
import zio.{Has, Layer}

case class MyConfig(ldap: String, port: Int, dburl: String)

object TestConfig {

  val myConfig = descriptor[MyConfig]

  val map =
    Map(
      "LDAP" -> "xyz",
      "PORT" -> "8888",
      "DB_URL" -> "postgres"
    )

  val source = ConfigSource.fromMap(map)

  val result: Layer[ReadError[String], Has[MyConfig]] = Config.fromMap(map, myConfig)


  val result2 = read(myConfig from source)
  // Either[ReadError[String], MyConfig]

  val result3: Layer[ReadError[String], zio.config.Config[MyConfig]] = Config.fromSystemEnv(myConfig)

}
