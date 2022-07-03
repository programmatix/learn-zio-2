
import zio.*

import java.io.{File, FileNotFoundException}
import scala.concurrent.Future
import scala.util.Try

object Main {
  def main(args: Array[String]): Unit = {
  }

  // How to model the messy reality of the world (flaky networks, database calls, etc.) functionally?
  def functionalEffects(): Unit = {
    // Generally try to model what we plan to do functionality, and then execute it impurely.

    // Can represent a CLI app like this:
    sealed trait Console[+A]
    final case class Return[A](value: () => A) extends Console[A]
    final case class PrintLine[A](line: String, rest: Console[A]) extends Console[A]
    final case class ReadLine[A](rest: String => Console[A]) extends Console[A]

    val example1: Console[Unit] =
      PrintLine("Hello, what is your name?",
        ReadLine(name =>
          PrintLine(s"Good to meet you, ${name}", Return(() => ())))
      )

    // All functional so far, nothing has been run.  Such immutable data structures modelling procedural effects are
    // called functional effects.

    // Then an interpreter to execute:
    def interpret[A](program: Console[A]): A = program match {
      case Return(value) =>
        value()
      case PrintLine(line, next) =>
        println(line)
        interpret(next)
      case ReadLine(next) =>
        interpret(next(scala.io.StdIn.readLine()))
    }

    // ZIO is all about modelling (and executing) functional effects
  }

  // Easy to create simple succeed/fail
  def just(): Unit = {
    val s1 = ZIO.succeed(42)
    val f1 = ZIO.fail("uh oh")
  }

  // The main type is ZIO[R,E,A] - kind of a supercharged Either that's also an IO monad
  def types(): Unit = {
    // A ZIO is parameterised on
    // R: Where it can run
    // E: What it returns on failure
    // A: What it returns on success
    val s1: ZIO[Any, Nothing, Int] = ZIO.succeed(42)
    val f1: ZIO[Any, Throwable, Unit] = ZIO.fail(new Exception())

    // There are also specialised types like Task, IO, UIO that provide defaults for some of those 3
    // UIO[A]      = ZIO[Any, Nothing, A]    (cannot fail)
    // Task[A]     = ZIO[Any, Throwable, A]  (close to a Try or Future)
    // IO[E, A]    = ZIO[Any, E, A]
  }

  // Can easily convert to/from Option, Either, Try, Future etc. A ZIO is a super-charged version of all of these.
  def convert(): Unit = {
    val o1 = ZIO.fromOption(Some(2))
    val t1 = ZIO.fromTry(Try(42 / 0))
    val f1: Task[String] = ZIO.fromFuture(Future { "42" })
  }

  // Handling methods that can fail
  def sideEffects(): Unit = {
    val readLine: Task[String] = ZIO.attempt(scala.io.StdIn.readLine())

    // Blocking operations will starve the underlying thread pool by default, unless use blocking.  This gets executed
    // on separate threadpool explicitly for blocking effects.
    val sleeping = ZIO.attemptBlocking(Thread.sleep(1000))
  }

  // Can map as you'd expect
  def operations(): Unit = {
    ZIO.succeed(42).map(_ * 2)
    val e1: ZIO[Any, Exception, Unit] = ZIO.fail("uh oh").mapError(msg => new Exception(msg))
  }

  // Can for-comprehension and flatMap as you'd expect
  def chaining(): Unit = {
    val z1: ZIO[Any, Throwable, String] = ZIO.attempt(scala.io.StdIn.readLine())
      .flatMap(line => ZIO.attempt(println(line))
        .map(_ => line))

    val z2: ZIO[Any, Throwable, String] = for {
      line <- ZIO.attempt(scala.io.StdIn.readLine())
      out <- ZIO.attempt(println(line))
    } yield line
  }

  def combining(): Unit = {
    // Combine two ZIOs into a tuple
    val z1: ZIO[Any, Nothing, (Int, String)] = ZIO.succeed(42).zip(ZIO.succeed("hello"))
  }

