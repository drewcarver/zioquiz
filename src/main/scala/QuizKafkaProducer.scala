import zio._
import zio.kafka.producer._

object QuizKafkaProducer:
  val layer =
    ZLayer.scoped {
      for {
        config <- ZIO.config(KafkaConfig.config)
        settings = ProducerSettings(List(config.hostname))
        producer <- Producer.make(settings)
      } yield producer
    }
