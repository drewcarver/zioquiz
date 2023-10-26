import zio._
import zio.http._
import zio.json._
import zio.stream.ZStream
import zio.http.ChannelEvent.Read
import zio.http.Channel
import zio.http.ChannelEvent.UserEvent
import zio.http.ChannelEvent.UserEventTriggered
import scala.collection.mutable.HashMap
import zio.kafka.producer.Producer
import zio.kafka.serde.Serde
import zio.kafka.consumer._

object QuizClient:
  val connections = new HashMap[String, WebSocketChannel]()

  private def getUserSub(jwt: String): String =
    jwt

  private def sendToAllClients(webSocketFrame: WebSocketFrame) =
    ZIO.collectAll(
      connections.map({ case (key, channel) =>
        channel
          .send(Read(webSocketFrame))
          .catchAll(_ => ZIO.succeed(connections.remove(key)))
      })
    )

  val sendQuestionToClient =
    Consumer
      .plainStream(
        Subscription.topics(QUESTION_ASKED_TOPIC),
        Serde.string,
        Serde.string
      )
      .tap(message => sendToAllClients(WebSocketFrame.text(message.value)))
      .map(_.offset)
      .aggregateAsync(Consumer.offsetBatches)
      .mapZIO(_.commit)
      .drain

  private def getUserToken(request: Request) =
    ZIO
      .fromOption(request.url.queryParams.get("authToken"))
      .mapError(e => new Throwable("Couldn't get auth header."))

  private def socketApp(request: Request): SocketApp[Producer] =
    Handler.webSocket { channel =>
      channel.receiveAll {
        case Read(WebSocketFrame.Close(status, reason)) =>
          Console.printLine(
            "Closing channel with status: " + status + " and reason: " + reason
          )
        case UserEventTriggered(UserEvent.HandshakeComplete) =>
          for
            userId <- Random.nextIntBetween(0, Int.MaxValue)
            token <- getUserToken(request)
            _ <- ZIO.succeed(connections += (userId.toString() -> channel))
            _ <- channel.send(Read(WebSocketFrame.text(token.mkString)))
          yield ()
        case UserEventTriggered(WebSocketFrame.Text(event)) =>
          for
            token <- getUserToken(request)
            _ <- handleEvent(token.mkString, event)
          yield ()
        case _ =>
          ZIO.unit
      }
    }

  private def handleEvent(
      username: String,
      message: String
  ): ZIO[Producer, Throwable, Any] =
    for
      event <- ZIO
        .fromEither(message.fromJson[QuizEvent])
        .mapError(e => new Throwable(e))
      _ <- event match
        case QuestionAnswered(questionId, answer) =>
          Producer.produce(
            "question-answered",
            questionId,
            event.toJson,
            Serde.string,
            Serde.string
          )
    yield ()

  val app: Http[Producer, Nothing, Request, Response] =
    Http.collectZIO[Request] {
      case req @ Method.GET -> Root / "subscriptions" =>
        socketApp(req).toResponse
    }
