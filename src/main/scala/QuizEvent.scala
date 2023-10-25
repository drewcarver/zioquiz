import zio.json._

sealed trait QuizEvent extends Serializable
case class QuestionAnswered(questionid: String, answer: String)
    extends QuizEvent
case class QuestionAsked(questionid: String, question: String) extends QuizEvent

object QuizEvent:
  implicit val decoder: JsonDecoder[QuizEvent] =
    DeriveJsonDecoder.gen[QuizEvent]
  implicit val encoder: JsonEncoder[QuizEvent] =
    DeriveJsonEncoder.gen[QuizEvent]
