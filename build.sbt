import build.BuildImplementation.BuildDefaults

inThisBuild(
  List(
    organization := "io.github.alexarchambault.bleep",
    homepage := Some(url("https://github.com/alexarchambault/bleep")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "alexarchambault",
        "Alex Archambault",
        "",
        url("https://github.com/alexarchambault")
      )
    )
  )
)

dynverSeparator in ThisBuild := "-"

lazy val shared = project
  .settings(
    name := "bloop-shared",
    libraryDependencies ++= Seq(
      Dependencies.bsp4s,
      Dependencies.zinc,
      Dependencies.xxHashLibrary,
      Dependencies.configDirectories,
      Dependencies.sbtTestInterface,
      Dependencies.sbtTestAgent
    )
  )

import build.Dependencies
import build.Dependencies.Scala212Version

lazy val backend = project
  .enablePlugins(BuildInfoPlugin)
  .settings(testSettings ++ testSuiteSettings)
  .dependsOn(shared)
  .settings(
    name := "bloop-backend",
    buildInfoPackage := "bloop.internal.build",
    buildInfoKeys := BloopBackendInfoKeys,
    buildInfoObject := "BloopScalaInfo",
    libraryDependencies ++= List(
      Dependencies.javaDebug,
      Dependencies.nailgun,
      Dependencies.scalazCore,
      Dependencies.scalazConcurrent,
      Dependencies.coursierInterface,
      Dependencies.libraryManagement,
      Dependencies.sourcecode,
      Dependencies.monix,
      Dependencies.directoryWatcher,
      Dependencies.zt,
      Dependencies.brave,
      Dependencies.zipkinSender,
      Dependencies.pprint,
      Dependencies.difflib,
      Dependencies.asm,
      Dependencies.asmUtil
    )
  )

val testJSSettings = List(
  testOptions += Tests.Argument(TestFrameworks.JUnit, "-v", "-a"),
  scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
  libraryDependencies ++= List(
    "com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-core" % Dependencies.jsoniterVersion,
    "com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-macros" % Dependencies.jsoniterVersion % Provided
  )
)

val testResourceSettings = {
  // FIXME: Shared resource directory is ignored, see https://github.com/portable-scala/sbt-crossproject/issues/74
  Seq(Test).flatMap(inConfig(_) {
    unmanagedResourceDirectories ++= {
      unmanagedSourceDirectories.value
        .map(src => (src / ".." / "resources").getCanonicalFile)
        .filterNot(unmanagedResourceDirectories.value.contains)
        .distinct
    }
  })
}

// Needs to be called `jsonConfig` because of naming conflict with sbt universe...
lazy val config = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("config"))
  .settings(
    name := "bloop-config",
    unmanagedSourceDirectories in Compile +=
      baseDirectory.value / ".." / "src" / "main" / "scala-2.11-13",
    scalaVersion := scalaVersion.in(backend).value,
    scalacOptions := {
      scalacOptions.value.filterNot(opt => opt == "-deprecation"),
    },
    testResourceSettings
  )
  .jvmSettings(
    testSettings,
    target := (file("config") / "target" / "json-config-2.12" / "jvm").getAbsoluteFile,
    libraryDependencies ++= {
      List(
        Dependencies.jsoniterCore,
        Dependencies.jsoniterMacros % Provided,
        Dependencies.scalacheck % Test
      )
    }
  )
  .jsConfigure(_.enablePlugins(ScalaJSJUnitPlugin))
  .jsSettings(
    testJSSettings,
    target := (file("config") / "target" / "json-config-2.12" / "js").getAbsoluteFile
  )

lazy val jsonConfig213 = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("config"))
  .settings(
    name := "bloop-config",
    unmanagedSourceDirectories in Compile +=
      baseDirectory.value / ".." / "src" / "main" / "scala-2.11-13",
    scalaVersion := "2.13.1",
    testResourceSettings
  )
  .jvmSettings(
    testSettings,
    target := (file("config") / "target" / "json-config-2.13" / "jvm").getAbsoluteFile,
    libraryDependencies ++= List(
      Dependencies.jsoniterCore,
      Dependencies.jsoniterMacros % Provided
    )
  )
  .jsConfigure(_.enablePlugins(ScalaJSJUnitPlugin))
  .jsSettings(
    testJSSettings,
    target := (file("config") / "target" / "json-config-2.13" / "js").getAbsoluteFile
  )

lazy val sockets = project
  .settings(
    crossPaths := false,
    autoScalaLibrary := false,
    description := "IPC: Unix Domain Socket and Windows Named Pipes for Java",
    libraryDependencies ++= Seq(Dependencies.jna, Dependencies.jnaPlatform),
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
    sources in (Compile, doc) := Nil
  )

lazy val tmpDirSettings = Def.settings(
  javaOptions in Test += {
    val tmpDir = (baseDirectory in ThisBuild).value / "target" / "tests-tmp"
    s"-Dbloop.tests.tmp-dir=$tmpDir"
  }
)

