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

val JOIN_GAME_EVENT_PREFIX = "join-game:"

object MainApp extends ZIOAppDefault:

  object ProtobufCodecSupplier extends CodecSupplier {
    def get[A: Schema]: BinaryCodec[A] = ProtobufCodec.protobufCodec
  }

  override def run =
    ZStream.fromZIO(Server.serve(QuizClient.app))
      .runDrain
      .provide(
        QuizKafkaProducer.layer,
        GameRepoImpl.layer,
        ZLayer.succeed[CodecSupplier](ProtobufCodecSupplier),
        Server.defaultWithPort(8090)
      )
