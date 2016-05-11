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
package ch.openolitor.core.db.evolution

import scalikejdbc._
import scalikejdbc.SQLSyntax._
import ch.openolitor.core.repositories.CoreDBMappings
import scala.util._
import ch.openolitor.core.models._
import com.typesafe.scalalogging.LazyLogging
import ch.openolitor.core.db.evolution.scripts.V1Scripts
import ch.openolitor.util.IteratorUtil
import org.joda.time.DateTime
import ch.openolitor.core.repositories.BaseEntitySQLSyntaxSupport
import ch.openolitor.stammdaten.StammdatenDBMappings
import ch.openolitor.stammdaten.models._
import ch.openolitor.buchhaltung.models._
import ch.openolitor.core.repositories.SqlBinder
import scala.reflect._
import ch.openolitor.buchhaltung.BuchhaltungDBMappings

trait Script {
  def execute(implicit session: DBSession): Try[Boolean]
}

case class EvolutionException(msg: String) extends Exception

object Evolution extends Evolution(V1Scripts.scripts)

/**
 * Base evolution class to evolve database from a specific revision to another
 */
class Evolution(scripts: Seq[Script]) extends CoreDBMappings with LazyLogging with StammdatenDBMappings with BuchhaltungDBMappings {
  import IteratorUtil._

  logger.debug(s"Evolution manager consists of:$scripts")

  def checkDBSeeds(seeds: Map[Class[_ <: BaseId], Long])(implicit cpContext: ConnectionPoolContext, userId: UserId): Try[Map[Class[_ <: BaseId], Long]] = {
    DB readOnly { implicit session =>
      try {
        val dbIds = Seq(
          adjustSeed[Abotyp, AbotypId](seeds, abotypMapping),
          adjustSeed[Depot, DepotId](seeds, depotMapping),
          adjustSeeds[VertriebsartId](
            seeds,
            maxId[Depotlieferung, VertriebsartId](depotlieferungMapping),
            maxId[Heimlieferung, VertriebsartId](heimlieferungMapping),
            maxId[Postlieferung, VertriebsartId](postlieferungMapping)
          ),
          adjustSeeds[AboId](
            seeds,
            maxId[DepotlieferungAbo, AboId](depotlieferungAboMapping),
            maxId[HeimlieferungAbo, AboId](heimlieferungAboMapping),
            maxId[PostlieferungAbo, AboId](postlieferungAboMapping)
          ),
          adjustSeed[Kunde, KundeId](seeds, kundeMapping),
          adjustSeed[CustomKundentyp, CustomKundentypId](seeds, customKundentypMapping),
          adjustSeed[Lieferung, LieferungId](seeds, lieferungMapping),
          adjustSeed[Pendenz, PendenzId](seeds, pendenzMapping),
          adjustSeed[Person, PersonId](seeds, personMapping),
          adjustSeed[Produzent, ProduzentId](seeds, produzentMapping),
          adjustSeed[Produkt, ProduktId](seeds, produktMapping),
          adjustSeed[ProduktProduktekategorie, ProduktProduktekategorieId](seeds, produktProduktekategorieMapping),
          adjustSeed[ProduktProduzent, ProduktProduzentId](seeds, produktProduzentMapping),
          adjustSeed[Produktekategorie, ProduktekategorieId](seeds, produktekategorieMapping),
          adjustSeed[Projekt, ProjektId](seeds, projektMapping),
          adjustSeed[Tour, TourId](seeds, tourMapping),
          adjustSeed[Lieferplanung, LieferplanungId](seeds, lieferplanungMapping),
          adjustSeed[Lieferposition, LieferpositionId](seeds, lieferpositionMapping),
          adjustSeed[Bestellposition, BestellpositionId](seeds, bestellpositionMapping),
          adjustSeed[Abwesenheit, AbwesenheitId](seeds, abwesenheitMapping),
          adjustSeed[Rechnung, RechnungId](seeds, rechnungMapping),
          adjustSeed[ZahlungsImport, ZahlungsImportId](seeds, zahlungsImportMapping),
          adjustSeed[ZahlungsEingang, ZahlungsEingangId](seeds, zahlungsEingangMapping)
        ).flatten

        Success(seeds ++ dbIds.toMap)
      } catch {
        case t: Throwable =>
          Failure(t)
      }
    }
  }

