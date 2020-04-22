package eu.swdev.zioplayground

import doobie.free.connection.ConnectionIO
import doobie.implicits._
import zio._
import zio.blocking.Blocking
import zio.console.Console

/**
  * Sample application that uses a Doobie transactor to access a database
  */
object AccountProg extends zio.App {

  case class Configuration(
      dataSource: DataSource.Config
  )

  val configurationLayer = ZLayer.succeed(Configuration(DataSource.H2Config("jdbc:h2:mem:", "", "")))

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {

    val prog: ZIO[Account.HasAccountService with Console, Throwable, Unit] = for {

      accountService <- Account.getAccountService
      _              <- accountService.createTable
      _              <- accountService.create(0, 100)
      _              <- accountService.create(1, 50)
      t1             <- accountService.transfer(0, 1, 25)
      t2             <- accountService.transfer(0, 1, 100)
      b0             <- accountService.balance(0)
      b1             <- accountService.balance(1)
      _              <- console.putStrLn(s"t1: $t1")
      _              <- console.putStrLn(s"t1: $t2")
      _              <- console.putStrLn(s"b0: $b0")
      _              <- console.putStrLn(s"b1: $b1")
      accounts       <- accountService.accounts
      _              <- console.putStrLn(s"accounts: $accounts")
    } yield {
      ()
    }

    val dataSourceLayer = configurationLayer.map(h => Has(h.get.dataSource)) >>> DataSource.layer
    val transactorLayer = dataSourceLayer ++ ZLayer.requires[Blocking] >>> Transactor.layer
    val accountLayer    = transactorLayer >>> Account.layer

    prog
      .provideCustomLayer(accountLayer)
      .as(0)
      .catchAll {
        case t =>
          t.printStackTrace()
          ZIO.succeed(1)
      }

  }

}
