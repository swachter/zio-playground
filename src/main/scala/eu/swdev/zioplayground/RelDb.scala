package eu.swdev.zioplayground

import doobie.free.connection.ConnectionIO
import doobie.implicits._
import zio._
import zio.blocking.Blocking
import zio.console.Console

/**
  * Sample application that uses a Doobie transactor to access a database
  */
object RelDb extends zio.App {

  case class Configuration(
      dataSource: DataSource.Config
  )

  val configurationLayer = ZLayer.succeed(Configuration(DataSource.H2Config("jdbc:h2:mem:", "", "")))

  // some example database programs from the Doobie documentation https://tpolecat.github.io/doobie/docs/03-Connecting.html

  val program1: ConnectionIO[Int] = {
    import cats.implicits._
    42.pure[ConnectionIO]
  }

  val program2: ConnectionIO[Int] = {
    sql"select 43".query[Int].unique
  }

  val program3: ConnectionIO[(Int, Double)] =
    for {
      a <- sql"select 44".query[Int].unique
      b <- sql"select random()".query[Double].unique
    } yield (a, b)

  val program4: ConnectionIO[List[(Int, Double)]] = {
    import cats.implicits._
    program3.replicateA(5)
  }

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {

    val prog: ZIO[Transactor.Transactor with Console, Throwable, Unit] = for {
      transactor <- Transactor.getTransactor
      value1     <- transactor.transact(program1)
      _          <- console.putStrLn(s"value1: $value1")
      value2     <- transactor.transact(program2)
      _          <- console.putStrLn(s"value2: $value2")
      value3     <- transactor.transact(program3)
      _          <- console.putStrLn(s"value3: $value3")
      value4     <- transactor.transact(program4)
      _          <- console.putStrLn(s"value4: $value4")
    } yield {
      ()
    }

    val dataSourceLayer = configurationLayer.map(h => Has(h.get.dataSource)) >>> DataSource.layer
    val transactorLayer = dataSourceLayer ++ ZLayer.requires[Blocking] >>> Transactor.layer

    prog
      .provideCustomLayer(transactorLayer)
      .as(0)
      .catchAll {
        case t =>
          t.printStackTrace()
          ZIO.succeed(1)
      }

  }

}
