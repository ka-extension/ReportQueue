package models

import scalaj.http.Http
import scala.util.matching.Regex
import play.api.libs.json.Json

object ProgramReporter extends Reporter {
  val pattern: Regex = raw"(\d{7,20})".r
  protected val extractor: Regex = raw"https?:\/\/(?:[a-zA-Z]{2,3}\.)?khanacademy\.org\/[a-zA-Z\-]+\/.*?\/(\d{7,20})".r
  def transform(id: String): Option[String] = Option(id).filter(pattern.pattern.matcher(_).matches).map("https://www.khanacademy.org/api/labs/scratchpads/" + _).map(Http(_).param("projection", """{"url":1}""").asString).filter(r => r.code >= 200 && r.code < 300).map(_.body).map(Json.parse(_)).map(_ \ "url").flatMap(_.asOpt[String])
}