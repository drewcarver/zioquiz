import zio._
import zio.redis._
import zio.json._
import monocle.syntax.all._

case class QuizGame(gameId: Int, players: List[String])
object QuizGame:
  implicit val decoder: JsonDecoder[QuizGame] =
    DeriveJsonDecoder.gen[QuizGame]
  implicit val encoder: JsonEncoder[QuizGame] =
    DeriveJsonEncoder.gen[QuizGame]

trait GameRepo:
  def get(gameId: String): Task[QuizGame]
  def create(): Task[QuizGame]
  def join(gameId: String): Task[QuizGame]

case class GameRepoImpl(
    redis: Redis
) extends GameRepo:
  override def create(): Task[QuizGame] =
    for {
      gameId <- Random.nextInt
      playerId <- Random.nextInt
      game = QuizGame(gameId, List(playerId.toString()))

      succeeded <- redis
        .set("game", game.toJson, Some(1.hour))
    } yield (game)

  override def get(gameId: String): Task[QuizGame] =
    for 
      gameJson <- redis.get(gameId)
        .returning[String]
        .flatMap(ZIO.fromOption)
        .mapError(e => new Throwable("Couldn't find game."))
      game <- ZIO.fromEither(gameJson.fromJson[QuizGame])
        .mapError(e => new Throwable("Couldn't parse game JSON."))
    yield game

  override def join(gameId: String): Task[QuizGame] =
    for {
      playerId <- Random.nextInt
      game <- get(gameId)
      gameWithJoinedPlayer = game
        .focus(_.players)
        .modify(_.::(playerId.toString()))

      success <- redis
        .set(
          gameId,
          gameWithJoinedPlayer.toJson,
          Some(4.hour)
        )
        .mapError(_ => new Throwable("Couldn't join game."))
    } yield gameWithJoinedPlayer

object GameRepoImpl:
    val layer: ZLayer[Redis, Nothing, GameRepo] = 
      ZLayer {
        for 
          redis <- ZIO.service[Redis]
        yield GameRepoImpl(redis)
      }

object GameRepo:
  def join(id: String): ZIO[GameRepo, Throwable, QuizGame] =
    ZIO.serviceWithZIO[GameRepo](_.join(id))
  def create(): ZIO[GameRepo, Throwable, QuizGame] =
    ZIO.serviceWithZIO[GameRepo](_.create())
  def get(id: String): ZIO[GameRepo, Throwable, QuizGame] =
    ZIO.serviceWithZIO[GameRepo](_.get(id))