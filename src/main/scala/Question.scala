import zio.json._

case class Question(text: String)

implicit val decoder: JsonDecoder[Question] =
  DeriveJsonDecoder.gen[Question]
implicit val encoder: JsonEncoder[Question] =
  DeriveJsonEncoder.gen[Question]
