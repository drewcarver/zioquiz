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

val JOIN_GAME_EVENT_PREFIX = "join-game:"
case class Question(text: String)
implicit val decoder: JsonDecoder[Question] =
  DeriveJsonDecoder.gen[Question]
implicit val encoder: JsonEncoder[Question] =
  DeriveJsonEncoder.gen[Question]

object MainApp extends ZIOAppDefault:
  val connections = new HashMap[String, WebSocketChannel]()

  object ProtobufCodecSupplier extends CodecSupplier {
    def get[A: Schema]: BinaryCodec[A] = ProtobufCodec.protobufCodec
  }

  val createGame: ZIO[Redis, String, GameCreated] =
    for {
      redis <- ZIO.service[Redis]
      lobbyId <- Random.nextInt

      succeeded <- redis
        .set("game", lobbyId, Some(1.hour))
        .mapError(_ => "Couldn't update redis.")

      time = java.time.Instant.now().toEpochMilli()
      game = GameCreated(8L, time)
    } yield (game)

  def getUserSub(jwt: String): String =
    jwt

  def socketApp(req: Request): SocketApp[GameRepo] =
    Handler.webSocket { channel =>
      channel.receiveAll {
        case UserEventTriggered(UserEvent.HandshakeComplete) =>
          for
            userId <- Random.nextIntBetween(0, Int.MaxValue)
            token <- ZIO
              .fromOption(req.url.queryParams.get("authToken"))
              .mapError(e => new Throwable("Couldn't get auth header."))
            _ <- ZIO.succeed(connections += (userId.toString() -> channel))
            _ <- channel.send(Read(WebSocketFrame.text(token.mkString)))
          yield ()
        case Read(WebSocketFrame.Text(event)) if event.startsWith(JOIN_GAME_EVENT_PREFIX) =>
          val gameId = event.split(":").last
          for 
            gameRepo <- ZIO.service[GameRepo]
            _ <- gameRepo.join(gameId).mapError(e => new Throwable(e))
            _ <- channel.send(Read(WebSocketFrame.text(gameId)))
          yield ()
        case _ =>
          ZIO.unit
      }
    }

  val app: Http[GameRepo, Nothing, Request, Response] =
    Http.collectZIO[Request] {
      case req @ Method.GET -> Root / "subscriptions" =>
        socketApp(req).toResponse
    }

  def sendToAllClients(webSocketFrame: WebSocketFrame) = 
    ZIO.collectAll(
      connections
      .values
      .map(c => c.send(Read(webSocketFrame)))
    )

  def askQuestion = 
    val question = Question("What is your favorite color?")
    for {
     redis <- ZIO.service[Redis]
      _ <- redis.set("currentAnswer", "yellow")
      _ <- sendToAllClients(WebSocketFrame.text(question.toJson))
    } yield ()

  val showAnswer = 
    ZIO.collectAll(
      connections
      .values
      .map(c => {
        c.send(Read(WebSocketFrame.text("answer")))
      })
    )

  val gameApp: ZStream[Redis, Throwable, Nothing] = 
    ZStream.fromZIO(
      for {
        _ <- askQuestion

        _ <- ZIO.sleep(3.seconds)

        _ <- showAnswer

        _ <- ZIO.sleep(3.seconds)
      } yield ()).forever.drain

  override def run =
    ZStream.fromZIO(Server.serve(app))
      .merge(gameApp)
      .runDrain
      .provide(
        GameRepoImpl.layer,
        Redis.layer,
        RedisExecutor.layer,
        ZLayer.succeed(RedisConfig.Default),
        ZLayer.succeed[CodecSupplier](ProtobufCodecSupplier),
        Server.defaultWithPort(8090)
      )
