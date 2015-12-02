/*                                                                           *\
*    ____                   ____  ___ __                                      *
*   / __ \____  ___  ____  / __ \/ (_) /_____  _____                          *
*  / / / / __ \/ _ \/ __ \/ / / / / / __/ __ \/ ___/   OpenOlitor             *
* / /_/ / /_/ /  __/ / / / /_/ / / / /_/ /_/ / /       contributed by tegonal *
* \____/ .___/\___/_/ /_/\____/_/_/\__/\____/_/        http://openolitor.ch   *
*     /_/                                                                     *
*                                                                             *
* This program is free software: you can redistribute it and/or modify it     *
* under the terms of the GNU General Public License as published by           *
* the Free Software Foundation, either version 3 of the License,              *
* or (at your option) any later version.                                      *
*                                                                             *
* This program is distributed in the hope that it will be useful, but         *
* WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY  *
* or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for *
* more details.                                                               *
*                                                                             *
* You should have received a copy of the GNU General Public License along     *
* with this program. If not, see http://www.gnu.org/licenses/                 *
*                                                                             *
\*                                                                           */
package ch.openolitor.core

import akka.serialization._
import ch.openolitor.core.domain._
import akka.actor.ActorSystem
import spray.json.JsonParser
import akka.actor.ActorSystem

class AkkaJsonSerializer extends Serializer with BaseEventsJsonProtocol {
  // This is whether "fromBinary" requires a "clazz" or not
  def includeManifest: Boolean = true

  val charset = "UTF-8"

  val serialization = SerializationExtension(Boot.defaultAkkaSystem)

  // Pick a unique identifier for your Serializer,
  // you've got a couple of billions to choose from,
  // 0 - 16 is reserved by Akka itself
  def identifier = 763232434

  // "toBinary" serializes the given object to an Array of Bytes
  def toBinary(obj: AnyRef): Array[Byte] = {
    if (obj.isInstanceOf[PersistetEvent]) {
      val event = obj.asInstanceOf[PersistetEvent]
      val json = persistetEventFormat.write(event)
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
      if (isAssignableFrom[PersistetEvent](cl)) {
        val json = JsonParser.apply(new String(bytes, charset))
        persistetEventFormat.read(json)
      }
    }
    r.asInstanceOf[AnyRef]
  }

}

