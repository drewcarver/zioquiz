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

  val sendScoreToClient =
    Consumer
      .plainStream(
        Subscription.topics(SCORE_UPDATED_TOPIC),
        Serde.string,
        Serde.string
      )
      .tap(message => 
          for
            _ <- sendToAllClients(WebSocketFrame.text(message.value))
          yield ()
      )
      .map(_.offset)
      .aggregateAsync(Consumer.offsetBatches)
      .mapZIO(_.commit)
      .drain

  val sendQuestionToClient =
    Consumer
      .plainStream(
        Subscription.topics(QUESTION_ASKED_TOPIC),
        Serde.string,
        Serde.string
      )
      .tap(message => 
          for
            _ <- Console.printLine("question was asked")
            _ <- sendToAllClients(WebSocketFrame.text(message.value))
          yield ()
      )
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
        case UserEventTriggered(UserEvent.HandshakeComplete) =>
          for
            userId <- Random.nextIntBetween(0, Int.MaxValue)
            token <- getUserToken(request)
            _ <- ZIO.succeed(connections += (userId.toString() -> channel))
            _ <- channel.send(Read(WebSocketFrame.text("hi")))
          yield ()
        case Read(WebSocketFrame.Text(event)) =>
          for
            token <- getUserToken(request)
            _ <- handleEvent(token.mkString, event)
          yield ()
        case e =>
          Console.printLine(e.toString)
      }
    }

  private def handleEvent(
      username: String,
      message: String
  ): ZIO[Producer, Throwable, Any] =
    for
      event <- ZIO
        .fromEither(message.fromJson[QuizEvent])
        .mapError(e => Console.printLine(e))
        .mapError(_ => new Throwable("Couldn't parse"))
      _ <- event match
        case QuestionAnswered(questionId, playerId, answer) =>
          for
            _ <- Producer.produce(
                QUESTION_ANSWERED_TOPIC,
                questionId,
                event.toJson,
                Serde.string,
                Serde.string
              )
          yield ()
        case _ => Console.printLine("Don't know what to do with this")
    yield ()

  val app: Http[Producer, Nothing, Request, Response] =
    Http.collectZIO[Request] {
      case req @ Method.GET -> Root / "subscriptions" =>
        socketApp(req).toResponse
    }
