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
  case class GameClient(id: String)
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

  def socketApp(req: Request): SocketApp[Any] =
    Handler.webSocket { channel =>
      channel.receiveAll {
        case UserEventTriggered(UserEvent.HandshakeComplete) =>
          for
            userId <- Random.nextIntBetween(0, Int.MaxValue)
            token <- ZIO
              .fromOption(req.url.queryParams.get("authToken"))
              .mapError(e => new Throwable("Couldn't get auth header."))
            _ <- ZIO.succeed(connections += (userId.toString() -> channel))
            _ <- channel.send(Read(WebSocketFrame.text(s"${token.mkString}")))
          yield ()
        case Read(WebSocketFrame.Text(event))
            if event.startsWith(JOIN_GAME_EVENT_PREFIX) =>
          val gameId = event.split(":").last
          channel.send(Read(WebSocketFrame.text(gameId)))
        case Read(WebSocketFrame.Text(event))
            if event.startsWith(JOIN_GAME_EVENT_PREFIX) =>
          val gameId = event.split(":").last
          channel.send(Read(WebSocketFrame.text(gameId)))
        case Read(WebSocketFrame.Text("test")) =>
          ZIO.collectAll(
            connections.values.map(c =>
              c.send(Read(WebSocketFrame.text("hi!")))
            )
          )
        case _ =>
          ZIO.unit
      }
    }

  val app: Http[Redis, Nothing, Request, Response] =
    Http.collectZIO[Request] {
      case req @ Method.GET -> Root / "subscriptions" =>
        socketApp(req).toResponse
      case Method.POST -> Root / "game" =>
        createGame.fold(
          Response.text(_),
          game => Response.json(game.toJson)
        )
    }

  override def run =
    Server
      .serve(app)
      .provide(
        Redis.layer,
        RedisExecutor.layer,
        ZLayer.succeed(RedisConfig.Default),
        ZLayer.succeed[CodecSupplier](ProtobufCodecSupplier),
        Server.defaultWithPort(8090)
      )
