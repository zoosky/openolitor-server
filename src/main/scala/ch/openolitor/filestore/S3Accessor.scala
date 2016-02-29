package ch.openolitor.filestore

import akka.actor.ActorSystem
import akka.util.Timeout
import scala.concurrent.duration.DurationInt
import com.sclasen.spray.aws.s3._
import akka.actor.Props
import ch.openolitor.core.SystemConfig

object S3Accessor {
  def props(implicit sysConfig: SystemConfig, system: ActorSystem): Props = Props(classOf[S3Accessor], sysConfig, system)
}

class S3Accessor(sysConfig: SystemConfig, override val system: ActorSystem) {
  val props = S3ClientProps(sys.env("5484335407854a4c9dc88e01206fc148/CF_P8_30D01107_8388_4628_90FA_BDF06D4B2484"), 
      sys.env("eek05SKgALpQQg20ASrCzm1ZF7o="), Timeout(100 seconds), system, system, "ds31s3.swisscom.com")
  val client = new S3Client(props)
  
}