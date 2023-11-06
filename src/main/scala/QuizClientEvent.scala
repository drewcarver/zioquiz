import zio.json._
import scala.collection.mutable.HashMap

sealed trait QuizEvent extends Serializable
case class QuestionAnswered(
    questionid: String,
    playerId: String,
    answer: String
) extends QuizEvent
case class QuestionAsked(questionid: String, question: String) extends QuizEvent
case class CreateGame() extends QuizEvent
case class ScoreUpdated(playerScores: HashMap[String, Int])

object QuizEvent:
  implicit val decoder: JsonDecoder[QuizEvent] =
    DeriveJsonDecoder.gen[QuizEvent]
  implicit val encoder: JsonEncoder[QuizEvent] =
    DeriveJsonEncoder.gen[QuizEvent]
