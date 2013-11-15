package controllers

import org.w3.vs.{Metrics, model}
import org.w3.vs.model._
import org.w3.vs.view.collection._
import org.w3.vs.view.Forms._
import play.api.libs.iteratee.{Enumeratee, Enumerator, Iteratee}
import play.api.libs.json.{Json => PlayJson, _}
import play.api.libs.{EventSource, Comet}
import play.api.mvc._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.controllers._
import scala.concurrent.{Promise, Future}
import org.w3.vs.view.model.JobView
import org.w3.vs.store.Formats._
import play.api.i18n.Messages
import play.api.http.MimeTypes
import com.ning.http.client._
import com.ning.http.client.AsyncHandler.STATE
import scala.util.Try

object Jobs extends VSController {

  val logger = play.Logger.of("controllers.Jobs")

  private def lowCreditWarning(credits: Int) = {
    if (credits <= 0)
      List(("warn" -> Messages("warn.noCredits", credits, routes.Application.pricing().url)))
    else if (credits <= 50)
      List(("warn" -> Messages("warn.lowCredits", credits, routes.Application.pricing().url)))
    else
      List.empty
  }

  def index: ActionA = AuthenticatedAction("back.jobs") { implicit req => user =>
    for {
      jobs_ <- model.Job.getFor(user.id)
      jobs <- JobsView(jobs_)
    } yield {
      render {
        case Accepts.Html() =>
          Ok(views.html.main(
            user = user,
            title = "Jobs - W3C Validator Suite",
            collections = Seq(jobs.bindFromRequest),
            messages = lowCreditWarning(user.credits)
          ))
        case Accepts.Json() => Ok(jobs.bindFromRequest.toJson)
      }
    }
  }

  def redirect(): ActionA = Action { implicit req => MovedPermanently(routes.Jobs.index().url) }

  def newJob: ActionA = AuthenticatedAction("back.newJob") { implicit req => user =>
    render {
      case Accepts.Html() => {
        Ok(views.html.newJob(
          form = JobForm(user),
          user = user,
          messages = lowCreditWarning(user.credits)
        ))
      }
    }
  }

  def checkEntrypoint(url: URL): Future[Unit] = {
    val promise: Promise[Unit] = akka.dispatch.Futures.promise[Unit]()
    val handler = new AsyncHandler[Unit]() {
      def onThrowable(p1: Throwable) {
        if (!promise.isCompleted) {
          promise.complete(Try(throw p1))
        }
      }
      def onStatusReceived(p1: HttpResponseStatus): STATE = {
        if (p1.getStatusCode == 200) {
          promise.complete(Try())
        } else {
          promise.complete(Try(throw new Exception("Response code != 20")))
        }
        STATE.ABORT
      }
      def onHeadersReceived(p1: HttpResponseHeaders): STATE = STATE.ABORT
      def onCompleted() {
        if (!promise.isCompleted) {
          promise.complete(Try(throw new Exception("Promise was not completed by the end of the response")))
        }
      }
      def onBodyPartReceived(p1: HttpResponseBodyPart): STATE = STATE.ABORT

    }
    vs.formHttpClient.prepareGet(url.toString).execute(handler)
    promise.future
  }

  def createAction: ActionA = AuthenticatedAction("form.createJob") { implicit req => user =>
    JobForm(user).bindFromRequest().fold(
      form => Future.successful {
        Metrics.form.createJobFailure()
        render {
          case Accepts.Html() => BadRequest(views.html.newJob(form, user))
          case Accepts.Json() => BadRequest
        }
      },
      job => {
        for {
          _ <- checkEntrypoint(job.strategy.entrypoint)
          _ <- job.save()
          _ <- job.run()
        } yield {
          render {
            case Accepts.Html() => SeeOther(routes.Jobs.index.url).flashing(("success" -> Messages("jobs.created", job.name)))
            case Accepts.Json() => Created(routes.Job.get(job.id).toString)
          }
        }
      }
    )
  }

  def socket(typ: SocketType): Handler = {
    typ match {
      case SocketType.ws => webSocket()
      case SocketType.events => eventsSocket()
    }
  }

  def webSocket(): WebSocket[JsValue] = WebSocket.using[JsValue] { implicit reqHeader =>
    val iteratee = Iteratee.ignore[JsValue]
    val enum: Enumerator[JsValue] = Enumerator.flatten(getUser().map(user => enumerator(user)))
    (iteratee, enum)
  }

  def eventsSocket: ActionA = AuthenticatedAction { implicit req => user =>
    render {
      case AcceptsStream() => Ok.stream(enumerator(user) &> EventSource()).as(MimeTypes.EVENT_STREAM)
    }
  }

  private def enumerator(user: User): Enumerator[JsValue] = {
    user.jobDatas() &> Enumeratee.map {
      iterator =>
        PlayJson.toJson(iterator.map(JobView(_).toJson))
    }
  }

}
