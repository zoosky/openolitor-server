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

import spray.json.DefaultJsonProtocol
import spray.json._
import org.joda.time._
import org.joda.time.format._
import ch.openolitor.core.models._
import java.util.UUID

/**
 * Basis JSON Formatter for spray-json serialisierung/deserialisierung
 */
object BaseJsonProtocol extends DefaultJsonProtocol {
  val defaultConvert: Any => String = x => x.toString

  def enumFormat[E](implicit fromJson: String => E, toJson: E => String = defaultConvert) = new JsonFormat[E] {
    def write(obj: E): JsValue = JsString(toJson(obj))

    def read(json: JsValue): E =
      json match {
        case (JsString(value)) => fromJson(value)
        case value => deserializationError(s"Unrecognized enum format:$value")
      }
  }

  def baseIdFormat[I <: BaseId](implicit fromJson: UUID => I) = new JsonFormat[I] {
    def write(obj: I): JsValue = JsString(obj.id.toString)

    def read(json: JsValue): I =
      json match {
        case (JsString(value)) => fromJson(UUID.fromString(value))
        case value => deserializationError(s"Unrecognized baseId format:$value")
      }
  }

  /*
   * joda datetime format
   */
  implicit val dateTimeFormat = new JsonFormat[DateTime] {

    val formatter = ISODateTimeFormat.basicDateTimeNoMillis

    def write(obj: DateTime): JsValue = {
      JsString(formatter.print(obj))
    }

    def read(json: JsValue): DateTime = json match {
      case JsString(s) => try {
        formatter.parseDateTime(s)
      } catch {
        case t: Throwable => error(s)
      }
      case _ =>
        error(json.toString())
    }

    def error(v: Any): DateTime = {
      val example = formatter.print(0)
      deserializationError(f"'$v' is not a valid date value. Dates must be in compact ISO-8601 format, e.g. '$example'")
    }
  }
  implicit val dateTimeOptionFormat = new JsonFormat[Option[DateTime]] {

    val formatter = ISODateTimeFormat.basicDateTimeNoMillis

    def write(obj: Option[DateTime]): JsValue = {
      obj.map(x => JsString(formatter.print(x))).getOrElse(JsNull)
    }

    def read(json: JsValue): Option[DateTime] = json match {
      case JsString(s) => try {
        Some(formatter.parseDateTime(s))
      } catch {
        case t: Throwable => Some(error(s))
      }
      case _ =>
        None
    }

    def error(v: Any): DateTime = {
      val example = formatter.print(0)
      deserializationError(f"'$v' is not a valid date value. Dates must be in compact ISO-8601 format, e.g. '$example'")
    }
  }

}