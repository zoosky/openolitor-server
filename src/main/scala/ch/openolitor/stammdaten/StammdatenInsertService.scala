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

import scalaz._
import Scalaz._
import ch.openolitor.core._
import ch.openolitor.core.db._
import ch.openolitor.core.domain._
import ch.openolitor.stammdaten._
import ch.openolitor.stammdaten.models._
import java.util.UUID
import scalikejdbc.DB
import com.typesafe.scalalogging.LazyLogging
import ch.openolitor.core.domain.EntityStore._
import akka.actor.ActorSystem
import ch.openolitor.core.Macros._
import scala.concurrent.ExecutionContext.Implicits.global
import ch.openolitor.core.models._
import org.joda.time.DateTime
import ch.openolitor.core.Macros._
import shapeless.Tuple
import org.joda.time.DateTime

object StammdatenInsertService {
  def apply(implicit sysConfig: SystemConfig, system: ActorSystem): StammdatenInsertService = new DefaultStammdatenInsertService(sysConfig, system)
}

class DefaultStammdatenInsertService(sysConfig: SystemConfig, override val system: ActorSystem)
    extends StammdatenInsertService(sysConfig) with DefaultStammdatenRepositoryComponent {
}

/**
 * Actor zum Verarbeiten der Insert Anweisungen für das Stammdaten Modul
 */
