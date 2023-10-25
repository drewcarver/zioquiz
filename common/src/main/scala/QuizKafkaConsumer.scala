import zio._
import zio.kafka.consumer._

object QuizKafkaConsumer:
  val layer =
    ZLayer.scoped {
      for
        config <- ZIO.config(KafkaConfig.config)
        settings = ConsumerSettings(List(config.hostname)).withGroupId("group")
        consumer <- Consumer.make(settings)
      yield consumer
    }
