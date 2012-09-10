package org.w3.vs.exception

import org.w3.vs.view._
import org.w3.vs.view.form._
import org.w3.vs.model._

// TODO messages

//sealed trait SuiteException extends Exception
//case object Unknown extends Exception //with SuiteException
case class UnknownJob(id: JobId) extends Exception("UnknownJob") //with SuiteException
case object UnauthorizedJob extends Exception("UnauthorizedJob") //with SuiteException


trait UnauthorizedException
case object UnknownUser extends Exception("UnknownUser") with UnauthorizedException //with SuiteException
case object Unauthenticated extends Exception("Unauthenticated") with UnauthorizedException //with SuiteException


case class StoreException(t: Throwable) extends Exception("StoreException") //with SuiteException
case class Unexpected(t: Throwable) extends Exception(t) //with SuiteException

case class InvalidJobFormException(form: JobForm, user: User, org: Organization, idO: Option[JobId]) extends Exception("InvalidJobFormException")
//case class InvalidFormException(form: VSForm) extends Exception("InvalidFormException")
