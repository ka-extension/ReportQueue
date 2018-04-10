package ioutil

import java.io.{ Closeable, File }
import scala.io.Source

import play.api.libs.json._
import com.fasterxml.jackson.databind.JsonNode

object IOUtil {
  def using[A <: Closeable, R](r: A)(callback: A => R): R = try {
    callback(r)
  } finally {
    r.close
  }
}