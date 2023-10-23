import zio._
import zio.kafka.producer._
import zio.kafka.serde._
import zio.json._

object QuizOrchestrator:
  val askQuestion =
    Producer
      .produce[Any, String, String](
        topic = "question-asked",
        key = "1",
        value = Question("What is the capital of Assyria?").toJson,
        keySerializer = Serde.string,
        valueSerializer = Serde.string
      )
      .catchAll(e => Console.printLineError(e.getMessage()))
      .ignore
