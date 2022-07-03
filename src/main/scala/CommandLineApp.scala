import zio._

object CommandLineApp extends zio.ZIOAppDefault {
   def run = for {
     number <- zio.Console.readLine("Enter a number:")
     numberParsed <- ZIO.attempt(Integer.parseInt(number))
     numberMultiplied = numberParsed * 10
     _ <- zio.Console.printLine(numberMultiplied)
   } yield()
}
