import edu.cornell.cac.docker.api._
import edu.cornell.cac.docker.api.entities._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, MINUTES, SECONDS}
import scala.concurrent.{Future, TimeoutException}
import scala.util.Try
//import edu.cornell.cac.docker.api.json.Formats._        // use this for API version < v1.12
import edu.cornell.cac.docker.api.json.FormatsV112._

import scala.concurrent.Await

object MpiRunSimple {


  def main(args: Array[String]): Unit = {

    implicit val docker = Docker("localhost")

    // Without this, we don't have shell functionality for e.g., IO redirects
    val shellRunner: Option[Seq[String]] = Some(Seq("/bin/sh", "-c"))

    val timeout = Duration(30, SECONDS)
    val containerCount = 4
    val runtime = Duration(7, MINUTES)
    val outPathContainer = "/data"
    val outPathHost = "/tmp"
    val stderrFile = s"$outPathContainer/my_mpi_job.stderr"
    val stdoutFile = s"$outPathContainer/my_mpi_job.stdout"
    val outputRedirect =  Seq("2>", stderrFile, ">", stdoutFile)

    // Note that this command loops forever; a container will stop when its
    // command exits
    val cmd = Seq("/usr/sbin/sshd", "-D")
    val sshStopCmd = Seq("service", "ssh", "stop") // configure this to be appropriate for your OS
    val headImg = "dockeropenmpi_mpi_head"
    val nodeImg = "dockeropenmpi_mpi_node"

    val containerName = "dockeropenmpi"
    val imageHeadTag = RepositoryTag.create(headImg, Some("latest"))
    val imageNodeTag = RepositoryTag.create(nodeImg, Some("latest"))

    //TODO: make this easier to do:
    val bindMntVol: BindMountVolume = BindMountVolume(outPathContainer, outPathHost)
    val hc1Binds = HostConfig(Some(Seq(bindMntVol)))

    val cfg = ContainerConfiguration(Some("dockeropenmpi"), Some(cmd),
      Volumes = Some(Map(outPathContainer -> DockerVolume(outPathContainer, outPathHost))),
      HostConfig = Some(hc1Binds)
    )

    // create image, returns a list of docker messages when finished
    val messages = Await.result(docker.imageCreate(imageHeadTag), timeout) ::
      Await.result(docker.imageCreate(imageNodeTag), timeout)

    //TODO: maybe better to create in parallel?
    // create containers
    val futureHeadContainerId: Future[ContainerId] =
      docker.containerCreate(headImg, cfg, Some(containerName + "_head"))
        .map{case(futId, msgs) => futId}

    val futureNodeContainerIds: Seq[Future[ContainerId]] = (1 until containerCount).map{ii =>
      docker.containerCreate(nodeImg, cfg, Some(containerName + "_head" + ii.toString))
    }.map{fut => fut.map{case(futId, msgs) => futId}}

    val containerIds = (futureHeadContainerId +: futureNodeContainerIds).map{
      fId => Await.result(fId, timeout)
    }
    val initiations = containerIds.map{id =>
      println(s"starting container $id")
      docker.containerStart(id)
    }

    Await.result(Future.sequence(initiations), Duration.Inf)
    println("All containers have started.")

    // Do some work
    println("Do some work ...")
    docker.containerExecCreate(
      containerIds.head,
      shellRunner.getOrElse(Seq()) ++ Seq((
        "mpirun -n 2 python /home/mpirun/mpi4py_benchmarks/all_tests.py".split(' ').toSeq ++
          outputRedirect
        ).mkString(" "))
    ).flatMap{case (execId, msgs) =>
      println("initiating exec start!") // DEBUG
      if (msgs.nonEmpty) msgs.foreach(msg => println(msg))
      docker.containerExecStart(execId)
    }

    //TODO: remove this and add a wait method of some sort (once modeled as futures or tasks)
    Thread.sleep(runtime.toMillis)

    // Attempt normal container stop by stopping CMD process
    containerIds.foreach{id => docker.containerExec(id, sshStopCmd)}

    // stop & remove containers
    val completions = containerIds.map{ containerId => Future {
      Thread.sleep(timeout.toMillis)

      val stoppedAndRemoved: Try[Boolean] = for {
        stopRes <- Try{
          val res = Await.result(docker.containerStop(containerId), timeout)
          println(s"container $containerId stopped")
          res
        }.recover{
          case ex: TimeoutException =>
            println(s"Stopping container $containerId timed out: ${ex.getMessage}")
            println(s"Attempting to kill container $containerId.")
            Try(Await.result(docker.containerKill(containerId), timeout)).recover{
              case ex: TimeoutException =>
                println(s"Killing container $containerId timed out.")
                false
              case _ => false
            }.get
          case _ => false
        }
        removeRes <- Try(Await.result(docker.containerRemove(containerId), timeout)).recover{
          case ex: TimeoutException =>
            println(s"Removing container $containerId timed out: ${ex.getMessage}")
            false
          case _ => false
        }
      } yield removeRes && stopRes
    }}


    Await.result(Future.sequence(completions), Duration.Inf)
    println("All containers should be finished, terminating job.")

  }
}
