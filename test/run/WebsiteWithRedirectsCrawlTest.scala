package org.w3.vs.run

import org.w3.vs.util._
import org.w3.vs.util._
import org.w3.vs.util.website._
import org.w3.vs.model._
import org.w3.vs.util.akkaext._
import org.w3.vs.http._
import org.w3.vs.http.Http._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import org.w3.vs.util.Util._
import play.api.libs.iteratee._

class WebsiteWithRedirectsCrawlTest extends RunTestHelper with TestKitHelper {

  val strategy =
    Strategy( 
      entrypoint=URL("http://localhost:9001/"),
      linkCheck=true,
      maxResources = 100,
      filter=Filter(include=Everything, exclude=Nothing),
      assertorsConfiguration = Map.empty)
  
  val job = Job.createNewJob(name = "@@", strategy = strategy, creatorId = userTest.id)

  val circumference = 10
  
  val servers = Seq(Webserver(9001, Website.cyclicWithRedirects(circumference).toServlet))
  
  "test cyclic" in {
    
    (for {
      _ <- User.save(userTest)
      _ <- Job.save(job)
    } yield ()).getOrFail()
    
    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)

    val runningJob = job.run().getOrFail()
    val Running(runId, actorPath) = runningJob.status

    val completeRunEvent =
      (runningJob.runEvents() &> Enumeratee.mapConcat(_.toSeq) |>>> waitFor[RunEvent]{ case e: DoneRunEvent => e }).getOrFail(3.seconds)

    completeRunEvent.resources must be(circumference + 1)

  }
  
}
