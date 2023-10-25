import zio._
import zio.http._
import zio.redis._
import zio.schema._
import zio.schema.codec._
import zio.stream.ZStream
import zio.json._
import zio.http.ChannelEvent.Read
import java.util.Calendar
import zio.http.Channel
import zio.http.ChannelEvent.UserEvent
import zio.http.ChannelEvent.UserEventTriggered
import scala.collection.mutable.HashMap
import zio.http.Header.Authorization
import zio.kafka.consumer._
import zio.kafka.producer._
import zio.kafka.serde._

object MainApp extends ZIOAppDefault:
  override def run =
    for
      webSocketClient <- Server
        .serve(QuizClient.app)
        .provide(
          QuizKafkaProducer.layer,
          Server.defaultWithPort(8090)
        )
        .fork
      quizRunner <- QuizRunner.producer.provide(QuizKafkaProducer.layer).fork

      _ <- ZStream
        .fromZIO((webSocketClient <*> quizRunner).join)
        .merge(
          QuizClient.sendQuestionToClient.provideLayer(QuizKafkaConsumer.layer)
        )
        .runDrain
    yield ()
