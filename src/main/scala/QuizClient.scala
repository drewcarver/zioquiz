import zio._
import zio.http._
import zio.stream.ZStream
import zio.http.ChannelEvent.Read
import zio.http.Channel
import zio.http.ChannelEvent.UserEvent
import zio.http.ChannelEvent.UserEventTriggered
import scala.collection.mutable.HashMap

object QuizClient:
  val connections = new HashMap[String, WebSocketChannel]()
  
  private def getUserSub(jwt: String): String =
    jwt

  private def sendToAllClients(webSocketFrame: WebSocketFrame) = 
    ZIO.collectAll(
      connections
      .values
      .map(c => c.send(Read(webSocketFrame)))
    )

  private def socketApp(req: Request): SocketApp[Any] =
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
        case _ =>
          ZIO.unit
      }
    }

  val app: Http[Any, Nothing, Request, Response] =
    Http.collectZIO[Request] {
      case req @ Method.GET -> Root / "subscriptions" =>
        socketApp(req).toResponse
    }

