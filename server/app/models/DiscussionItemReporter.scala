package models
import scala.util.matching.Regex
import scalaj.http.Http
import play.api.libs.json.Json

object DiscussionItemReporter extends Reporter {
  override val pattern: Regex = raw"([a-zA-Z]+)\|([a-zA-Z]+)\|([a-zA-Z\d]+)\|(kaencrypted_[\w]+)".r
  override protected val extractor: Regex =
    raw"(?i)https?:\/\/(?:www|[a-z])\.khanacademy\.org.*?\?qa_expand_key=(kaencrypted_[\w]+)".r

  override def transform(id: String): Option[String] = id match {
    case pattern(discussionType, focusKind, focusId, expandKey) =>
      Option(s"https://www.khanacademy.org/api/internal/discussions/$focusKind/$focusId/$discussionType").map{e => println(e); e}
      .map(Http.apply).map(_.params(
        "casing" -> "camel", "projection" -> """{"focus":{"relativeUrl":1},"feedback":[{"normal":{"key":1}}]}""",
        "qa_expand_key" -> expandKey, "sort" -> "1", "subject" -> "all", "limit" -> "1", "page" -> "0", "lang" -> "en",
        "_" -> System.currentTimeMillis.toString
      )).map(_.asString).map{e => println(e.body); e}.filter(r => r.code >= 200 && r.code < 300).map(_.body).map(Json.parse).flatMap(body => for {
        url <- (body \ "focus" \ "relativeUrl").asOpt[String]
        key <- (body \ "feedback" \ 0 \ "key").asOpt[String]
        if key == expandKey
      } yield s"https://www.khanacademy.org$url?qa_expand_key=$key")
    case _ => None
  }

  override def idsAreSame(id: String, transformedId: String): Boolean = id match {
    case pattern(_, _, _, expandKey) =>
      super.idsAreSame(expandKey, transformedId)
    case _ => false
  }
}
