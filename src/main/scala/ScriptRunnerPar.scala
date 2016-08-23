import edu.cornell.cac.docker.api._
import edu.cornell.cac.docker.api.entities.{ContainerConfig, ContainerId, RepositoryTag}

import scala.concurrent.{Future, TimeoutException}
import scala.concurrent.duration.{Duration, MINUTES, SECONDS}
import scala.concurrent.ExecutionContext.Implicits.global
//import edu.cornell.cac.docker.api.json.Formats._        // use this for API version < v1.12
import edu.cornell.cac.docker.api.json.FormatsV112._

import scala.concurrent.Await

object ScriptRunnerPar {


  def main(args: Array[String]): Unit = {

    implicit val docker = Docker("localhost")
    val timeout = Duration(30, SECONDS)
    val containerCount = 4
    val runtime = Duration(20, SECONDS)

    // Note that this command loops forever; normally a container will stop when its
    // command exits
    val cmd = Seq("/bin/sh", "-c", "while true; do echo hello world; sleep 1; done")
    val containerName = "reactive-docker"
    val imageTag = RepositoryTag.create("busybox", Some("latest"))
    val cfg = ContainerConfig("busybox", cmd)

    // create image, returns a list of docker messages when finished
    val messages = Await.result(docker.imageCreate(imageTag), timeout)

    //TODO: maybe better to create in parallel?
    // create containers
    val futurecContainerIds: Seq[Future[ContainerId]] = (1 to containerCount).map{ii =>
      docker.containerCreate("busybox", cfg, Some(containerName + ii.toString))
    }.map{fut => fut.map{case(futId, msgs) => futId}}

    // run containers
    val initiations = futurecContainerIds.map{fId =>
      val containerId = Await.result(fId, timeout)
      println(s"starting container $containerId")
      docker.containerStart(containerId)
    }

    Await.result(Future.sequence(initiations), Duration.Inf)
    println("All containers have started.")

    // stop & remove containers
    val completions = futurecContainerIds.map { fId => Future {
      val containerId = Await.result(fId, timeout)
      Thread.sleep(runtime.toMillis)
      try {
        Await.result(docker.containerStop(containerId), timeout)
        println(s"container $containerId stopped")
      } catch {
        case ex: TimeoutException =>
          println(s"Stopping container $containerId timed out: ${ex.getMessage}")
          println(s"Attempting to kill container $containerId.")
          try {
            Await.result(docker.containerKill(containerId), timeout)
          } catch {
            case ex: TimeoutException => println(s"Killing container $containerId timed out.")
          }
      }
      try {
        Await.result(docker.containerRemove(containerId), timeout)
        println(s"container $containerId removed")
      } catch {
        case ex: TimeoutException =>
          println(s"Removing container $containerId timed out: ${ex.getMessage}")
      }
    }}


    Await.result(Future.sequence(completions), Duration.Inf)
    println("All containers should be finished, terminating job.")

  }
}
