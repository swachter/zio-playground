package eu.swdev.zioplayground
import java.util.concurrent.TimeUnit

import zio.duration.Duration
import zio.random.Random
import zio.{Ref, Schedule, UIO, URIO, ZIO, console, random}

// estimate pi; inspired by an idea from John A De Goes
object Pi extends zio.App {

  case class State(count: Long = 0, hits: Long = 0) {
    def hit(): State = State(count + 1, hits + 1)
    def missed(): State = State(count + 1, hits)
  }

  val dice = for {
    x <- random.nextDouble
    y <- random.nextDouble
  } yield {
    x*x + y*y > 1
  }

  def dices(cnt: Int, hits: Int): URIO[Random, State] = if (cnt == 10000) {
    ZIO.succeed(State(cnt, hits))
  } else {
    for {
      b <- dice
      s <- dices(cnt + 1, if (b) hits else hits + 1)
    } yield {
      s
    }
  }

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = for {
    state <- Ref.make(State())

    simulate = for {
      s <- dices(0, 0)
      _ <- state.update(x => State(x.count + s.count, x.hits + s.hits))
    } yield ()

    report = for {
      s <- state.get
      _ <- console.putStrLn(s"count: ${s.count}; pi: ${4.0 * s.hits / s.count}")
    } yield ()

    fibs <- ZIO.collectAll((0 to 6).map(_ => simulate.forever.fork))

    _ <- report.repeat(Schedule.recurs(3) && Schedule.spaced(Duration(10, TimeUnit.SECONDS)))

  } yield {
    0
  }

}