  def openFile(filename: String): ZIO[Any, Throwable, File] = ???

  def errorHandling(): Unit = {
    // Recover from any error
    openFile("primary.json")
      .catchAll(_ => openFile("backup.json"))
    openFile("primary.json")
      .orElse(openFile("backup.json"))

    // Recover from some errors
    openFile("primary.json").catchSome {
      case _ : FileNotFoundException => openFile("backup.json")
    }

    // fold and foldZIO are syntactic sugar for handling success and failure in one place
  }

  def retrying(): Unit = {
    openFile("primary").retry(Schedule.recurs(5))
  }

  // try-finally
  def finalising(): Unit = {
    // A finalizer must succeed so UIO is a good match
    // Failing to compile, unclear why
    // ZIO.fail("argh").ensuring(UIO.succeed(println("finalizing")))
  }

  def raii(): Unit = {
    // acquireRelease allows:
    // acquire effect (opening a file)
    // use effect     (doing something with it)
    // release effect (closing the file)

    // Couldn't get the example to compile...
  }

  def fib(n: Long): ZIO[Any, Nothing, Long] = {
    ZIO.succeed {
      if (n < 1) ZIO.succeed(n)
      else fib(n - 1).zipWith(fib(n - 2))(_ + _)
    }.flatten
  }

  // Like Cats Effect, ZIO has fibers.  Extremely lightweight threads that are scheduled by the ZIO runtime atop a
  // thread pool.  ZIO recommends not using fibers directly.
  // All effects are executed by fibers.
  def fibres(): Unit = {
    // Fibre[E, A] - can fail with E, can succeed with A
    val fiber = fib(100).fork

    val msg: ZIO[Any, Nothing, String] = for {
      fiber <- ZIO.succeed("hi!").fork
      message <- fiber.join
    } yield message

    // Fibres can be safely interrupted (finalizers are run) and composed
  }

  def parallelism(): Unit = {
    for {
      f1 <- ZIO.succeed("Hi!").fork
      f2 <- ZIO.succeed("Bye!").fork
      // Docs say there's a zipPar...
      f3 = f1.zip(f2)
      f4 <- f3.join
    } yield f4

    // If one parallel op fails, the others do too (similar to Reactive)

    val winner = ZIO.succeed("hello").race(ZIO.succeed("bye"))

    ZIO.succeed("hello").timeout(10.seconds)
  }

  def testing(): Unit = {
    // A Zio's first param is the environment, which can be anything - here using an Int
    val x: ZIO[Int, Throwable, _] = for {
      env <- ZIO.environment[Int]
      _ <- zio.Console.printLine(s"The value of the environment is: $env")
    } yield env

    // To run it we need to provide that environment
    val result = x.provideEnvironment(ZEnvironment(42))

    // Environments can have methods that return effects.  The methods themselves must be side-effect free.

    type UserID = String
    case class UserProfile()

    object Database {
      trait Service {
        def lookup(id: UserID): Task[UserProfile]
        def update(id: UserID, profile: UserProfile): Task[Unit]
      }
    }
    trait Database {
      def database: Database.Service
    }

    // How to use an effect from an environment
    val fetched: RIO[Database, UserProfile] = ZIO.serviceWithZIO[Database](_.database.lookup("id"))

    // A common pattern is to make the environment (Database) an interface, and have test and real versions of it
  }

  // If the whole app is going to be ZIO, can do this:
  def runningEffects(): Unit = {
    import zio._
    import zio.Console._

    object MyApp extends zio.ZIOAppDefault {

      def run =
        for {
          _    <- printLine("Hello! What is your name?")
          name <- readLine
          _    <- printLine(s"Hello, ${name}, welcome to ZIO!")
        } yield ()
    }
  }

  // Otherwise if integrating ZIO into existing app can do
  def running(): Unit = {
    val runtime = Runtime.default

    val x: IO[Throwable, Unit] = runtime.run(ZIO.attempt(println("Hello World!")))
  }


}