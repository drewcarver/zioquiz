import zio._
import zio.kafka.consumer._
import zio.kafka.serde._
import zio.json._
import scala.collection.mutable.HashMap
import zio.kafka.producer.Producer

object ScoreKeeper:
  val scores = new HashMap[String, Int]

  private def gradeScore(questionAnsweredEvent: QuestionAnswered) =
    val rightAnswer = "yellow"

    if (!scores.contains(questionAnsweredEvent.playerId))
      scores += (questionAnsweredEvent.playerId -> 0)

    if (questionAnsweredEvent.answer == rightAnswer)
      scores(questionAnsweredEvent.playerId) += 1

  val run =
    Consumer
      .plainStream(
        Subscription.topics(QUESTION_ANSWERED_TOPIC),
        Serde.string,
        Serde.string
      )
      .tap(message =>
        for
          event <- ZIO.fromEither(message.value.fromJson[QuizEvent])
          _ <- event match
            case questionAnswered: QuestionAnswered =>
              gradeScore(questionAnswered)
              Producer.produce(
                SCORE_UPDATED_TOPIC,
                "1",
                scores.map({
                  case (key, value) => Score(key, value)
                }).toJson,
                Serde.string,
                Serde.string
                )
        yield ()
      )
      .map(_.offset)
      .aggregateAsync(Consumer.offsetBatches)
      .mapZIO(_.commit)
      .drain
