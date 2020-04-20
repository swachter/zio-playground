package eu.swdev.zioplayground

import doobie.free.connection.ConnectionIO
import zio.blocking.Blocking
import zio.internal.Platform
import zio._

import scala.concurrent.ExecutionContext
import doobie.implicits._
import zio.console.Console

/**
 * Sample application that uses Doobie
 *
 * A Doobie transactor is provided by a layer.
 */
object RelDb extends zio.App {

  case class DbConfig(url: String, user: String, password: String)

  val configurationLayer = ZLayer.succeed(DbConfig("jdbc:h2:~/test;DB_CLOSE_DELAY=-1", "", ""))

  type DbConfiguration = Has[DbConfig]

  object Transactor {

    import doobie.{Transactor => DoobieTransactor}

    trait Service {

      def transact[A](ma: ConnectionIO[A]): Task[A]

    }

    class Live(doobieTransactor: DoobieTransactor[Task]) extends Service {
      import zio.interop.catz._
      override def transact[A](ma: ConnectionIO[A]): Task[A] = doobieTransactor.trans.apply(ma)
    }

    def h2Transactor(
        conf: DbConfig,
        connectEC: ExecutionContext,
        blockingEC: ExecutionContext
    ): Managed[Throwable, Service] = {
      import org.h2.jdbcx.JdbcConnectionPool
      val blocker = cats.effect.Blocker.liftExecutionContext(blockingEC)
      import zio.interop.catz._
      Managed
        .makeEffect(JdbcConnectionPool.create(conf.url, conf.user, conf.password))(_.dispose())
        .map(cp => new Live(DoobieTransactor.fromDataSource[Task](cp, connectEC, blocker)))
    }

    val getTransactor = ZIO.access[Transactor](_.get)

  }

  type Transactor = Has[Transactor.Service]

  // provide a Transactor.Service base on the database configuration and Blocking.Service
  // TODO: Unfortunately the type annotations are required to guide type derivation; is this really required?
  val h2TransactorLayer =
    ZLayer.fromServicesManaged[DbConfig, Blocking.Service, Any, Throwable, Transactor.Service] {
      (config: DbConfig, blockingService: Blocking.Service) =>
        // TODO: is it correct to use the Platform default executor here?
        Transactor.h2Transactor(config, Platform.default.executor.asEC, blockingService.blockingExecutor.asEC)
    }

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

    val prog: ZIO[Transactor with Console, Throwable, Unit] = for {
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

    // TODO: Is this the correct way to wire the necessary layers
    val customLayer = (ZLayer.requires[Blocking] ++ configurationLayer) >>> h2TransactorLayer

    prog
      .provideCustomLayer(customLayer)
      .as(0)
      .catchAll {
        case t =>
          t.printStackTrace()
          ZIO.succeed(1)
      }

  }

}
