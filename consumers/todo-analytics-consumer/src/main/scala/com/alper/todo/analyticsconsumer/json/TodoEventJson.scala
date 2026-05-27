package com.alper.todo.analyticsconsumer.json

import com.alper.todo.analyticsconsumer.model.TodoEventEnvelope
import play.api.libs.json._

import java.time.Instant
import java.util.UUID

object TodoEventJson {

  private implicit val instantReads: Reads[Instant] =
    Reads.of[String].map(Instant.parse)

  implicit val reads: Reads[TodoEventEnvelope] = Json.reads[TodoEventEnvelope]

  def parse(raw: String): JsResult[TodoEventEnvelope] =
    Json.parse(raw).validate[TodoEventEnvelope]

  def uuidField(payload: JsObject, key: String): UUID =
    UUID.fromString((payload \ key).as[String])

  def requiredString(payload: JsObject, key: String): String =
    (payload \ key).as[String]

  def optionalString(payload: JsObject, key: String): Option[String] =
    (payload \ key).asOpt[String]

  def requiredBoolean(payload: JsObject, key: String): Boolean =
    (payload \ key).as[Boolean]
}
