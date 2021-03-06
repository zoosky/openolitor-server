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
package ch.openolitor.buchhaltung

import org.specs2.mutable._
import ch.openolitor.stammdaten.models._
import ch.openolitor.buchhaltung.models._
import org.joda.time.DateTime
import ch.openolitor.core.MandantConfiguration
import ch.openolitor.core.SystemConfig
import akka.actor.ActorSystem

class BuchhaltungInsertServiceSpec extends Specification {
  "BuchhaltungInsertService" should {
    val config = SystemConfig(MandantConfiguration(
      "", "", "", 0, 0, Map(), null
    ), null, null)

    val service = new MockBuchhaltungInsertService(config, null, 6, 5, "777777777", "123456")

    "calculate correct checksum according to definition matrix" in {
      service.calculateChecksum("00000001290381204712347234".toList map (_.asDigit)) === 0
    }

    "calculate correct checksum according to definition matrix" in {
      service.calculateChecksum("00000001290381204712347230".toList map (_.asDigit)) === 3
    }

    "calculate correct checksum according to definition matrix" in {
      service.calculateChecksum("00000001290381204712347233".toList map (_.asDigit)) === 5
    }

    "calculate correct checksum according to definition matrix" in {
      service.calculateChecksum("00000001290381204712347232".toList map (_.asDigit)) === 7
    }

    "calculate correct checksum according to definition matrix" in {
      service.calculateChecksum("00000001290381204712347231".toList map (_.asDigit)) === 9
    }

    "calculate correct referenzNummer" in {
      val rechnung = RechnungCreate(
        KundeId(123),
        AboId(111),
        "titel",
        3,
        CHF,
        20.5,
        None,
        new DateTime,
        new DateTime,
        None,
        "street",
        None,
        None,
        "3000",
        "Bern"
      )

      service.generateReferenzNummer(rechnung, RechnungId(777)) === "123456000000000000001237773"
    }

    "calculate correct esrNummer" in {
      val rechnung = RechnungCreate(
        KundeId(321),
        AboId(565656),
        "titel",
        5,
        CHF,
        20.57,
        None,
        new DateTime,
        new DateTime,
        None,
        "street",
        None,
        None,
        "3000",
        "Bern"
      )

      val referenzNummer = service.generateReferenzNummer(rechnung, RechnungId(555))
      service.generateEsrNummer(rechnung, referenzNummer) === "0100000020573>123456000000000000003215558+ 777777777>"
    }
  }

  "BuchhaltungInsertService" should {
    val config = SystemConfig(MandantConfiguration(
      "", "", "", 0, 0, Map(), null
    ), null, null)

    val service = new MockBuchhaltungInsertService(config, null, 6, 5, "132", "")

    "fill teilnehmernummer from right" in {
      val rechnung = RechnungCreate(
        KundeId(321),
        AboId(565656),
        "titel",
        5,
        CHF,
        20.57,
        None,
        new DateTime,
        new DateTime,
        None,
        "street",
        None,
        None,
        "3000",
        "Bern"
      )

      val referenzNummer = service.generateReferenzNummer(rechnung, RechnungId(555))
      service.generateEsrNummer(rechnung, referenzNummer) === "0100000020573>000000000000000000003215552+ 000000132>"
    }
  }
}

class MockBuchhaltungInsertService(sysConfig: SystemConfig, override val system: ActorSystem, RechnungIdLengthP: Int,
    KundeIdLengthP: Int, TeilnehmernummerP: String, ReferenznummerPrefixP: String) extends DefaultBuchhaltungInsertService(sysConfig, system) {
  override lazy val RechnungIdLength = RechnungIdLengthP
  override lazy val KundeIdLength = KundeIdLengthP
  override lazy val Teilnehmernummer = TeilnehmernummerP
  override lazy val ReferenznummerPrefix = ReferenznummerPrefixP
}
