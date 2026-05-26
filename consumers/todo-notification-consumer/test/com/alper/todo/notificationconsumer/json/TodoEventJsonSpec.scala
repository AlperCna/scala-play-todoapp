package com.alper.todo.notificationconsumer.json

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.JsSuccess

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

class TodoEventJsonSpec extends AnyWordSpec with Matchers {

  "TodoEventJson" should {
    "parse a producer event fixture into the consumer contract" in {
      val fixturePath = Paths.get("fixtures", "todo-created.json")
      val rawJson = Files.readString(fixturePath, StandardCharsets.UTF_8)

      val result = TodoEventJson.parseEnvelope(rawJson)

      result shouldBe a[JsSuccess[_]]

      val envelope = result.get
      envelope.eventType shouldBe "TodoCreated"
      envelope.eventVersion shouldBe 1
      envelope.entityType shouldBe "todo"
      envelope.payload.title shouldBe "Finish Kafka workshop notes"
      envelope.payload.isCompleted shouldBe false
      envelope.payload.description shouldBe Some("Prepare the notification flow explanation")
    }
  }
}
