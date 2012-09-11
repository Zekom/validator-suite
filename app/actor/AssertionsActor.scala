package org.w3.vs.actor

import org.w3.vs._
import org.w3.vs.model._
import org.w3.vs.assertor._
import akka.actor._
import akka.dispatch._
import scalaz._
import scala.collection.mutable.Queue
import scala.concurrent.stm._
import org.w3.util._
import JobActor._

case class AssertorCall(context: (OrganizationId, JobId, RunId), assertor: FromHttpResponseAssertor, response: HttpResponse) {
  override def toString = "AssertorCall[%s/%s assertor." format (context._2.shortId, context._3.shortId, assertor.name, response.url)
}

object AssertionsActor {

  val MAX_PENDING_ASSERTION = 2

}

import AssertionsActor._

class AssertionsActor(job: Job)(implicit conf: VSConfiguration) extends Actor {

  val logger = play.Logger.of(classOf[AssertionsActor])

  implicit val ec = conf.assertorExecutionContext

  val pendingAssertions: Ref[Int] = Ref(0)

  val queue = Queue[AssertorCall]()

  private def scheduleAssertion(context: (OrganizationId, JobId, RunId), assertor: FromHttpResponseAssertor, response: HttpResponse): Unit = {

    val start = System.currentTimeMillis()
    atomic { implicit txn => pendingAssertions += 1 }
    val sender = self
    
    Future {
      assertor.assert(context, response)
    } onComplete { case _ =>
      val end = System.currentTimeMillis()
      logger.debug("%s took %dms to assert %s" format (assertor.name, end - start, response.url))
      atomic { implicit txn => pendingAssertions -= 1 }
    } onComplete {
      case Left(t) => sender ! AssertorFailure(context, assertor.name, response.url, why = t.getMessage)
      case Right(assertorResponse) => sender ! assertorResponse
    }
    
  }

  def receive = {

    case Stop => {
      queue.dequeueAll(_ => true)
    }

    case result: AssertorResult => {
      // not sure why this is done this way (Alex)
      context.parent ! result
      while (queue.nonEmpty && pendingAssertions.single() <= MAX_PENDING_ASSERTION) {
        val AssertorCall(context, assertorId, nextRI) = queue.dequeue()
        scheduleAssertion(context, assertorId, nextRI)
      }
    }

    case result: AssertorFailure => {
      context.parent ! result
      while (queue.nonEmpty && pendingAssertions.single() <= MAX_PENDING_ASSERTION) {
        val AssertorCall(context, assertorId, nextRI) = queue.dequeue()
        scheduleAssertion(context, assertorId, nextRI)
      }
    }

    case call @ AssertorCall(context, assertorId, response) => {
      
      if (pendingAssertions.single() > MAX_PENDING_ASSERTION) {
        queue.enqueue(call)
      } else {
        scheduleAssertion(context, assertorId, response)
      }
    }

  }


}
