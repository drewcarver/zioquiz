import zio._

final case class KafkaConfig(hostname: String)
object KafkaConfig:
  val config: Config[KafkaConfig] =
    (Config.string("HOSTNAME")).map {
      case (hostname) => KafkaConfig(hostname)
    }
