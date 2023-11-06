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
  val gameWithConnections =
    new HashMap[Int, HashMap[String, WebSocketChannel]]()

  private def getUserSub(jwt: String): String =
    jwt

  private def sendToAllClients(gameId: Int, webSocketFrame: WebSocketFrame) =
    ZIO.collectAll(
      gameWithConnections(gameId).map({ case (key, channel) =>
        channel
          .send(Read(webSocketFrame))
          .catchAll(_ => ZIO.succeed(gameWithConnections(gameId).remove(key)))
      })
    )

  val sendQuestionToClient =
    Consumer
      .plainStream(
        Subscription.topics(QUESTION_ASKED_TOPIC),
        Serde.string,
        Serde.string
      )
      .tap(message => 
        for 
          questionAsked <- ZIO.fromEither(message.value.fromJson[QuizKafkaEvent.QuizKafkaEvent])
          _ <- questionAsked match 
            case QuizKafkaEvent.QuestionAsked(gameId, question) => sendToAllClients(gameId, WebSocketFrame.text(question))
        yield ())
      .map(_.offset)
      .aggregateAsync(Consumer.offsetBatches)
      .mapZIO(_.commit)
      .drain

  private def getUsername(request: Request) =
    ZIO
      .fromOption(request.url.queryParams.get("username"))
      .map(_.mkString)
      .mapError(e => new Throwable("Couldn't get auth header."))

  def createGame(request: Request, channel: WebSocketChannel) =
    ZIO
      .fromOption(request.url.queryParams.get("connectionType"))
      .mapError(_ => new Throwable("Couldn't find connection type."))
      .flatMap(_.mkString match 
          case "create" =>
            for 
              username <- getUsername(request)
              gameId <- Random.nextIntBetween(0, Int.MaxValue) 
              gameHashmap = new HashMap[String, WebSocketChannel]()
              _ <- ZIO.succeed(gameHashmap += (username -> channel))
              _ <- ZIO.succeed(gameWithConnections += (gameId -> gameHashmap))
              _ <- Producer.produce(
                  CREATE_GAME_TOPIC,
                  gameId,
                  QuizKafkaEvent.CreateGame(gameId).asInstanceOf[QuizKafkaEvent.QuizKafkaEvent].toJson,
                  Serde.int,
                  Serde.string
                )
            yield ()
          case "join" =>
            for 
              username <- getUsername(request)
              gameId <- ZIO.fromOption(request.url.queryParams.get("gameId"))
                .map(_.mkString.toInt)
                .mapError(_ => new Throwable("Coudln't find game id to join."))
              _ <- ZIO.succeed(gameWithConnections(gameId) += (username -> channel))
              _ <- Console.printLine(s"User ${username} joined the game.")
            yield ()
        )
            

  private def socketApp(request: Request): SocketApp[Producer] =
    Handler.webSocket { channel =>
      channel.receiveAll {
        case Read(WebSocketFrame.Close(status, reason)) => 
          for
            username <- getUsername(request)
            _ <- Console.printLine(s"User ${username} left the game.")
          yield ()
        case UserEventTriggered(UserEvent.HandshakeComplete) => createGame(request, channel)
        case UserEventTriggered(WebSocketFrame.Text(event)) => getUsername(request).flatMap(handleEvent(_, event))
        case _ => ZIO.unit
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
        case QuestionAnswered(questionId, username, answer) =>
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
