package com.ethanmcdonough.reportqueue.controllers

import javax.inject._
import java.util.Date
import java.net.URLEncoder
import java.io.File

import models.{User, DataInserter, UserReporter, ProgramReporter, DiscussionItemReporter}

import play.api.routing.JavaScriptReverseRouter
import play.api.mvc._
import play.api.{Environment, Configuration}
import play.api.libs.json.{JsValue, Json}
import play.Logger

import play.api.libs.concurrent.CustomExecutionContext
import akka.actor.ActorSystem

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try, Failure}

import scalaj.http.{Token, Http}

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpTransport

trait HttpContext extends ExecutionContext
class HttpContextImpl @Inject() (system: ActorSystem) extends CustomExecutionContext(system, "http-context")
  with HttpContext

class MainController @Inject() (context: ExecutionContext, httpContext: HttpContextImpl, cc: ControllerComponents,
                                env: Environment, config: Configuration) extends AbstractController(cc) {

  private def buildSimpleQuery(params: Tuple2[String, String]*): String = params
    .map { e => s"${URLEncoder.encode(e._1, "UTF-8")}=${URLEncoder.encode(e._2, "UTF-8")}" }.mkString("&")

  private val consumer: Option[Token] = for {
    public <- config.get[Option[String]]("reportqueue.ka.api.public")
    secret <- config.get[Option[String]]("reportqueue.ka.api.secret")
  } yield Token(public, secret)

  private val httpTransport: HttpTransport = GoogleNetHttpTransport.newTrustedTransport
  private val dataStore: File = env.getFile("conf/credentials")
  private val clientSecret: File = env.getFile("conf/client-secret.json")

  private val userInserter: Option[DataInserter] = config.getOptional[String]("reportqueue.google.sheets.users")
    .map(DataInserter(_, dataStore, clientSecret, httpTransport, UserReporter))
  private val programInserter: Option[DataInserter] = config.getOptional[String]("reportqueue.google.sheets.programs")
    .map(DataInserter(_, dataStore, clientSecret, httpTransport, ProgramReporter))
  private val discussionInserter: Option[DataInserter] = config.getOptional[String]("reportqueue.google.sheets.discussion")
    .map(DataInserter(_, dataStore, clientSecret, httpTransport, DiscussionItemReporter))
  private val inserters: Map[String, Option[DataInserter]] = Map("user" -> userInserter, "program" -> programInserter,
    "discussion" -> discussionInserter)

  dataStore.mkdir

  def submit(): Action[AnyContent] = Action.async {
    implicit request =>
      (for {
        kaid <- request.session.get("kaid")
        name <- request.session.get("name")
      } yield Future {
        Ok(views.html.submit.render(
          User(kaid, name),
          request.getQueryString("type") getOrElse "",
          request.getQueryString("id") getOrElse "",
          request.getQueryString("callback") getOrElse ""))
      }(context)) getOrElse Future {
        consumer.map(Http("https://www.khanacademy.org/api/auth2/request_token")
          .postForm(Seq("oauth_callback" -> routes.MainController.oauthCallback().absoluteURL(request.secure)))
          .oauth(_).asToken).map("https://www.khanacademy.org/api/auth2/authorize?oauth_token=" + _.body.key)
          .map(Redirect(_).withSession(
            "callback-type" -> request.getQueryString("type").getOrElse(""),
            "callback-id" -> request.getQueryString("id").getOrElse(""),
            "callback-callback" -> request.getQueryString("callback").getOrElse(""))) getOrElse {
            if (consumer.isEmpty)
              Logger.error("Could not find reportqueue.ka.api.public and/or reportqueue.ka.api.secret")
            InternalServerError(views.html.internalservererror.render())
          }
      }(httpContext)
  }

  def oauthCallback(): Action[AnyContent] = Action.async {
    request =>
      (for {
        name <- request.session.get("kaid")
        kaid <- request.session.get("name")
      } yield Future {
        Redirect(routes.MainController.submit().url)
      }(context)) getOrElse Future {
        val access: Option[Token] = for {
          public <- request.getQueryString("oauth_token")
          secret <- request.getQueryString("oauth_token_secret")
          verifier <- request.getQueryString("oauth_verifier")
          consumer <- consumer
        } yield Http("https://www.khanacademy.org/api/auth2/access_token")
          .postForm.oauth(consumer, Token(public, secret), verifier).asToken.body

        val jsonResponse: Option[JsValue] = for {
          consumer <- consumer
          access <- access
        } yield Json.parse(Http("https://www.khanacademy.org/api/v1/user").oauth(consumer, access).asString.body)

        jsonResponse.map { json =>
          (for {
            kaid <- (json \ "kaid").asOpt[String]
            name <- (json \ "nickname").asOpt[String]
          } yield Redirect(s"${routes.MainController.submit().url}?${
            buildSimpleQuery(
              "type" -> request.session.get("callback-type").getOrElse(""),
              "id" -> request.session.get("callback-id").getOrElse(""),
              "callback" -> request.session.get("callback-callback").getOrElse(""))
          }").withSession("kaid" -> kaid, "name" -> name)) getOrElse {
            Logger.error("""Could not retrieve "kaid" and/or "nickname" from https://www.khanacademy.org/api/v1/user""")
            InternalServerError(views.html.internalservererror.render())
          }
        } getOrElse {
          if (consumer.isEmpty) {
            Logger.error("Could not find reportqueue.ka.api.public and/or reportqueue.ka.api.secret in application.conf")
            InternalServerError(views.html.internalservererror.render())
          } else
            BadRequest(views.html.badrequest.render())
        }
      }(httpContext)
  }

  def report(reportType: String): Action[AnyContent] = Action.async {
    request =>
      request.body.asJson.flatMap(body => for {
        id <- (body \ "id").asOpt[String]
        reason <- (body \ "reason").asOpt[String]
        inserter <- (inserters get reportType).flatten
        kaid <- request.session.get("kaid")
        name <- request.session.get("name")
      } yield Future {
        Try(inserter.insert(inserter.service, User(kaid, name), id, new Date, reason, request.remoteAddress) match {
          case Right(msg) => Ok(Json.toJson(Map("message" -> msg)))
          case Left(msg)  => BadRequest(Json.toJson(Map("message" -> msg)))
        }) match {
          case Success(response) => response
          case Failure(e) =>
            Logger.error("Error", e)
            InternalServerError(Json.toJson(Map("message" -> "Internal server error")))
        }
      }(httpContext)) getOrElse Future {
        if (!request.body.asJson.exists(b => (b \ "id").asOpt[String].isDefined && (b \ "reason").asOpt[String].isDefined))
          BadRequest(Json.toJson(Map("message" -> "Bad request")))
        else if ((inserters get reportType).isEmpty)
          NotFound(Json.toJson(Map("message" -> "Not found")))
        else if ((for {
          kaid <- request.session.get("kaid")
          name <- request.session.get("name")
        } yield User(kaid, name)).isEmpty) Unauthorized(Json.toJson(Map("message" -> "Unauthorized")))
        else InternalServerError(Json.toJson(Map("message" -> "Something went wrong")))
      }(context)
  }

  def jsRoutes = Action {
    request => Ok(JavaScriptReverseRouter("jsRoutes")(routes.javascript.MainController.report)(request)).as("text/javascript")
  }
}