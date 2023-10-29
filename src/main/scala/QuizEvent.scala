import zio.json._
import scala.collection.mutable.HashMap

case class Score(playerName: String, score: Int)
object Score:
  implicit val decoder: JsonDecoder[Score] = DeriveJsonDecoder.gen[Score]
  implicit val encoder: JsonEncoder[Score] = DeriveJsonEncoder.gen[Score]

sealed trait QuizEvent extends Serializable
case class QuestionAnswered(questionId: String, playerId: String, answer: String) extends QuizEvent
case class QuestionAsked(questionId: String, question: String) extends QuizEvent
case class ScoreUpdated(scores: List[Score]) extends QuizEvent

object QuizEvent:
  implicit val decoder: JsonDecoder[QuizEvent] = DeriveJsonDecoder.gen[QuizEvent]
  implicit val encoder: JsonEncoder[QuizEvent] = DeriveJsonEncoder.gen[QuizEvent]
