package eu.swdev.zioplayground

import doobie.free.connection.ConnectionIO
import javax.sql.DataSource
import zio.blocking.Blocking
import zio.internal.Platform
import zio.{Has, Task, ZIO, ZLayer}

import scala.concurrent.ExecutionContext

/**
 * A module that provides a Doobie transactor based on a javax.sql.DataSource
 */
object Transactor {

  import doobie.{Transactor => DoobieTransactor}

  trait Service {

    def transact[A](ma: ConnectionIO[A]): Task[A]

  }

  type Transactor = Has[Service]

  private class ServiceImpl(doobieTransactor: DoobieTransactor[Task]) extends Service {
    import zio.interop.catz._
    override def transact[A](ma: ConnectionIO[A]): Task[A] = doobieTransactor.trans.apply(ma)
  }

  def dataSourceTransactor(
      dataSource: DataSource,
      connectEC: ExecutionContext,
      blockingEC: ExecutionContext
  ): Service = {
    val blocker = cats.effect.Blocker.liftExecutionContext(blockingEC)
    import zio.interop.catz._
    new ServiceImpl(DoobieTransactor.fromDataSource[Task](dataSource, connectEC, blocker))
  }

  val layer: ZLayer[Has[DataSource] with Blocking, Nothing, Has[Service]] =
    // TODO: Unfortunately the type annotations are required to guide type derivation; is this really required?
    ZLayer.fromServices[DataSource, Blocking.Service, Service] { (dataSource: DataSource, blockingService: Blocking.Service) =>
      // TODO: is it correct to use the Platform default executor here?
      dataSourceTransactor(dataSource, Platform.default.executor.asEC, blockingService.blockingExecutor.asEC)
    }

  val getTransactor = ZIO.access[Transactor](_.get)

}