class StammdatenInsertService(override val sysConfig: SystemConfig) extends EventService[EntityInsertedEvent[_, _]] with LazyLogging with AsyncConnectionPoolContextAware
  with StammdatenDBMappings {
  self: StammdatenRepositoryComponent =>

  val ZERO = 0

  val handle: Handle = {
    case EntityInsertedEvent(meta, id: AbotypId, abotyp: AbotypModify) =>
      createAbotyp(meta, id, abotyp)
    case EntityInsertedEvent(meta, id: KundeId, kunde: KundeModify) =>
      createKunde(meta, id, kunde)
    case EntityInsertedEvent(meta, id: PersonId, person: PersonCreate) =>
      val modify = copyTo[PersonCreate, PersonModify](person, "id" -> None)
      createPerson(meta, id, modify, person.kundeId, person.sort)
    case EntityInsertedEvent(meta, id: DepotId, depot: DepotModify) =>
      createDepot(meta, id, depot)
    case EntityInsertedEvent(meta, id: AboId, abo: AboModify) =>
      createAbo(meta, id, abo)
    case EntityInsertedEvent(meta, id: LieferungId, lieferung: LieferungAbotypCreate) =>
      createLieferung(meta, id, lieferung)
    case EntityInsertedEvent(meta, id: VertriebsartId, lieferung: HeimlieferungAbotypModify) =>
      createHeimlieferungVertriebsart(meta, id, lieferung)
    case EntityInsertedEvent(meta, id: VertriebsartId, lieferung: PostlieferungAbotypModify) =>
      createPostlieferungVertriebsart(meta, id, lieferung)
    case EntityInsertedEvent(meta, id: VertriebsartId, depotlieferung: DepotlieferungAbotypModify) =>
      createDepotlieferungVertriebsart(meta, id, depotlieferung)
    case EntityInsertedEvent(meta, id: CustomKundentypId, kundentyp: CustomKundentypCreate) =>
      createKundentyp(meta, id, kundentyp)
    case EntityInsertedEvent(meta, id: ProduktId, produkt: ProduktModify) =>
      createProdukt(meta, id, produkt)
    case EntityInsertedEvent(meta, id: ProduktekategorieId, produktekategorie: ProduktekategorieModify) =>
      createProduktekategorie(meta, id, produktekategorie)
    case EntityInsertedEvent(meta, id: ProduzentId, produzent: ProduzentModify) =>
      createProduzent(meta, id, produzent)
    case EntityInsertedEvent(meta, id: TourId, tour: TourModify) =>
      createTour(meta, id, tour)
    case EntityInsertedEvent(meta, id: ProjektId, projekt: ProjektModify) =>
      createProjekt(meta, id, projekt)
    case EntityInsertedEvent(meta, id, lieferplanungCreateData: LieferplanungCreate) =>
      createLieferplanung(id, lieferplanungCreateData)
    case EntityInsertedEvent(meta, id, bestellungCreateData: BestellungenCreate) =>
      createBestellungen(id, bestellungCreateData)
    case EntityInsertedEvent(meta, id, entity) =>
      logger.debug(s"Receive unmatched insert event for entity:$entity with id:$id")
    case e =>
      logger.warn(s"Unknown event:$e")
  }

  def createAbotyp(meta: EventMetadata, id: AbotypId, abotyp: AbotypModify)(implicit userId: UserId = meta.originator) = {
    val typ = copyTo[AbotypModify, Abotyp](abotyp,
      "id" -> id,
      "anzahlAbonnenten" -> ZERO,
      "letzteLieferung" -> None,
      "waehrung" -> CHF,
      "erstelldat" -> meta.timestamp,
      "ersteller" -> meta.originator,
      "modifidat" -> meta.timestamp,
      "modifikator" -> meta.originator)

    DB autoCommit { implicit session =>
      //create abotyp
      writeRepository.insertEntity[Abotyp, AbotypId](typ)
    }
  }

  def createDepotlieferungVertriebsart(meta: EventMetadata, id: VertriebsartId, vertriebsart: DepotlieferungAbotypModify)(implicit userId: UserId = meta.originator) = {
    val insert = copyTo[DepotlieferungAbotypModify, Depotlieferung](vertriebsart, "id" -> id,
      "erstelldat" -> meta.timestamp,
      "ersteller" -> meta.originator,
      "modifidat" -> meta.timestamp,
      "modifikator" -> meta.originator)

    DB autoCommit { implicit session =>
      writeRepository.insertEntity[Depotlieferung, VertriebsartId](insert)
    }
  }

  def createHeimlieferungVertriebsart(meta: EventMetadata, id: VertriebsartId, vertriebsart: HeimlieferungAbotypModify)(implicit userId: UserId = meta.originator) = {
    val insert = copyTo[HeimlieferungAbotypModify, Heimlieferung](vertriebsart, "id" -> id,
      "erstelldat" -> meta.timestamp,
      "ersteller" -> meta.originator,
      "modifidat" -> meta.timestamp,
      "modifikator" -> meta.originator)

    DB autoCommit { implicit session =>
      writeRepository.insertEntity[Heimlieferung, VertriebsartId](insert)
    }
  }

  def createPostlieferungVertriebsart(meta: EventMetadata, id: VertriebsartId, vertriebsart: PostlieferungAbotypModify)(implicit userId: UserId = meta.originator) = {
    val insert = copyTo[PostlieferungAbotypModify, Postlieferung](vertriebsart, "id" -> id,
      "erstelldat" -> meta.timestamp,
      "ersteller" -> meta.originator,
      "modifidat" -> meta.timestamp,
      "modifikator" -> meta.originator)

    DB autoCommit { implicit session =>
      writeRepository.insertEntity[Postlieferung, VertriebsartId](insert)
    }
  }

  def createLieferung(meta: EventMetadata, id: LieferungId, lieferung: LieferungAbotypCreate)(implicit userId: UserId = meta.originator) = {
    val insert = copyTo[LieferungAbotypCreate, Lieferung](lieferung, "id" -> id,
      "anzahlAbwesenheiten" -> ZERO,
      "status" -> Offen,
      "lieferplanungId" -> None,
      "lieferplanungNr" -> None,
      "erstelldat" -> meta.timestamp,
      "ersteller" -> meta.originator,
      "modifidat" -> meta.timestamp,
      "modifikator" -> meta.originator)

    DB autoCommit { implicit session =>
      //create lieferung
      writeRepository.insertEntity[Lieferung, LieferungId](insert)
    }
  }

  def createLieferplanung(id: UUID, lieferplanung: LieferplanungCreate) = {
    val lieferplanungId = LieferplanungId(id)
    val insert = readRepository.getLatestLieferplanung map {
      case Some(latestLP) => {
        val newNr = latestLP.nr + 1
        val lp = copyTo[LieferplanungCreate, Lieferplanung](lieferplanung, "id" -> lieferplanungId,
          "nr" -> newNr)
        (lp, newNr)
      }
      case None => {
        val firstNr = 1
        val lp = copyTo[LieferplanungCreate, Lieferplanung](lieferplanung, "id" -> lieferplanungId,
          "nr" -> firstNr)
        (lp, firstNr)
      }
    }
    insert map {
      _ match {
        case (obj: Lieferplanung, nr: Int) =>
          DB autoCommit { implicit session =>
            //create lieferplanung
            writeRepository.insertEntity(obj)
          }
          //alle nächsten Lieferungen alle Abotypen (wenn Flag es erlaubt)
          readRepository.getLieferungenNext() map {
            _ foreach {
              lieferung =>                  
                val lObj = copyTo[Lieferung, Lieferung](lieferung //"lieferplanungId" -> Some(lieferplanungId),
                  //"lieferplanungNr" -> Some(nr)
                  )
                DB autoCommit { implicit session =>
                  //create lieferplanung
                  writeRepository.updateEntity[Lieferung, LieferungId](lObj)
                }

            }
          }
      }
    }
  }

  def createBestellungen(id: UUID, create: BestellungenCreate) = {
    DB autoCommit { implicit session =>
      //delete all Bestellpositionen from Bestellungen (Bestellungen are maintained even if nothing is added)
      readRepository.getBestellpositionenByLieferplan(create.lieferplanungId) foreach {
        _ foreach {
          position => writeRepository.deleteEntity[Bestellposition, BestellpositionId](position.id)
        }
      }
      //fetch corresponding Lieferungen and generate Bestellungen
      val newBs = collection.mutable.Map[Tuple3[ProduzentId, LieferplanungId, DateTime], Bestellung]()
      val newBPs = collection.mutable.Map[Tuple2[BestellungId, ProduktId], Bestellposition]()
      readRepository.getLieferplanung(create.lieferplanungId) map {
        case Some(lieferplanung) =>
          readRepository.getLieferpositionenByLieferplan(create.lieferplanungId) map {
            _ map {
              lieferposition =>
                {
                  writeRepository.getById(lieferungMapping, lieferposition.lieferungId) map { lieferung =>
                    // enhance or create bestellung by produzentT
                    newBs.get((lieferposition.produzentId, create.lieferplanungId, lieferung.datum)) match {
                      case None => {
                        //create bestellung
                        val bestellung = Bestellung(BestellungId(UUID.randomUUID()), lieferposition.produzentId, lieferposition.produzentKurzzeichen, lieferplanung.id, lieferplanung.nr, lieferung.datum, None, 0)
                        newBs += (lieferposition.produzentId, create.lieferplanungId, lieferung.datum) -> bestellung
                      }
                    }

                    newBs.get((lieferposition.produzentId, create.lieferplanungId, lieferung.datum)) map { bestellung =>
                      //fetch or create bestellposition by produkt
                      val bp = newBPs.get((bestellung.id, lieferposition.produktId)) match {
                        case Some(existingBP) => {
                          val newMenge = existingBP.menge + lieferposition.menge.get
                          val newPreis = existingBP.preis |+| lieferposition.preis
                          val newAnzahl = existingBP.anzahl + lieferposition.anzahl
                          val copyBP = copyTo[Bestellposition, Bestellposition](existingBP,
                            "menge" -> newMenge,
                            "preis" -> newPreis,
                            "anzahl" -> newAnzahl)
                          copyBP
                        }
                        case None => {
                          //create bestellposition
                          val bestellposition = Bestellposition(BestellpositionId(UUID.randomUUID()),
                            bestellung.id,
                            lieferposition.produktId,
                            lieferposition.produktBeschrieb,
                            lieferposition.preisEinheit,
                            lieferposition.einheit,
                            lieferposition.menge.get,
                            lieferposition.preis,
                            lieferposition.anzahl)
                          bestellposition
                        }
                      }
                      newBPs += (bestellung.id, lieferposition.produktId) -> bp
                      val newPreisTotal = bp.preis.get |+| bestellung.preisTotal
                      val copyB = copyTo[Bestellung, Bestellung](bestellung,
                        "preisTotal" -> newPreisTotal)
                    }
                  }
                }
            }

          }

          //jetzt die neuen objekte kreieren
          newBs foreach {
            case (_, bestellung) =>
              writeRepository.insertEntity(bestellung)
          }
          newBPs foreach {
            case (_, bestellposition) =>
              writeRepository.insertEntity(bestellposition)
          }
      }
    }
  }

  def createKunde(meta: EventMetadata, id: KundeId, create: KundeModify)(implicit userId: UserId = meta.originator) = {
    val bez = create.bezeichnung.getOrElse(create.ansprechpersonen.head.fullName)
    val kunde = copyTo[KundeModify, Kunde](create,
      "id" -> id,
      "bezeichnung" -> bez,
      "anzahlPersonen" -> create.ansprechpersonen.length,
      "anzahlAbos" -> ZERO,
      "anzahlPendenzen" -> ZERO,
      "erstelldat" -> meta.timestamp,
      "ersteller" -> meta.originator,
      "modifidat" -> meta.timestamp,
      "modifikator" -> meta.originator)
    DB autoCommit { implicit session =>
      //create abotyp
      writeRepository.insertEntity[Kunde, KundeId](kunde)
    }
  }

  def createPerson(meta: EventMetadata, id: PersonId, create: PersonModify, kundeId: KundeId, sort: Int)(implicit userId: UserId = meta.originator) = {

    val person = copyTo[PersonModify, Person](create, "id" -> id,
      "kundeId" -> kundeId,
      "sort" -> sort,
      "erstelldat" -> meta.timestamp,
      "ersteller" -> meta.originator,
      "modifidat" -> meta.timestamp,
      "modifikator" -> meta.originator)

    DB autoCommit { implicit session =>
      writeRepository.insertEntity[Person, PersonId](person)
    }
  }

  def createPendenz(meta: EventMetadata, id: PendenzId, create: PendenzModify, kundeId: KundeId, kundeBezeichnung: String)(implicit userId: UserId = meta.originator) = {
    val pendenz = copyTo[PendenzModify, Pendenz](create, "id" -> id,
      "kundeId" -> kundeId, 
      "kundeBezeichnung" -> kundeBezeichnung,
      "erstelldat" -> meta.timestamp,
      "ersteller" -> meta.originator,
      "modifidat" -> meta.timestamp,
      "modifikator" -> meta.originator)

    DB autoCommit { implicit session =>
      writeRepository.insertEntity[Pendenz, PendenzId](pendenz)
    }
  }

  def createDepot(meta: EventMetadata, id: DepotId, create: DepotModify)(implicit userId: UserId = meta.originator) = {
    val depot = copyTo[DepotModify, Depot](create,
      "id" -> id,
      "farbCode" -> "",
      "anzahlAbonnenten" -> ZERO,
      "erstelldat" -> meta.timestamp,
      "ersteller" -> meta.originator,
      "modifidat" -> meta.timestamp,
      "modifikator" -> meta.originator)
    DB autoCommit { implicit session =>
      writeRepository.insertEntity[Depot, DepotId](depot)
    }
  }

  def createAbo(meta: EventMetadata, id: AboId, create: AboModify)(implicit userId: UserId = meta.originator) = {
    DB autoCommit { implicit session =>
      val abo = create match {
        case create: DepotlieferungAboModify =>
          writeRepository.insertEntity[DepotlieferungAbo, AboId](copyTo[DepotlieferungAboModify, DepotlieferungAbo](create,
            "id" -> id, "saldo" -> ZERO,
            "erstelldat" -> meta.timestamp,
            "ersteller" -> meta.originator,
            "modifidat" -> meta.timestamp,
            "modifikator" -> meta.originator))
        case create: HeimlieferungAboModify =>
          writeRepository.insertEntity[HeimlieferungAbo, AboId](copyTo[HeimlieferungAboModify, HeimlieferungAbo](create,
            "id" -> id, "saldo" -> ZERO,
            "erstelldat" -> meta.timestamp,
            "ersteller" -> meta.originator,
            "modifidat" -> meta.timestamp,
            "modifikator" -> meta.originator))
        case create: PostlieferungAboModify =>
          writeRepository.insertEntity[PostlieferungAbo, AboId](copyTo[PostlieferungAboModify, PostlieferungAbo](create,
            "id" -> id, "saldo" -> ZERO,
            "erstelldat" -> meta.timestamp,
            "ersteller" -> meta.originator,
            "modifidat" -> meta.timestamp,
            "modifikator" -> meta.originator))
      }
    }
  }

  def createKundentyp(meta: EventMetadata, id: CustomKundentypId, create: CustomKundentypCreate)(implicit userId: UserId = meta.originator) = {
    val kundentyp = copyTo[CustomKundentypCreate, CustomKundentyp](create,
      "id" -> id,
      "anzahlVerknuepfungen" -> ZERO,
      "erstelldat" -> meta.timestamp,
      "ersteller" -> meta.originator,
      "modifidat" -> meta.timestamp,
      "modifikator" -> meta.originator)
    DB autoCommit { implicit session =>
      writeRepository.insertEntity[CustomKundentyp, CustomKundentypId](kundentyp)
    }
  }

  def createProdukt(meta: EventMetadata, id: ProduktId, create: ProduktModify)(implicit userId: UserId = meta.originator) = {
    val produkt = copyTo[ProduktModify, Produkt](create,
      "id" -> id,
      "erstelldat" -> meta.timestamp,
      "ersteller" -> meta.originator,
      "modifidat" -> meta.timestamp,
      "modifikator" -> meta.originator)
    DB autoCommit { implicit session =>
      writeRepository.insertEntity[Produkt, ProduktId](produkt)
    }
  }

  def createProduktekategorie(meta: EventMetadata, id: ProduktekategorieId, create: ProduktekategorieModify)(implicit userId: UserId = meta.originator) = {
    val produktekategrie = copyTo[ProduktekategorieModify, Produktekategorie](create,
      "id" -> id,
      "erstelldat" -> meta.timestamp,
      "ersteller" -> meta.originator,
      "modifidat" -> meta.timestamp,
      "modifikator" -> meta.originator)
    DB autoCommit { implicit session =>
      writeRepository.insertEntity[Produktekategorie, ProduktekategorieId](produktekategrie)
    }
  }

  def createProduzent(meta: EventMetadata, id: ProduzentId, create: ProduzentModify)(implicit userId: UserId = meta.originator) = {
    val produzent = copyTo[ProduzentModify, Produzent](create,
      "id" -> id,
      "erstelldat" -> meta.timestamp,
      "ersteller" -> meta.originator,
      "modifidat" -> meta.timestamp,
      "modifikator" -> meta.originator)
    DB autoCommit { implicit session =>
      writeRepository.insertEntity[Produzent, ProduzentId](produzent)
    }
  }

  def createTour(meta: EventMetadata, id: TourId, create: TourModify)(implicit userId: UserId = meta.originator) = {
    val tour = copyTo[TourModify, Tour](create,
      "id" -> id,
      "erstelldat" -> meta.timestamp,
      "ersteller" -> meta.originator,
      "modifidat" -> meta.timestamp,
      "modifikator" -> meta.originator)
    DB autoCommit { implicit session =>
      writeRepository.insertEntity[Tour, TourId](tour)
    }
  }

  def createProjekt(meta: EventMetadata, id: ProjektId, create: ProjektModify)(implicit userId: UserId = meta.originator) = {
    val projekt = copyTo[ProjektModify, Projekt](create,
      "id" -> id,
      "erstelldat" -> meta.timestamp,
      "ersteller" -> meta.originator,
      "modifidat" -> meta.timestamp,
      "modifikator" -> meta.originator)
    DB autoCommit { implicit session =>
      writeRepository.insertEntity[Projekt, ProjektId](projekt)
    }
  }
}