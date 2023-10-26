import zio._
import zio.kafka.consumer._
import zio.kafka.serde._
import zio.json._
import scala.collection.mutable.HashMap

object ScoreKeeper:
  val scores = new HashMap[String, Int]

  private def gradeScore(questionAnsweredEvent: QuestionAnswered) =
    val rightAnswer = "yellow"

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
        ZIO.fromEither(
          message.value
            .fromJson[QuizEvent]
            .map({ case questionAnswered: QuestionAnswered =>
              gradeScore(questionAnswered)
            })
        )
      )
      .map(_.offset)
      .aggregateAsync(Consumer.offsetBatches)
      .mapZIO(_.commit)
      .drain
