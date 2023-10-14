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
  def get(gameId: String): Task[Either[String, QuizGame]]
  def create(): Task[QuizGame]
  def join(gameId: String): ZIO[Any, String, QuizGame]

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

  override def get(gameId: String): Task[Either[String, QuizGame]] =
    for gameJson <- redis.get(gameId).returning[String]
    yield gameJson
      .toRight("Game not found")
      .flatMap(_.fromJson[QuizGame])

  override def join(gameId: String): ZIO[Any, String, QuizGame] =
    for {
      playerId <- Random.nextInt
      game <- get(gameId).mapError(_ => "Couldn't find game.")
      gameWithJoinedPlayer <- ZIO.fromEither(
        game
          .map(g => g.focus(_.players).modify(_.::(playerId.toString())))
      )

      success <- redis
        .set(
          gameId,
          gameWithJoinedPlayer.toJson,
          Some(4.hour)
        )
        .mapError(_ => "Couldn't join game.")
    } yield gameWithJoinedPlayer
