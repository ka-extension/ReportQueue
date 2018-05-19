package ioutil

import java.io.Closeable

object IOUtil {
  def using[A <: Closeable, R](r: A)(callback: A => R): R = try {
    callback(r)
  } finally {
    r.close()
  }
}