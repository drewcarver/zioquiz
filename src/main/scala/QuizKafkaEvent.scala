import zio.json._
import scala.collection.mutable.HashMap

package QuizKafkaEvent:
  sealed trait QuizKafkaEvent extends Serializable
  case class QuestionAnswered(questionid: String, playerId: String, answer: String) extends QuizKafkaEvent
  case class QuestionAsked(gameId: Int, questionid: String, question: String) extends QuizKafkaEvent
  case class CreateGame(id: Int) extends QuizKafkaEvent
  case class ScoreUpdated(playerScores: HashMap[String, Int]) extends QuizKafkaEvent

  object QuizKafkaEvent:
    implicit val decoder: JsonDecoder[QuizKafkaEvent] = DeriveJsonDecoder.gen[QuizKafkaEvent]
    implicit val encoder: JsonEncoder[QuizKafkaEvent] = DeriveJsonEncoder.gen[QuizKafkaEvent]
