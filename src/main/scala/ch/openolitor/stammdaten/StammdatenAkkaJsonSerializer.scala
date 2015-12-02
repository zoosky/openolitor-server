package ch.openolitor.stammdaten

import akka.serialization.Serializer
import spray.json._
import ch.openolitor.stammdaten.dto.StammdatenCreateModel

class StammdatenAkkaJsonSerializer extends Serializer {
  import StammdatenJsonProtocol._
  // This is whether "fromBinary" requires a "clazz" or not
  def includeManifest: Boolean = true

  val charset = "UTF-8"

  // Pick a unique identifier for your Serializer,
  // you've got a couple of billions to choose from,
  // 0 - 16 is reserved by Akka itself
  def identifier = 1234532531

  // "toBinary" serializes the given object to an Array of Bytes
  def toBinary(obj: AnyRef): Array[Byte] = {
    if (obj.isInstanceOf[StammdatenBaseEntity[_]]) {
      val entity = obj.asInstanceOf[StammdatenBaseEntity[_]]
      val json = stammdatenBaseEntityFormat.write(entity)
      json.toString.getBytes(charset);
    } else if (obj.isInstanceOf[StammdatenCreateModel]) {
      val entity = obj.asInstanceOf[StammdatenCreateModel]
      val json = stammdatenCreateModelFormat.write(entity)
      json.toString.getBytes(charset);
    } else if (obj.isInstanceOf[StammdatenBaseId]) {
      val entity = obj.asInstanceOf[StammdatenBaseId]
      val json = stammdatenBaseIdFormat.write(entity)
      json.toString.getBytes(charset);
    } else {
      Array.empty
    }
  }

  def isAssignableFrom[T: Manifest](c: Class[_]) =
    manifest[T].runtimeClass.isAssignableFrom(c)

  // "fromBinary" deserializes the given array,
  // using the type hint (if any, see "includeManifest" above)
  def fromBinary(bytes: Array[Byte],
    clazz: Option[Class[_]]): AnyRef = {

    val r = clazz.map { cl =>
      if (isAssignableFrom[StammdatenBaseEntity[_]](cl)) {
        val json = JsonParser(new String(bytes, charset))
        stammdatenBaseEntityFormat.read(json)
      } else if (isAssignableFrom[StammdatenCreateModel](cl)) {
        val json = JsonParser(new String(bytes, charset))
        stammdatenCreateModelFormat.read(json)
      } else if (isAssignableFrom[StammdatenBaseId](cl)) {
        val json = JsonParser(new String(bytes, charset))
        stammdatenBaseIdFormat.read(json)
      }
    }
    r.asInstanceOf[AnyRef]
  }

}