import zio._
import zio.stream._
import zio.kafka.producer._
import zio.kafka.serde._
import zio.json._
import zio.kafka.consumer.Consumer
import zio.kafka.consumer.Subscription

object QuizRunner:
  def producer(gameId: Int) =
    Random.nextInt
      .flatMap(questionId =>
        Producer.produce(
          QUESTION_ASKED_TOPIC,
          questionId.toString(),
          QuizKafkaEvent.QuestionAsked(
            gameId,
            questionId.toString(),
            "What is the capital of Assyria?"
          ).asInstanceOf[QuizEvent].toJson,
          Serde.string,
          Serde.string
        )
      )
      .schedule(Schedule.fixed(5.seconds))

  val gameListener =
    Consumer
      .plainStream(
        Subscription.topics(CREATE_GAME_TOPIC),
        Serde.string,
        Serde.string
      )
      .tap(
        _.value.fromJson[QuizKafkaEvent.QuizKafkaEvent] match 
          case CreateGame() => 
            for 
              gameId <- Random.nextIntBetween(0, Int.MaxValue)
              _ <- producer(gameId)
            yield ()
      )
      .map(_.offset)
      .aggregateAsync(Consumer.offsetBatches)
      .mapZIO(_.commit)
      .drain
