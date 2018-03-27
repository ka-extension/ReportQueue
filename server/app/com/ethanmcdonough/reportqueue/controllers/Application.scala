package com.ethanmcdonough.reportqueue.controllers

import javax.inject._
import routes.Application.index
import com.ethanmcdonough.reportqueue.shared.SharedMessages
import play.api.mvc._

@Singleton
class Application @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  def index = Action { request: Request[AnyContent] =>
    Ok(views.html.index(SharedMessages.itWorks + " " + routes.Application.index.absoluteURL(request.secure)(request)))
  }

}
