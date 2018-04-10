package models

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Arrays.asList
import java.io.{ IOException, FileInputStream, File, InputStreamReader }

import play.Logger

import ioutil.IOUtil.using

import scala.util.Try
import scala.collection.JavaConverters.{ asScalaBuffer, asJavaCollection }
import scala.collection.mutable.ListBuffer

import com.google.api.client.googleapis.auth.oauth2.{ GoogleAuthorizationCodeFlow, GoogleClientSecrets }
import com.google.api.services.sheets.v4.{ Sheets, SheetsScopes }
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.http.HttpTransport
import com.google.api.services.sheets.v4.model.ValueRange

class DataInserter(spreadsheetId: String, dataStore: File, clientSecret: File, httpTransport: HttpTransport, reporter: Reporter) {
  private val appName: String = "Report Queue"
  private val scopes: List[String] = List(SheetsScopes.SPREADSHEETS, SheetsScopes.DRIVE)
  private val jackson: JsonFactory = JacksonFactory.getDefaultInstance
  private val datef: SimpleDateFormat = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss zzz")
  private val userReasonCharCount: Int = 500
  private val userReportTimeMillis: Long = 60 * 60000L
  private val userReportTimeCount: Int = 5

  @throws[IOException]
  def credentials: Credential = using[FileInputStream, Credential](new FileInputStream(clientSecret)) { clientSecret =>
    val googleDataStoreFactory: FileDataStoreFactory = new FileDataStoreFactory(dataStore)
    val googleClientSecrets: GoogleClientSecrets = GoogleClientSecrets.load(jackson, new InputStreamReader(clientSecret))
    val flow: GoogleAuthorizationCodeFlow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, jackson, googleClientSecrets, asJavaCollection(scopes)).setDataStoreFactory(googleDataStoreFactory).setAccessType("offline").build
    new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver).authorize("user")
  }

  @throws[IOException]
  def insert(service: Sheets, user: User, id: String, date: Date, reason: String, ip: String): Either[String, String] = {
    val data: List[List[Object]] = currentEntries(service)
    if (alreadySubmitted(data, user, id)) Left("You already reported that")
    else if (reason.length > userReasonCharCount) Left(s"Reasons can't exceed $userReasonCharCount characters in length")
    else if (quotaExceeded(data, user, date)) Left(s"You can't submit more than $userReportTimeCount reports in ${userReportTimeMillis / 60000L} minutes")
    else {
      reporter.transform(id) match {
        case Some(id) => {
          insertItems(service, id, user, reason, ip, date)
          Right("Thanks.  A Guardian will review your report.")
        }
        case None => Left("Invalid ID")
      }
    }
  }

  @throws[IOException]
  def service: Sheets = new Sheets.Builder(httpTransport, jackson, credentials).setApplicationName(appName).build

  @throws[IOException]
  def currentEntries(service: Sheets): List[List[Object]] = asScalaBuffer(service.spreadsheets.values.get(spreadsheetId, "A:E").execute.getValues).toList.map(asScalaBuffer(_).toList).drop(1)

  def quotaExceeded(data: List[List[Object]], user: User, date: Date): Boolean = data.count(e => Try(e(1).toString == user.kaid && date.getTime - datef.parse(e(4).toString).getTime < userReportTimeMillis) getOrElse {
    Logger.error("Failed to process row: " + e)
    false
  }) >= userReportTimeCount

  def alreadySubmitted(data: List[List[Object]], user: User, id: String): Boolean = data.count(e => (for {
    sheetReportedId <- (e lift 0).map(e => reporter.extractFromTransformed(e.toString)).flatten
    userKaid <- (e lift 1)
  } yield sheetReportedId == id && userKaid.toString == user.kaid) getOrElse false) > 0

  def insertItems(service: Sheets, id: String, user: User, reason: String, ip: String, date: Date): Unit = {
    val insertionBody: ValueRange = (new ValueRange).setValues(asList(asList(id.asInstanceOf[Object], user.kaid.asInstanceOf[Object], reason.asInstanceOf[Object], ip.asInstanceOf[Object], datef.format(date).asInstanceOf[Object])))
    val insertRequest = service.spreadsheets.values.append(spreadsheetId, "A:E", insertionBody)
    insertRequest.setValueInputOption("USER_ENTERED")
    insertRequest.setInsertDataOption("OVERWRITE")
    insertRequest.execute
  }
}

object DataInserter {
  def apply(spreadsheetId: String, dataStore: File, clientSecret: File, httpTransport: HttpTransport, reporter: Reporter): DataInserter = new DataInserter(spreadsheetId, dataStore, clientSecret, httpTransport, reporter)
}