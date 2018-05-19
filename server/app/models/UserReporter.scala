package models

import scalaj.http.Http
import scala.util.matching.Regex
import play.api.libs.json.Json

object UserReporter extends Reporter {
  val pattern: Regex = raw"(kaid_\d{20,30})".r
  protected val extractor: Regex = raw"https://www.khanacademy.org/profile/(kaid_\d{20,30})".r
  def transform(id: String): Option[String] = Option(id).filter(pattern.pattern.matcher(_).matches)
    .map(e => Http("https://www.khanacademy.org/api/internal/user/profile")
      .params("kaid" -> e, "projection" -> """{"kaid":1}""").asString)
    .filter(r => r.code >= 200 && r.code < 300).map(_.body).map(Json.parse)
    .map(_ \ "kaid").flatMap(_.asOpt[String]).map("https://www.khanacademy.org/profile/" + _)
}