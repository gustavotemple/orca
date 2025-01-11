package orca

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder

import scala.concurrent.duration._

class OrcaTest extends Simulation {

  private val title = "orca-test"

  // Pipeline Domain
  private val postCreatePipelineRequest = "POST create-pipeline (mem hotspot)"
  private val getOnePipelineRequest = "GET one-pipeline"
  private val getListPipelineRequest = "GET list-pipelines"
  private val putCancelPipelineRequest = "PUT cancel-pipeline (cpu hotspot)"
  private val delPipelineRequest = "DEL delete-pipeline"

  // Task Domain
  private val postCreateTaskRequest = "POST create-task"
  private val getOneTaskRequest = "GET one-task"
  private val getListTasksRequest = "GET list-tasks (mem hotspot)"
  private val putCancelTaskRequest = "PUT cancel-task (cpu hotspot)"
  private val delTaskRequest = "DEL delete-task"

  private val circularPipelineValues = Array(
    Map("pipeline" -> "01HWJXGMWNSR9CRQB7DN0NMS9D"),
    Map("pipeline" -> "01HWJXGMD3FQZZBH1SFW057NBB"),
    Map("pipeline" -> "01HWJXGKXEJ3A01CKP7NJANWTX"),
    Map("pipeline" -> "01HWJXGKDSA5WC8D669RWC5327"),
    Map("pipeline" -> "01HWJXGHF6MJ79KH56MENZQAK7"),
  ).circular

  private val postCreatePipeline =
    http(postCreatePipelineRequest)
      .post("/orchestrate")
      .header("Content-Type", "application/json")
      .body(StringBody(
        """
          |{
          |    "id": "#{pipelineConfigId}",
          |    "name": "pipeline#{randomInt}",
          |    "application": "#{application}"
          |}
        """.stripMargin))
      .check(
        jmesPath("ref").find.transform(ref => ref.substring(ref.lastIndexOf('/') + 1)).saveAs("pipeline"),
        status.is(200)
      )

  private val getOnePipeline =
    http(getOnePipelineRequest)
      .get("/pipelines/#{pipeline}")
      .check(status.is(200))

  private val getListPipeline =
    http(getListPipelineRequest)
      .get("/pipelines?pipelineConfigIds=#{pipelineConfigId}")
      .check(status.is(200))

  private val putCancelPipeline =
    http(putCancelPipelineRequest)
      .put("/pipelines/#{pipeline}/cancel?reason=reason#{randomInt}")
      .check(status.is(202))

  private val delPipeline =
    http(delPipelineRequest)
      .delete("/pipelines/#{pipeline}")
      .check(status.is(200))

  private val circularTaskValues = Array(
    Map("task" -> "01HWJYRAYBFS2D3XN05C85JEF2"),
    Map("task" -> "01HWJYRAEZ3G3RPM6D0ZWA0C8K"),
    Map("task" -> "01HWJYR9Z8XV6H0WRY7ZG3BMD4"),
    Map("task" -> "01HWJYR9FNYN209JEQ2H70KHWE"),
    Map("task" -> "01HWJYR8ZXQT44GVTHD4BZ05QD"),
  ).circular

  private val postCreateTask =
    http(postCreateTaskRequest)
      .post("/ops")
      .header("Content-Type", "application/context+json")
      .body(StringBody(
        """
          |{
          |    "application": "#{application}",
          |    "description": { "name": "task#{randomInt}" }
          |}
        """.stripMargin))
      .check(
        jmesPath("ref").find.transform(ref => ref.substring(ref.lastIndexOf('/') + 1)).saveAs("task"),
        status.is(200)
      )

  private val getOneTask =
    http(getOneTaskRequest)
      .get("/tasks/#{task}")
      .check(status.is(200))

  private val getListTasks =
    http(getListTasksRequest)
      .get("/tasks")
      .check(status.is(200))

  private val putCancelTask =
    http(putCancelTaskRequest)
      .put("/tasks/#{task}/cancel")
      .check(status.is(202))

  private val delTask =
    http(delTaskRequest)
      .delete("/tasks/#{task}")
      .check(status.is(200))

  private val orca: ScenarioBuilder = scenario(title)
    .group("Pipeline") {
      exec(session => {
        session.set("pipelineConfigId", "configId-1")
      })
        .exec(session => {
          session.set("application", "application-1")
        })
        .exec(session => {
          val randomInt: String = scala.util.Random.nextInt(1000000000).toString
          session.set("randomInt", randomInt)
        })
        .feed(circularPipelineValues)
        .exec(postCreatePipeline)
        .pause(2)
        .exec(getOnePipeline)
        .exec(getListPipeline)
        .exec(putCancelPipeline)
        .exec(delPipeline)
    }
    .group("Task") {
      exec(session => {
        session.set("application", "application-1")
      })
        .exec(session => {
          val randomInt: String = scala.util.Random.nextInt(1000000000).toString
          session.set("randomInt", randomInt)
        })
        .feed(circularTaskValues)
        .exec(postCreateTask)
        .pause(2)
        .exec(getOneTask)
        .exec(getListTasks)
        .exec(putCancelTask)
        .exec(delTask)
    }

  val protocolReqres: HttpProtocolBuilder = http
    .baseUrl("http://127.0.0.1:8083")
    .disableCaching
  setUp(
    orca
      .inject(
        rampUsers(120) during (60 seconds),
        constantUsersPerSec(8) during (540 seconds)
      )
  ).protocols(protocolReqres)
    .assertions(
      global.successfulRequests.percent.gte(99)
    )
}

// sbt clean compile
// sbt "gatling:testOnly gatling.OrcaTest"