import build.BuildImplementation.jvmOptions
// For the moment, the dependency is fixed
lazy val frontend: Project = project
  .dependsOn(
    sockets,
    shared,
    backend,
    backend % "test->test",
    config.jvm
  )
  .enablePlugins(BuildInfoPlugin)
  .configs(IntegrationTest)
  .settings(
    testSettings,
    testSuiteSettings,
    Defaults.itSettings,
    BuildDefaults.frontendTestBuildSettings,
    includeFilter in unmanagedResources in Test := {
      new FileFilter {
        def accept(file: File): Boolean = {
          val abs = file.getAbsolutePath
          !(
            abs.contains("scala-2.12") ||
              abs.contains("classes-") ||
              abs.contains("target")
          )
        }
      }
    }
  )
  .settings(
    name := "bloop-frontend",
    bloopName := "bloop",
    mainClass in Compile in run := Some("bloop.Cli"),
    buildInfoPackage := "bloop.internal.build",
    buildInfoKeys := bloopInfoKeys(nativeBridge04, jsBridge06, jsBridge1),
    javaOptions in run ++= jvmOptions,
    javaOptions in Test ++= jvmOptions,
    tmpDirSettings,
    javaOptions in IntegrationTest ++= jvmOptions,
    libraryDependencies += Dependencies.graphviz % Test,
    fork in run := true,
    fork in Test := true,
    fork in run in IntegrationTest := true,
    parallelExecution in test := false,
    libraryDependencies ++= List(
      Dependencies.jsoniterMacros % Provided,
      Dependencies.scalazCore,
      Dependencies.monix,
      Dependencies.caseApp,
      Dependencies.scalaDebugAdapter,
      Dependencies.libdaemonjvm,
      Dependencies.logback
    )
  )

lazy val bloopgun = project
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(GraalVMNativeImagePlugin)
  .settings(testSuiteSettings)
  .settings(
    name := "bloopgun",
    fork in run := true,
    fork in Test := true,
    parallelExecution in Test := false,
    buildInfoPackage := "bloopgun.internal.build",
    buildInfoKeys := List(version),
    buildInfoObject := "BloopgunInfo",
    libraryDependencies ++= List(
      //Dependencies.configDirectories,
      Dependencies.snailgun,
      // Use zt-exec instead of nuprocess because it doesn't require JNA (good for graalvm)
      Dependencies.ztExec,
      Dependencies.coursierInterface,
      Dependencies.coursierInterfaceSubs,
      Dependencies.jsoniterCore,
      Dependencies.jsoniterMacros % Provided,
      Dependencies.libdaemonjvm
    ),
    mainClass in GraalVMNativeImage := Some("bloop.bloopgun.Bloopgun"),
    graalVMNativeImageCommand := {
      val oldPath = graalVMNativeImageCommand.value
      if (!scala.util.Properties.isWin) oldPath
      else "C:/Users/runneradmin/.jabba/jdk/graalvm-ce-java11@20.1.0/bin/native-image.cmd"
    },
    graalVMNativeImageOptions ++= {
      val reflectionFile = sourceDirectory.in(Compile).value./("graal")./("reflection.json")
      assert(reflectionFile.exists)
      List(
        "--no-server",
        "--enable-http",
        "--enable-https",
        "-H:EnableURLProtocols=http,https",
        "--enable-all-security-services",
        "--no-fallback",
        s"-H:ReflectionConfigurationFiles=$reflectionFile",
        "--allow-incomplete-classpath",
        "-H:+ReportExceptionStackTraces",
        "--initialize-at-build-time=scala.Symbol",
        "--initialize-at-build-time=scala.Function1",
        "--initialize-at-build-time=scala.Function2",
        "--initialize-at-build-time=scala.runtime.StructuralCallSite",
        "--initialize-at-build-time=scala.runtime.EmptyMethodCache"
      )
    }
  )

lazy val launcher = project
  .dependsOn(sockets, bloopgun, frontend % "test->test")
  .settings(testSuiteSettings)
  .settings(
    name := "bloop-launcher",
    fork in Test := true,
    parallelExecution in Test := false,
    libraryDependencies ++= List(
      Dependencies.coursierInterface
    ),
    tmpDirSettings
  )

lazy val bloop4j = project
  .dependsOn(config.jvm)
  .settings(
    name := "bloop4j",
    fork in run := true,
    fork in Test := true,
    libraryDependencies ++= List(
      Dependencies.bsp4j
    )
  )

lazy val jsBridge06 = project
  .dependsOn(frontend % Provided, frontend % "test->test")
  .in(file("bridges") / "scalajs-0.6")
  .settings(testSettings)
  .settings(
    name := "bloop-js-bridge-0.6",
    libraryDependencies ++= List(
      Dependencies.scalaJsTools06,
      Dependencies.scalaJsSbtTestAdapter06,
      Dependencies.scalaJsEnvs06
    )
  )

lazy val jsBridge1 = project
  .dependsOn(frontend % Provided, frontend % "test->test")
  .in(file("bridges") / "scalajs-1")
  .settings(testSettings)
  .settings(
    name := "bloop-js-bridge-1",
    libraryDependencies ++= List(
      Dependencies.scalaJsLinker1,
      Dependencies.scalaJsLogging1,
      Dependencies.scalaJsEnvs1,
      Dependencies.scalaJsEnvNode1,
      Dependencies.scalaJsEnvJsdomNode1,
      Dependencies.scalaJsSbtTestAdapter1
    )
  )

lazy val nativeBridge04 = project
  .dependsOn(frontend % Provided, frontend % "test->test")
  .in(file("bridges") / "scala-native-0.4")
  .settings(testSettings)
  .settings(
    name := "bloop-native-bridge-0.4",
    libraryDependencies += Dependencies.scalaNativeTools04,
    javaOptions in Test ++= jvmOptions,
    fork in Test := true
  )

lazy val stuff = project
  .aggregate(
    sockets,
    frontend,
    backend,
    launcher,
    bloopgun,
    shared,
    config.jvm,
    jsBridge1
  )
