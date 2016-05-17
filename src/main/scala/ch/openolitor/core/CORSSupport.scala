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

import spray.http.{ HttpMethods, HttpMethod, HttpResponse, AllOrigins, HttpOrigin, SomeOrigins }
import spray.http.HttpHeaders._
import spray.http.HttpMethods._
import spray.routing._
import com.typesafe.scalalogging.LazyLogging
import ch.openolitor.util.ConfigUtil._
import com.typesafe.config.Config

// see also https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS
trait CORSSupport extends LazyLogging {
  this: HttpService =>

  val ooConfig: Config

  lazy val allowOrigin = ooConfig.getStringListOption(s"security.cors.allow-origin").map(list => SomeOrigins(list.map(HttpOrigin.apply))).getOrElse(AllOrigins)

  private val allowOriginHeader = `Access-Control-Allow-Origin`(allowOrigin)
  val allowCredentialsHeader = `Access-Control-Allow-Credentials`(true)
  val allowHeaders = `Access-Control-Allow-Headers`("Origin, X-Requested-With, Content-Type, Accept, Accept-Encoding, Accept-Language, Host, Referer, User-Agent, OO-XSRF-TOKEN")
  private val optionsCorsHeaders = List(
    allowHeaders,
    `Access-Control-Max-Age`(1728000)
  )
  logger.debug(s"$this:allowOriginHeader:$allowOriginHeader")

  def corsDirective[T]: Directive0 = mapRequestContext { ctx =>
    ctx.withRouteResponseHandling({
      //It is an option requeset for a resource that responds to some other method
      case Rejected(x) if (ctx.request.method.equals(HttpMethods.OPTIONS) && !x.filter(_.isInstanceOf[MethodRejection]).isEmpty) => {
        val allowedMethods: List[HttpMethod] = x.filter(_.isInstanceOf[MethodRejection]).map(rejection => {
          rejection.asInstanceOf[MethodRejection].supported
        })
        logger.debug(s"Got cors request:${ctx.request.uri}:$x:$allowedMethods")
        ctx.complete(HttpResponse().withHeaders(
          `Access-Control-Allow-Methods`(OPTIONS, allowedMethods: _*) :: allowCredentialsHeader :: allowOriginHeader ::
            optionsCorsHeaders
        ))
      }
    }).withHttpResponseHeadersMapped { headers =>
      allowCredentialsHeader :: allowOriginHeader :: headers
    }
  }

  private def preflightRequestHandler: Route = options {
    complete(HttpResponse(200).withHeaders(
      `Access-Control-Allow-Methods`(OPTIONS, POST, PUT, GET, DELETE) ::
        allowCredentialsHeader :: allowOriginHeader :: allowHeaders :: Nil
    ))
  }

  def cors(r: Route) = preflightRequestHandler ~ corsDirective {
    r
  }
}