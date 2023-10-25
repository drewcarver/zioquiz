import zio._
import zio.stream._
import zio.kafka.producer._
import zio.kafka.serde._
import zio.json._

object QuizRunner:
  val producer =
    Random.nextInt
      .flatMap(questionId =>
        Producer.produce(
          QUESTION_ASKED_TOPIC,
          questionId.toString(),
          QuestionAsked(
            questionId.toString(),
            "What is the capital of Assyria?"
          ).asInstanceOf[QuizEvent].toJson,
          Serde.string,
          Serde.string
        )
      )
      .schedule(Schedule.fixed(5.seconds))
      .tap(_ => Console.printLine("Message sent"))
