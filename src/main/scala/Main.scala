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
        case Read(WebSocketFrame.Text("start")) =>
          ZIO.unit
        case _ =>
          ZIO.unit
      }
    }

  val app: Http[GameRepo, Nothing, Request, Response] =
    Http.collectZIO[Request] {
      case req @ Method.GET -> Root / "subscriptions" =>
        socketApp(req).toResponse
    }

  val gameApp: ZStream[Any, Throwable, Nothing] = 
    ZStream.fromZIO(
      ZIO.collectAll(connections.values.map(c => c.send(Read(WebSocketFrame.text("test")))))
    ).repeat(Schedule.fixed(5.seconds)).forever.drain

  override def run =
    ZStream.fromZIO(Server
      .serve(app)).merge(gameApp).runDrain
      .provide(
        GameRepoImpl.layer,
        Redis.layer,
        RedisExecutor.layer,
        ZLayer.succeed(RedisConfig.Default),
        ZLayer.succeed[CodecSupplier](ProtobufCodecSupplier),
        Server.defaultWithPort(8090)
      )
