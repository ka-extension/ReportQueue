package models

import scala.util.matching.Regex

trait Reporter {
  val pattern: Regex

  protected val extractor: Regex
  def transform(id: String): Option[String]

  def valid(id: String): Boolean = pattern.pattern.matcher(id).matches
  def extractFromTransformed(transformedId: String): Option[String] = transformedId match {
    case extractor(id) => Some(id)
    case _ => None
  }
  def idsAreSame(id: String, transformedId: String): Boolean = extractFromTransformed(transformedId).contains(id)
}