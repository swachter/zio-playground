package eu.swdev.zioplayground

import java.util.concurrent.TimeUnit

import zio.clock.Clock
import zio.console.Console
import zio.duration.Duration
import zio.random.Random
import zio.stm.{STM, TRef}
import zio.{RIO, ZIO, console}

// cf. "Beautiful concurrency"; Simon Peyton Jones
object SantaClaus extends zio.App {

  type USTM[A] = STM[Nothing, A]

  type CRIO[A] = RIO[Console with Random with Clock, A]

  case class Gate(capacity: Int, nbStillAwaitedHelpers: TRef[Int])

  case class GroupEvent(emptySlots: Int, inGate: Gate, outGate: Gate)

  case class Group(capacity: Int, groupEvent: TRef[GroupEvent])

  def newGate(capacity: Int): USTM[Gate] = for {
    tv <- TRef.make(0)
  } yield Gate(capacity, tv)

  def passGate(gate: Gate): CRIO[Unit] = STM.atomically {
    for {
      left <- gate.nbStillAwaitedHelpers.get
      _ <- STM.check(left > 0)
      _ <- gate.nbStillAwaitedHelpers.set(left - 1)
    } yield ()
  }

  def operateGate(gate: Gate): CRIO[Unit] = for {
    _ <- STM.atomically(gate.nbStillAwaitedHelpers.set(gate.capacity))
    _ <- STM.atomically {
      for {
        left <- gate.nbStillAwaitedHelpers.get
        _ <- STM.check(left == 0)
      } yield ()
    }
  } yield ()

  def newGroup(capacity: Int): CRIO[Group] = STM.atomically {
    for {
      inGate <- newGate(capacity)
      outGate <- newGate(capacity)
      groupEvent <- TRef.make(GroupEvent(capacity, inGate, outGate))
    } yield Group(capacity, groupEvent)
  }

  def joinGroup(group: Group): CRIO[(Gate, Gate)] = STM.atomically {
    for {
      groupEvent <- group.groupEvent.get
      _ <- STM.check(groupEvent.emptySlots > 0)
      _ <- group.groupEvent.set(groupEvent.copy(emptySlots = groupEvent.emptySlots - 1))
    } yield (groupEvent.inGate, groupEvent.outGate)
  }

  def awaitGroup(group: Group): USTM[(Gate, Gate)] = for {
    groupEvent <- group.groupEvent.get
      _ <- STM.check(groupEvent.emptySlots == 0)
    newInGate <- newGate(group.capacity)
    newOutGate <- newGate(group.capacity)
    _ <- group.groupEvent.set(GroupEvent(group.capacity, newInGate, newOutGate))
  } yield (groupEvent.inGate, groupEvent.outGate)

  def meetInStudy(id: Int): CRIO[Unit] = console.putStrLn(s"Elf $id meeting in study")

  def deliverToys(id: Int): CRIO[Unit] = console.putStrLn(s"Reeindeer $id delivering toys")

  def helper1(group: Group, io: CRIO[Unit]) = for {
    (inGate, outGate) <- joinGroup(group)
    _ <- passGate(inGate)
    _ <- io
    _ <- passGate(outGate)
  } yield ()

  def elf1(group: Group, id: Int): CRIO[Unit] = helper1(group, meetInStudy(id))

  def reindeer1(group: Group, id: Int): CRIO[Unit] = helper1(group, deliverToys(id))

  val randomDelay: CRIO[Unit] = for {
    millis <- zio.random.nextInt(1000)
    _ <- zio.clock.sleep(Duration(millis, TimeUnit.MILLISECONDS))
  } yield ()

  def helper(io: CRIO[Unit]) =
    (io *> randomDelay).forever.fork

  def elf(group: Group, id: Int) = helper(elf1(group, id))

  def reindeer(group: Group, id: Int) = helper(reindeer1(group, id))

  def chooseGroup(group: Group, task: String): STM[Throwable, (String, (Gate, Gate))] = for {
    gates <- awaitGroup(group)
  } yield (task, gates)

  def santa(elfGroup: Group, reinGroup: Group): CRIO[Unit] = for {
    _ <- console.putStrLn("-----------------")
    (task, (inGate, outGate)) <- STM.atomically {
      chooseGroup(reinGroup, "deliver toys").orElse(chooseGroup(elfGroup, "meet in my study"))
    }
    _ <- console.putStrLn(s"Ho! Ho! Ho! let's $task")
    _ <- operateGate(inGate)
    _ <- operateGate(outGate)
  } yield ()

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = (for {
    elfGroup <- newGroup(3)
    _ <- ZIO.collectAll((1 to 10).map(elf(elfGroup, _)))
    reinGroup <- newGroup(9)
    _ <- ZIO.collectAll((1 to 10).map(reindeer(reinGroup, _)))
    _ <- santa(elfGroup, reinGroup).forever
  } yield 0).catchAll {
    case t: Throwable =>
      t.printStackTrace()
      ZIO.succeed(1)
  }

}
