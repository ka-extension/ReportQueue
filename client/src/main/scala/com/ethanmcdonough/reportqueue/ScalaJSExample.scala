package com.ethanmcdonough.reportqueue

import com.ethanmcdonough.reportqueue.shared.SharedMessages
import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{ Success, Failure }
import org.scalajs.dom.raw.HTMLDocument

object ScalaJSExample {

  def main(args: Array[String]): Unit = {
    dom.document.getElementById("scalajsShoutOut").textContent = SharedMessages.itWorks
    Ajax.get(url = "/", responseType = "document") onComplete { 
      case Success(xhr) => println(xhr.response.asInstanceOf[HTMLDocument].body.innerHTML)
      case Failure(err) => println(err)
    }
  }
}
