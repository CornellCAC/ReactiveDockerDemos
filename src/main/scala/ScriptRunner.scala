import edu.cornell.cac.docker.api._
import edu.cornell.cac.docker.api.entities.{ContainerConfig, RepositoryTag}

import scala.concurrent.duration.Duration
import scala.concurrent.duration.SECONDS
//import edu.cornell.cac.docker.api.json.Formats._        // use this for API version < v1.12
import edu.cornell.cac.docker.api.json.FormatsV112._      // use this for API versions v1.12+
import scala.concurrent.Await
import play.api.libs.iteratee._
import scala.concurrent.ExecutionContext.Implicits.global


object ScriptRunner {

  def main(args: Array[String]): Unit = {

    implicit val docker = Docker("localhost")
    val timeout = Duration(30, SECONDS)

    // Note that this command loops forever; normally a container will stop when its
    // command exits
    val cmd = Seq("/bin/sh", "-c", "while true; do echo hello world; sleep 1; done")
    val containerName = "reactive-docker"
    val imageTag = RepositoryTag.create("busybox", Some("latest"))
    val cfg = ContainerConfig("busybox", cmd)


    // create image, returns a list of docker messages when finished
    val messages = Await.result(docker.imageCreate(imageTag), timeout)

    messages.foreach(m => println(s"imageCreate: $m"))

    // create container
    val containerId = Await.result(docker.containerCreate(
      "busybox", cfg, Some(containerName)), timeout
    )._1

    // run container
    Await.result(docker.containerStart(containerId), timeout)
    println(s"container $containerId is running")

  }
}
