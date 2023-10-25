import zio._
import zio.http._
import zio.stream._
import common._

object Main extends ZIOAppDefault:
  override def run =
    ZStream
      .fromZIO(
        Server
          .serve(QuizClient.app)
          .provide(
            QuizKafkaProducer.layer,
            Server.defaultWithPort(8090)
          )
      )
      .merge(
        QuizClient.sendQuestionToClient.provideLayer(QuizKafkaConsumer.layer)
      )
      .runDrain
