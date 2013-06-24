package org.w3.vs.util

import java.io._
import scala.concurrent.{ Future, ExecutionContext }
import com.codahale.metrics._
import scala.concurrent.duration.Duration
import scala.concurrent.Await
import scala.util.{ Success, Failure, Try }
import org.w3.vs.web._

object Util {

  val logger = play.Logger.of("org.w3.vs.util.Util")

  /**
   * deletes all the files in a directory (only first level, not recursive)
   * note: there is still an issue with Windows
   */
  def delete(f: File): Unit = {
    if (f.isDirectory)
      f.listFiles foreach delete
    if (!f.delete())
      throw new FileNotFoundException("Failed to delete file: " + f)
  }

  /* use the following timer for synchronous tasks */

  def timer[T](name: String)(body: => T): T = {
    val start = System.currentTimeMillis
    val result = body
    val end = System.currentTimeMillis
    logger.debug(name + ": " + (end - start))
    result
  }

  def timer[T](t: Timer)(body: => T): T = {
    val context = t.time()
    val result = body
    context.stop()
    result
  }

  def timer[T](name: String, t: Timer)(body: => T): T = {
    timer(t) { timer(name)(body) }
  }

  implicit class FutureF[+T](val future: Future[T]) extends AnyVal {

    def getOrFail(duration: Duration = Duration("6s")): T = {
      Await.result(future, duration)
    }

    /**
     * logs (DEBUG) how long the Future took to be completed
     */
    def timer(name: String)(implicit ec: ExecutionContext): Future[T] = {
      val start = System.currentTimeMillis
      future onComplete { case _ =>
        val end = System.currentTimeMillis
        logger.debug(name + ": " + (end - start) + "ms")
      }
      future
    }

    /**
     * configures the timer metrics for the given Future
     */
    def timer(timer: Timer)(implicit ec: ExecutionContext): Future[T] = {
      val context = timer.time()
      future onComplete { case _ => context.stop() }
      future
    }

  }

  implicit class DurationW(val n: Int) extends AnyVal {
    import scala.concurrent.duration.{ FiniteDuration, Duration }
    def second: FiniteDuration = seconds
    def seconds: FiniteDuration = Duration(n, java.util.concurrent.TimeUnit.SECONDS)
  }

  import java.net.{ URL => jURL }

  implicit class jURLW(val url: jURL) extends AnyVal {
    def withToken(token: String): jURL = new jURL(s"""${url}t0k3n=${token}""")
  }

  implicit class URLW(val url: URL) extends AnyVal {
    def withToken(token: String): URL = URL(s"""${url}t0k3n=${token}""")
  }

  implicit class TryW[T](val t: Try[T]) extends AnyVal {
    def asFuture: Future[T] = t match {
      case Success(s) => Future.successful(s)
      case Failure(f) => Future.failed(f)
    }
  }

//  def getLoggerNames: List[String] = {
//    val lc = (LoggerContext) LoggerFactory.getILoggerFactory()
//     List<String> strList = new ArrayList<String>();
//     for (ch.qos.logback.classic.Logger log : lc.getLoggerList()) {
//       if(log.getLevel() != null || hasAppenders(log)) {
//         strList.add(log.getName());
//       }
//     }
//     return strList;
//   }

  import play.api.libs.iteratee._
  import scala.reflect.ClassTag

  def waitFor[A] = new {

    def apply[E]()(implicit classTag: ClassTag[A]): Iteratee[E, A] = Cont {
      case Input.El(classTag(a)) => Done(a)
      case in @ Input.EOF => Error("couln't find an element that matches the partial function", in)
      case _ => apply()(classTag)
    }

    def apply[B](pf: PartialFunction[A, B]): Iteratee[A, B] = Cont {
      case Input.El(a) if pf.isDefinedAt(a) => Done(pf(a))
      case in @ Input.EOF => Error("couln't find an element that matches the partial function", in)
      case _ => apply(pf)
    }
  }

  /** Enumeratee that maps Input.Empty to Input.EOF */
  def endWithEmpty[T]: Enumeratee[T, T] =
    Enumeratee.mapInput[T] {
      case Input.Empty => Input.EOF
      case t => t
    }

  /** Enumeratee that just passes the elements and prints them -- for test only */
  def eprint[T]: Enumeratee[T, T] = Enumeratee.map { t => println("** "+t); t }

  import reactivemongo.core.commands.GetLastError

  val journalCommit = GetLastError(awaitJournalCommit = true)

}