  def adjustSeed[E <: BaseEntity[I], I <: BaseId: ClassTag](seeds: Map[Class[_ <: BaseId], Long], syntax: BaseEntitySQLSyntaxSupport[E])(implicit session: DBSession, userId: UserId): Option[(Class[I], Long)] = {
    adjustSeeds(seeds, maxId[E, I](syntax))
  }

  def adjustSeeds[I <: BaseId: ClassTag](seeds: Map[Class[_ <: BaseId], Long], queries: Option[Long]*)(implicit session: DBSession, userId: UserId): Option[(Class[I], Long)] = {
    val entity: Class[I] = classTag[I].runtimeClass.asInstanceOf[Class[I]]
    val q = queries.flatten
    val overallMaxId = if (q.length > 0) q.max else 0
    seeds.get(entity).map(_ < overallMaxId).getOrElse(true) match {
      case true => Some(entity -> overallMaxId)
      case _ => None
    }
  }

  def maxId[E <: BaseEntity[I], I <: BaseId](syntax: BaseEntitySQLSyntaxSupport[E])(implicit session: DBSession, userId: UserId): Option[Long] = {
    val alias = syntax.syntax("x")
    val idx = alias.id
    withSQL {
      select(max(idx))
        .from(syntax as alias)
    }.map(_.longOpt(1)).single.apply().getOrElse(None)
  }

  def evolveDatabase(fromRevision: Int = 0)(implicit cpContext: ConnectionPoolContext, userId: UserId): Try[Int] = {
    val currentDBRevision = DB readOnly { implicit session => currentRevision }
    val revision = Math.max(fromRevision, currentDBRevision)
    scripts.take(scripts.length - revision) match {
      case Nil => Success(revision)
      case scriptsToApply => evolve(scriptsToApply, revision)
    }
  }

  def evolve(scripts: Seq[Script], currentRevision: Int)(implicit cpContext: ConnectionPoolContext, userId: UserId): Try[Int] = {
    logger.debug(s"evolve database from:$currentRevision")
    val x = scripts.zipWithIndex.view.map {
      case (script, index) =>
        try {
          logger.debug(s"evolve script:$script [$index]")
          DB localTx { implicit session =>
            script.execute match {
              case Success(x) =>
                val rev = currentRevision + index + 1
                insertRevision(rev) match {
                  case true => Success(rev)
                  case false =>
                    //fail
                    throw EvolutionException(s"Couldn't register new db schema")
                }
              case Failure(e) =>
                //failed throw e to rollback transaction
                throw e
            }
          }
        } catch {
          case e: Exception =>
            logger.warn(s"catched exception:", e)
            Failure(e)
        }
    }.toIterator.takeWhileInclusive(_.isSuccess).toSeq

    logger.debug(s"Evolved:$x:${x.reverse.headOption.getOrElse("xxx")}")
    x.reverse.headOption.getOrElse(Failure(EvolutionException(s"No Script found")))
  }

  def insertRevision(revision: Int)(implicit session: DBSession, userId: UserId): Boolean = {
    val entity = DBSchema(DBSchemaId(), revision, Done, DateTime.now, userId, DateTime.now, userId)
    val params = dbSchemaMapping.parameterMappings(entity)
    withSQL(insertInto(dbSchemaMapping).values(params: _*)).update.apply() == 1
  }

  lazy val schema = dbSchemaMapping.syntax("db_schema")

  /**
   * load current revision from database schema
   */
  def currentRevision(implicit session: DBSession): Int = {
    withSQL {
      select(max(schema.revision))
        .from(dbSchemaMapping as schema)
        .where.eq(schema.status, parameter(Applying))
    }.map(_.intOpt(1).getOrElse(0)).single.apply().getOrElse(0)
  }

  def revisions(implicit session: DBSession): List[DBSchema] = {
    withSQL {
      select
        .from(dbSchemaMapping as schema)
        .where.eq(schema.status, parameter(Applying))
    }.map(dbSchemaMapping(schema)).list.apply()
  }
}