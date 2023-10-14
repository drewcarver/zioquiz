import mill._, scalalib._

object ZioQuiz extends RootModule with ScalaModule {
  def scalaVersion = "3.3.1"
  def ivyDeps = Agg(
    ivy"dev.zio::zio-http:3.0.0-RC2",
    ivy"dev.zio::zio-schema-protobuf:0.4.9",
    ivy"dev.zio::zio-redis:0.2.0",
    ivy"dev.optics::monocle-core:3.2.0"
  )
}
