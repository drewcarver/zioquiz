import java.util.Date
import zio.json._

final case class Answer(answerId: Long, value: String)
final case class Player(playerId: String, points: Int)
final case class GameCreated(gameId: Long, timestamp: Long)
final case class QuestionAsked(
    questionId: Long,
    value: String,
    answers: Array[Answer]
)
final case class AnswerSubmitted(questionId: Long, answerId: Long)
final case class LeaderboardUpdated(players: Array[Player])

object GameCreated:
  implicit val decoder: JsonDecoder[GameCreated] =
    DeriveJsonDecoder.gen[GameCreated]
  implicit val encoder: JsonEncoder[GameCreated] =
    DeriveJsonEncoder.gen[GameCreated]

