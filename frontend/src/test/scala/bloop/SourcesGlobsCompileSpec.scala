package bloop

import java.nio.file.Files

import bloop.config.Config
import bloop.logging.RecordingLogger
import bloop.util.TestProject
import bloop.util.TestUtil

object SourcesGlobsCompileSpec extends bloop.testing.BaseSuite {

  test("compile respects sources globs field") {
    TestUtil.withinWorkspace { workspace =>
      val sources = List(
        """/main/scala/Foo.scala
          |package foo
          |class Foo
          """.stripMargin,
        """/main/scala/FooTest.scala
          |package foo
          |class Foo // would be compile error if this was not excluded via globs.
          """.stripMargin
      )

      val logger = new RecordingLogger(ansiCodesSupported = false)
      val baseProject = TestProject(workspace, "a", sources)
      val globDirectory =
        baseProject.config.directory.resolve("src").resolve("main").resolve("scala")
      val `A` = baseProject.copy(
        config = baseProject.config.copy(
          sources = Nil,
          sourcesGlobs = Some(
            List(
              Config.SourcesGlobs(
                globDirectory,
                walkDepth = None,
                includes = List("glob:*.scala"),
                excludes = List("glob:*Test.scala")
              )
            )
          )
        )
      )
      val projects = List(`A`)
      val state = loadState(workspace, projects, logger)
      val compiledState = state.compile(`A`)
      assertValidCompilationState(compiledState, projects)
      assertNoDiff(
        logger.compilingInfos.mkString("\n"),
        "Compiling a (1 Scala source)"
      )

      // Create new file and assert that we now compile two files instead of one file.
      logger.clear()
      Files.write(globDirectory.resolve("Foo2.scala"), Array.emptyByteArray)
      val compiledState2 = state.clean(`A`).compile(`A`)
      assertValidCompilationState(compiledState2, projects)
      assertNoDiff(
        logger.compilingInfos.mkString("\n"),
        "Compiling a (2 Scala sources)"
      )
    }
  }
}
