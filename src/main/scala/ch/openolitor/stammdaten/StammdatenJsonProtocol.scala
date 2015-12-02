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
package ch.openolitor.stammdaten

import spray.json._
import ch.openolitor.core.models._
import java.util.UUID
import org.joda.time._
import org.joda.time.format._
import ch.openolitor.core.BaseJsonProtocol
import ch.openolitor.stammdaten.dto._

/**
 * JSON Format deklarationen für das Modul Stammdaten
 */
object StammdatenJsonProtocol extends DefaultJsonProtocol {
  import BaseJsonProtocol._

  //enum formats
  implicit val wochentagFormat = enumFormat(x => Wochentag.apply(x).getOrElse(Montag))
  implicit val rhythmusFormat = enumFormat(Rhythmus.apply)
  implicit val preiseinheitFormat = enumFormat(Preiseinheit.apply)
  implicit val waehrungFormat = enumFormat(Waehrung.apply)

  //id formats
  implicit val vertriebsartIdFormat = baseIdFormat(VertriebsartId.apply)
  implicit val abotypIdFormat = baseIdFormat(AbotypId.apply)
  implicit val depotIdFormat = baseIdFormat(DepotId.apply)
  implicit val tourIdFormat = baseIdFormat(TourId.apply)

  implicit val lieferzeitpunktFormat = new RootJsonFormat[Lieferzeitpunkt] {
    def write(obj: Lieferzeitpunkt): JsValue =
      JsObject((obj match {
        case w: Wochentag => w.toJson
      }).asJsObject.fields + ("type" -> JsString(obj.productPrefix)))

    def read(json: JsValue): Lieferzeitpunkt =
      json.asJsObject.getFields("type") match {
        case Seq(JsString("Wochentag")) => json.convertTo[Wochentag]
      }
  }

  implicit val depotlieferungFormat = jsonFormat4(Depotlieferung.apply)
  implicit val heimlieferungFormat = jsonFormat4(Heimlieferung.apply)
  implicit val postlieferungFormat = jsonFormat3(Postlieferung.apply)

  implicit val vertriebsartFormat = new JsonFormat[Vertriebsart] {
    def write(obj: Vertriebsart): JsValue =
      JsObject((obj match {
        case p: Postlieferung => p.toJson
        case hl: Heimlieferung => hl.toJson
        case dl: Depotlieferung => dl.toJson
      }).asJsObject.fields + ("type" -> JsString(obj.productPrefix)))

    def read(json: JsValue): Vertriebsart =
      json.asJsObject.getFields("type") match {
        case Seq(JsString("Postlieferung")) => json.convertTo[Postlieferung]
        case Seq(JsString("Heimlieferung")) => json.convertTo[Heimlieferung]
        case Seq(JsString("Depotlieferung")) => json.convertTo[Depotlieferung]
      }
  }

  implicit val depot = jsonFormat3(Depot.apply)
  implicit val tour = jsonFormat3(Tour.apply)

  implicit val postlieferungDetailFormat = jsonFormat2(PostlieferungDetail.apply)
  implicit val depotlieferungDetailFormat = jsonFormat3(DepotlieferungDetail.apply)
  implicit val heimlieferungDetailFormat = jsonFormat3(HeimlieferungDetail.apply)

  implicit val vertriebsartDetailFormat = new JsonFormat[Vertriebsartdetail] {
    def write(obj: Vertriebsartdetail): JsValue =
      JsObject((obj match {
        case p: PostlieferungDetail => p.toJson
        case hl: HeimlieferungDetail => hl.toJson
        case dl: DepotlieferungDetail => dl.toJson
      }).asJsObject.fields + ("type" -> JsString(obj.productPrefix)))

    def read(json: JsValue): Vertriebsartdetail =
      json.asJsObject.getFields("type") match {
        case Seq(JsString("PostlieferungDetail")) => json.convertTo[PostlieferungDetail]
        case Seq(JsString("HeimlieferungDetail")) => json.convertTo[HeimlieferungDetail]
        case Seq(JsString("DepotlieferungDetail")) => json.convertTo[DepotlieferungDetail]
      }
  }

  implicit val abotypFormat = jsonFormat12(Abotyp.apply)
  implicit val abotypDetailFormat = jsonFormat13(AbotypDetail.apply)
  implicit val abotypCreateFormat = jsonFormat9(AbotypCreate.apply)

  implicit val stammdatenBaseEntityFormat = new JsonFormat[StammdatenBaseEntity[_]] {
    def write(obj: StammdatenBaseEntity[_]): JsValue =
      JsObject((obj match {
        case e: Abotyp => e.toJson
        case e: Depot => e.toJson
        case e: Vertriebsart => e.toJson
        case e: Tour => e.toJson
      }).asJsObject.fields + ("type" -> JsString(obj.productPrefix)))

    def read(json: JsValue): StammdatenBaseEntity[_] =
      json.asJsObject.getFields("type") match {
        case Seq(JsString("Abotyp")) => json.convertTo[Abotyp]
        case Seq(JsString("Depot")) => json.convertTo[Depot]
        case Seq(JsString("Vertriebsart")) => json.convertTo[Vertriebsart]
        case Seq(JsString("Tour")) => json.convertTo[Tour]
      }
  }

  implicit val stammdatenCreateModelFormat = new JsonFormat[StammdatenCreateModel] {
    def write(obj: StammdatenCreateModel): JsValue =
      JsObject((obj match {
        case e: AbotypCreate => e.toJson
      }).asJsObject.fields + ("type" -> JsString(obj.productPrefix)))

    def read(json: JsValue): StammdatenCreateModel =
      json.asJsObject.getFields("type") match {
        case Seq(JsString("AbotypCreate")) => json.convertTo[AbotypCreate]
      }
  }

  implicit val stammdatenBaseIdFormat = new JsonFormat[StammdatenBaseId] {
    def write(obj: StammdatenBaseId): JsValue =
      JsObject((obj match {
        case e: AbotypId => e.toJson
        case e: DepotId => e.toJson
        case e: TourId => e.toJson
        case e: VertriebsartId => e.toJson
      }).asJsObject.fields + ("type" -> JsString(obj.productPrefix)))

    def read(json: JsValue): StammdatenBaseId =
      json.asJsObject.getFields("type") match {
        case Seq(JsString("AbotypId")) => json.convertTo[AbotypId]
        case Seq(JsString("DepotId")) => json.convertTo[DepotId]
        case Seq(JsString("TourId")) => json.convertTo[TourId]
        case Seq(JsString("VertriebsartId")) => json.convertTo[VertriebsartId]
      }
  }
}
