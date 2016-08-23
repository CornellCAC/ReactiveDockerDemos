import edu.cornell.cac.docker.api._
import edu.cornell.cac.docker.api.entities.{ContainerConfig, ContainerId, RepositoryTag}

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, SECONDS}
//import edu.cornell.cac.docker.api.json.Formats._        // use this for API version < v1.12
import edu.cornell.cac.docker.api.json.FormatsV112._

import scala.concurrent.Await

object ScriptRunnerPar {


  def main(args: Array[String]): Unit = {

    implicit val docker = Docker("localhost")
    val timeout = Duration(30, SECONDS)
    val containerCount = 4

    val cmd = Seq("/bin/sh", "-c", "while true; do echo hello world; sleep 1; done")
    val containerName = "reactive-docker"
    val imageTag = RepositoryTag.create("busybox", Some("latest"))
    val cfg = ContainerConfig("busybox", cmd)


    // create image, returns a list of docker messages when finished
    val messages = Await.result(docker.imageCreate(imageTag), timeout)

    // create containers
    val futurecContainerIds: Seq[Future[ContainerId]] = (1 to containerCount).map{ii =>
      docker.containerCreate("busybox", cfg, Some(containerName))
    }.map{fut => fut.map{case(futId, msgs) => futId}}

    // run containers
    Future{
      futurecContainerIds.foreach{fId =>
        val containerId = Await.result(fId, timeout)
        Await.result(docker.containerStart(containerId), timeout)
        println(s"container $containerId is running")
      }
    }
  }
}
