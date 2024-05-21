package bloop
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

import bloop.cli.ExitStatus
import bloop.engine.ExecutionContext
import bloop.io.AbsolutePath
import bloop.logging.RecordingLogger
import bloop.task.Task
import bloop.util.TestProject
import bloop.util.TestUtil

import coursierapi.MavenRepository

object ScalaVersionsSpec extends bloop.testing.BaseSuite {
  var loggers: List[RecordingLogger] = Nil

  def compileProjectFor(scalaVersion: String): Task[Unit] = Task {
    TestUtil.withinWorkspace { workspace =>
      val (compilerOrg, compilerArtifact) = {
        if (scalaVersion.startsWith("3.")) "org.scala-lang" -> "scala3-compiler_3"
        else "org.scala-lang" -> "scala-compiler"
      }

      def jarsForScalaVersion(version: String, logger: RecordingLogger) = {
        ScalaInstance
          .resolve(
            compilerOrg,
            compilerArtifact,
            version,
            logger,
            List(
              MavenRepository.of("https://scala-ci.typesafe.com/artifactory/scala-integration/")
            )
          )
          .allJars
          .map(AbsolutePath(_))
      }

      val source = {
        if (compilerArtifact.contains("scala3-compiler")) {
          """/main/scala/Foo.scala
            |class Foo { val x: String | Int = 1 }
            """.stripMargin
        } else {
          """/main/scala/Foo.scala
            |class Foo
            """.stripMargin
        }
      }

      val logger = new RecordingLogger(ansiCodesSupported = false)
      loggers.synchronized { loggers = logger :: loggers }
      val jars = jarsForScalaVersion(scalaVersion, logger)
      val `A` = TestProject(
        workspace,
        "a",
        List(source),
        scalaOrg = Some(compilerOrg),
        scalaCompiler = Some(compilerArtifact),
        scalaVersion = Some(scalaVersion),
        jars = jars
      )

      val projects = List(`A`)
      val state = loadState(workspace, projects, logger)
      val compiledState = state.compile(`A`)
      try {
        Predef.assert(compiledState.status == ExitStatus.Ok)
        assertValidCompilationState(compiledState, projects)
      } finally loggers.synchronized { loggers = loggers.filterNot(_ == logger) }
    }
  }

  val scalaVersions = List(
    "2.12.17",
    "2.13.10",
    "3.1.3",
    "3.2.1",
    "2.13.14"
  )

  val allVersions = scalaVersions

  test("cross-compile build to latest Scala versions") {

    val all = allVersions.map(compileProjectFor)

    try {
      TestUtil.await(FiniteDuration(120, "s"), ExecutionContext.ioScheduler) {
        Task
          .sequence(all.grouped(2).map(group => Task.gatherUnordered(group)).toList)
          .map(_ => ())
      }
    } catch {
      case NonFatal(t) =>
        loggers.foreach(logger => logger.dump())
        Thread.sleep(100)
        TestUtil.printThreadDump()
        Thread.sleep(100)
        throw t
    }
  }
}
