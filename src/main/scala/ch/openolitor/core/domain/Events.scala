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
package ch.openolitor.core.domain

import java.util.UUID
import ch.openolitor.core.models._
import spray.json._
import scala.reflect._
import akka.serialization._
import scala.util._
import akka.actor._

case class EventMetadata(version: Int, timestamp: Long, seqNr: Long, source: String)

sealed trait PersistetEvent extends Serializable with Product {
  val meta: EventMetadata
}

//events raised by this aggregateroot
case class EntityStoreInitialized(meta: EventMetadata) extends PersistetEvent
case class EntityInsertedEvent(meta: EventMetadata, id: UUID, entity: AnyRef) extends PersistetEvent
case class EntityUpdatedEvent(meta: EventMetadata, entity: BaseEntity[_ <: BaseId]) extends PersistetEvent
case class EntityDeletedEvent(meta: EventMetadata, id: BaseId) extends PersistetEvent

trait SerializationReference {
  val serialization: Serialization
}

trait BaseEventsJsonProtocol extends DefaultJsonProtocol with SerializationReference {

  implicit val metaFormat = jsonFormat4(EventMetadata.apply)
  implicit val entityStoreInitializeFormat = jsonFormat1(EntityStoreInitialized.apply)

  implicit val entityDeletedFormat = new RootJsonFormat[EntityDeletedEvent] {
    def write(obj: EntityDeletedEvent): JsValue = {
      serialization.serialize(obj.id) match {
        case Success(bytes) =>
          JsObject("meta" -> obj.meta.toJson, "class" -> JsString(obj.getClass.toString), "id" -> JsString(new String(bytes, "UTF-8")))
        case Failure(e) =>
          serializationError(e.getMessage)
      }
    }

    def read(json: JsValue): EntityDeletedEvent = json match {
      case JsObject(fields) if (fields.isDefinedAt("meta") && fields.isDefinedAt("class") && fields.isDefinedAt("id")) => {
        val meta = fields("meta").convertTo[EventMetadata]
        val clazz = fields("clazz").convertTo[String]
        val bytes = fields("id").convertTo[String]
        serialization.deserialize(bytes.getBytes("UTF-8"), Class.forName(clazz)) match {
          case Success(id) => EntityDeletedEvent(meta, id.asInstanceOf[BaseId])
          case Failure(e) => serializationError(e.getMessage)
        }
      }
      case x => serializationError(s"Can deserialize EntityDeletedEvent:$x")
    }
  }

  implicit val entityUpdatedFormat = new RootJsonFormat[EntityUpdatedEvent] {
    def write(obj: EntityUpdatedEvent): JsValue = {
      serialization.serialize(obj.entity) match {
        case Success(bytes) =>
          JsObject("meta" -> obj.meta.toJson, "class" -> JsString(obj.getClass.toString), "entity" -> JsString(new String(bytes, "UTF-8")))
        case Failure(e) =>
          serializationError(e.getMessage)
      }
    }

    def read(json: JsValue): EntityUpdatedEvent = json match {
      case JsObject(fields) if (fields.isDefinedAt("meta") && fields.isDefinedAt("class") && fields.isDefinedAt("entity")) => {
        val meta = fields("meta").convertTo[EventMetadata]
        val clazz = fields("clazz").convertTo[String]
        val bytes = fields("entity").convertTo[String]
        serialization.deserialize(bytes.getBytes("UTF-8"), Class.forName(clazz)) match {
          case Success(entity) => EntityUpdatedEvent(meta, entity.asInstanceOf[BaseEntity[_ <: BaseId]])
          case Failure(e) => serializationError(e.getMessage)
        }
      }
      case x => serializationError(s"Can deserialize EntityUpdatedEvent:$x")
    }
  }

  implicit val entityInsertedFormat = new RootJsonFormat[EntityInsertedEvent] {
    def write(obj: EntityInsertedEvent): JsValue = {
      serialization.serialize(obj.entity) match {
        case Success(bytes) =>
          JsObject("meta" -> obj.meta.toJson, "id" -> JsString(obj.id.toString), "class" -> JsString(obj.getClass.toString), "entity" -> JsString(new String(bytes, "UTF-8")))
        case Failure(e) =>
          serializationError(e.getMessage)
      }
    }

    def read(json: JsValue): EntityInsertedEvent = json match {
      case JsObject(fields) if (fields.isDefinedAt("meta") && fields.isDefinedAt("id") && fields.isDefinedAt("class") && fields.isDefinedAt("entity")) => {
        val meta = fields("meta").convertTo[EventMetadata]
        val clazz = fields("clazz").convertTo[String]
        val id = fields("id").convertTo[String]
        val bytes = fields("entity").convertTo[String]
        serialization.deserialize(bytes.getBytes("UTF-8"), Class.forName(clazz)) match {
          case Success(entity) => EntityInsertedEvent(meta, UUID.fromString(id), entity.asInstanceOf[AnyRef])
          case Failure(e) => serializationError(e.getMessage)
        }
      }
      case x => serializationError(s"Can deserialize EntityInsertedEvent:$x")
    }
  }

  implicit val persistetEventFormat = new RootJsonFormat[PersistetEvent] {
    def write(obj: PersistetEvent): JsValue =
      JsObject((obj match {
        case e: EntityStoreInitialized => e.toJson
        case e: EntityInsertedEvent => e.toJson
        case e: EntityUpdatedEvent => e.toJson
        case e: EntityDeletedEvent => e.toJson
      }).asJsObject.fields + ("type" -> JsString(obj.productPrefix)))

    def read(json: JsValue): PersistetEvent =
      json.asJsObject.getFields("type") match {
        case Seq(JsString("EntityStoreInitialized")) => json.convertTo[EntityStoreInitialized]
        case Seq(JsString("EntityInsertedEvent")) => json.convertTo[EntityInsertedEvent]
        case Seq(JsString("EntityUpdatedEvent")) => json.convertTo[EntityUpdatedEvent]
        case Seq(JsString("EntityDeletedEvent")) => json.convertTo[EntityDeletedEvent]
      }
  }
}