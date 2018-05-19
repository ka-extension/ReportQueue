package com.ethanmcdonough.reportqueue

import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalajs.dom.raw.{HTMLFormElement, HTMLInputElement, HTMLParagraphElement, HTMLSelectElement, HTMLSpanElement, Event}
import scala.scalajs.js.URIUtils.decodeURIComponent
import scala.scalajs.js.{JSON, Dynamic}
import scala.scalajs.js.Dynamic.{global => g}

object Main {
  private val regexes: Map[String, String] = Map(
    "program" -> raw"\d{5,20}", "user" -> raw"kaid_\d{10,30}",
    "discussion" -> raw"([a-zA-Z]+)\|([a-zA-Z]+)\|([a-zA-Z\d]+)\|(kaencrypted_[\w]+)")
  private def getCSRF: String = dom.document.cookie.split(";").map(_.split("=").map(e => decodeURIComponent(e.trim))).filter(_(0) == "ftok").head(1)
  def main(args: Array[String]): Unit = {
    val form: HTMLFormElement = dom.document.getElementById("report-form").asInstanceOf[HTMLFormElement]
    val idEl: HTMLInputElement = dom.document.getElementById("id").asInstanceOf[HTMLInputElement]
    val errorEl: HTMLParagraphElement = dom.document.getElementById("error").asInstanceOf[HTMLParagraphElement]
    val successEl: HTMLParagraphElement = dom.document.getElementById("success").asInstanceOf[HTMLParagraphElement]
    val reasonEl: HTMLInputElement = dom.document.getElementById("reason").asInstanceOf[HTMLInputElement]
    val typeEl: HTMLSelectElement = dom.document.getElementById("report-type").asInstanceOf[HTMLSelectElement]
    val submitEl: HTMLInputElement = dom.document.getElementById("submit-button").asInstanceOf[HTMLInputElement]
    val redirectUrl: String = dom.document.getElementById("redirect").asInstanceOf[HTMLSpanElement]
      .getAttribute("data-redir")

    typeEl.addEventListener("input", (e: Event) => {
      regexes.get(typeEl.value) foreach { regex =>
        idEl.setAttribute("pattern", regex)
        idEl.parentElement.classList.remove("is-invalid")
        idEl.parentElement.classList.remove("is-upgraded")
        idEl.parentElement.removeAttribute("data-upgraded")
        g.componentHandler.upgradeDom()
      }
    })

    form.addEventListener("submit", (e: Event) => {
      e.preventDefault
      successEl.textContent = ""
      errorEl.textContent = ""
      submitEl.disabled = true
      val route = g.jsRoutes.com.ethanmcdonough.reportqueue.controllers.MainController.report(typeEl.value)
      Ajax(
        route.method.toString, route.url.toString,
        JSON.stringify(Dynamic.literal("reason" -> reasonEl.value, "id" -> idEl.value)),
        0, Map("x-ftok" -> getCSRF, "Content-Type" -> "application/json"), true, "").recover {
          case dom.ext.AjaxException(x) => x
        }.map(x => {
          if (x.status >= 200 && x.status < 300) {
            successEl.textContent = JSON.parse(x.responseText).message.toString
            if (redirectUrl.matches(raw"https:\/\/(?:(?:www|[a-z]{2})\.)?khanacademy\.org.*"))
              dom.window.location.replace(redirectUrl)
          } else
            errorEl.textContent = JSON.parse(x.responseText).message.toString
          submitEl.disabled = false
        })
    })
  }
}
