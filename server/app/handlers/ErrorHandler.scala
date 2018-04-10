package handlers

import javax.inject._

import play.api.http.DefaultHttpErrorHandler
import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.routing.Router
import scala.concurrent._

@Singleton
class ErrorHandler @Inject() (
  env:          Environment,
  config:       Configuration,
  sourceMapper: OptionalSourceMapper,
  router:       Provider[Router]) extends DefaultHttpErrorHandler(env, config, sourceMapper, router) {

  override def onProdServerError(request: RequestHeader, exception: UsefulException) = Future.successful(InternalServerError(views.html.internalservererror.render))

  override def onNotFound(request: RequestHeader, message: String) = Future.successful(
    if (env.mode == Mode.Prod)
      NotFound(views.html.notfound.render)
    else
      NotFound(views.html.defaultpages.devNotFound.render(request.method, request.uri, Some(router.get))))
}