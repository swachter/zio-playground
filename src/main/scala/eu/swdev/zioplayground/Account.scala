package eu.swdev.zioplayground

import doobie.free.connection.ConnectionIO
import zio.{Has, Task, ZIO, ZLayer}
import doobie.implicits._
import doobie.quill.{DoobieContext, DoobieContextBase}
import io.getquill.Literal

object Account {

  trait Service {

    def createTable: Task[Int]

    def create(id: Int, balance: Int): Task[Int]

    def balance(id: Int): Task[Int]
    def updateBalance(id: Int, amount: Int): Task[Int]

    def transfer(from: Int, to: Int, amount: Int): Task[Boolean]

    def accounts: Task[List[Account]]
  }

  type HasAccountService = Has[Service]

  case class Account(id: Int, balance: Int)

  private class ServiceImpl(transactor: Transactor.Service) extends Service {

    val dc = new DoobieContext.H2(Literal)
    import dc._

    override def createTable: Task[Int] = transactor transact sql"create table account (id INT, balance INT)".update.run

    override def create(id: Int, balance: Int): Task[Int] =
      transactor.transact(sql"insert into account (id, balance) values ($id, $balance)".update.run)

    override def balance(id: Int): Task[Int] = transactor transact balanceCio(id)

    override def updateBalance(id: Int, amount: Int): Task[Int] = transactor transact updateBalanceCio(id, amount)

    override def transfer(from: Int, to: Int, amount: Int): Task[Boolean] = transactor transact {
      import cats.implicits._
      for {
        fromBalance <- balanceCio(from)
        done <- if (fromBalance >= amount) {
                 for {
                   toBalance <- balanceCio(to)
                   _         <- updateBalanceCio(from, fromBalance - amount)
                   _         <- updateBalanceCio(to, toBalance + amount)
                 } yield true
               } else {
                 false.pure[ConnectionIO]
               }
      } yield done
    }

    override val accounts: Task[List[Account]] = {
      val quoted = quote { query[Account] }
      val connectionIo = run(quoted) // compile time evaluation
      transactor transact connectionIo
    }

    private def balanceCio(id: Int): ConnectionIO[Int] = sql"select balance from account where id = $id".query[Int].unique
    private def updateBalanceCio(id: Int, amount: Int): ConnectionIO[Int] =
      sql"update account set balance = $amount where id = $id".update.run

  }

  val layer = ZLayer.fromService((transactor: Transactor.Service) => new ServiceImpl(transactor).asInstanceOf[Service])

  val getAccountService = ZIO.access[HasAccountService](_.get)

}
