import build.BuildImplementation.BuildDefaults
import scala.util.Properties

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
    ),
    sonatypeCredentialHost := "s01.oss.sonatype.org"
  )
)

lazy val sonatypeSetting = Def.settings(
  sonatypeProfileName := "io.github.alexarchambault"
)

(ThisBuild / dynverSeparator) := "-"

ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0"

val scalafixSettings: Seq[Setting[_]] = Seq(
  scalacOptions ++= {
    if (scalaVersion.value.startsWith("2.11")) Seq("-Ywarn-unused-import")
    else if (scalaVersion.value.startsWith("2.12")) Seq("-Ywarn-unused", "-Xlint:unused")
    else if (scalaVersion.value.startsWith("2.13")) Seq("-Wunused")
    else Seq.empty
  },
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision
)


lazy val shared = project
  .settings(scalafixSettings)
  .settings(
    sonatypeSetting,
    name := "bloop-shared",
    libraryDependencies ++= Seq(
      Dependencies.bsp4s,
      Dependencies.coursierInterface,
      Dependencies.zinc,
      Dependencies.log4j,
      Dependencies.xxHashLibrary,
      Dependencies.sbtTestInterface,
      Dependencies.sbtTestAgent
    )
  )

import build.Dependencies
import build.Dependencies.Scala212Version

lazy val backend = project
  .enablePlugins(BuildInfoPlugin)
  .settings(scalafixSettings)
  .settings(testSettings ++ testSuiteSettings)
  .dependsOn(shared)
  .settings(
    sonatypeSetting,
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
  .settings(scalafixSettings)
  .settings(
    sonatypeSetting,
    name := "bloop-config",
    (Compile / unmanagedSourceDirectories) +=
      Keys.baseDirectory.value / ".." / "src" / "main" / "scala-2.11-13",
    scalaVersion := (backend / Keys.scalaVersion).value,
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
  .settings(scalafixSettings)
  .settings(
    sonatypeSetting,
    name := "bloop-config",
    (Compile / unmanagedSourceDirectories) +=
      Keys.baseDirectory.value / ".." / "src" / "main" / "scala-2.11-13",
    scalaVersion := Dependencies.Scala213Version,
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
    shared,
    backend,
    backend % "test->test",
    config.jvm
  )
  .enablePlugins(BuildInfoPlugin)
  .configs(IntegrationTest)
  .settings(scalafixSettings)
  .settings(
    sonatypeSetting,
    testSettings,
    testSuiteSettings,
    Defaults.itSettings,
    BuildDefaults.frontendTestBuildSettings,
    (Test / unmanagedResources / includeFilter) := {
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
    (Compile / run / mainClass) := Some("bloop.Cli"),
    buildInfoPackage := "bloop.internal.build",
    buildInfoKeys := bloopInfoKeys(nativeBridge04, jsBridge06, jsBridge1),
    (run / javaOptions) ++= jvmOptions,
    (Test / javaOptions) ++= jvmOptions,
    tmpDirSettings,
    (IntegrationTest / javaOptions) ++= jvmOptions,
    (run / fork) := true,
    (Test / fork) := true,
    (IntegrationTest / run / fork) := true,
    (test / parallelExecution) := false,
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

lazy val bloopgunCoreSettings = Def.settings(
  sonatypeSetting,
  name := "bloopgun-core",
  (run / fork) := true,
  (Test / fork) := true,
  (Test / parallelExecution) := false,
  buildInfoPackage := "bloopgun.internal.build",
  buildInfoKeys := List(version),
  buildInfoObject := "BloopgunInfo",
  libraryDependencies ++= List(
    Dependencies.snailgun,
    // Use zt-exec instead of nuprocess because it doesn't require JNA (good for graalvm)
    Dependencies.ztExec,
    Dependencies.coursierInterface,
    Dependencies.coursierInterfaceSubs,
    Dependencies.jsoniterCore,
    Dependencies.jsoniterMacros % Provided,
    Dependencies.libdaemonjvm
  )
)

lazy val bloopgunSettings = Def.settings(
  sonatypeSetting,
  name := "bloopgun",
  libraryDependencies ++= List(
    Dependencies.logback,
    Dependencies.svmSubs
  ),
  (GraalVMNativeImage / mainClass) := Some("bloop.bloopgun.Bloopgun"),
  graalVMNativeImageCommand := {
    val oldPath = graalVMNativeImageCommand.value
    if (scala.util.Properties.isWin) sys.props("java.home") + "\\bin\\native-image.cmd"
    else oldPath
  },
  graalVMNativeImageOptions ++= {
    val reflectionFile = (Compile / Keys.sourceDirectory).value./("graal")./("reflection.json")
    assert(reflectionFile.exists, s"${reflectionFile.getAbsolutePath()} doesn't exist")
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
      "--initialize-at-build-time=scala.runtime.EmptyMethodCache",
      "--initialize-at-build-time=scala.runtime.LambdaDeserialize",
      "--initialize-at-build-time=scala.collection.immutable.VM"
    )
  }
)

lazy val `bloopgun-core` = project
  .disablePlugins(ScriptedPlugin)
  .enablePlugins(BuildInfoPlugin)
  .settings(scalafixSettings)
  .settings(testSuiteSettings)
  .settings(target := (file("bloopgun-core") / "target" / "bloopgun-2.12").getAbsoluteFile)
  .settings(bloopgunCoreSettings)
  .settings(
    scalaVersion := Dependencies.Scala212Version
  )

lazy val `bloopgun-core-213`: Project = project
  .in(file("bloopgun-core"))
  .disablePlugins(ScriptedPlugin)
  .enablePlugins(BuildInfoPlugin)
  .settings(scalafixSettings)
  .settings(testSuiteSettings)
  .settings(target := (file("bloopgun-core") / "target" / "bloopgun-2.13").getAbsoluteFile)
  .settings(bloopgunCoreSettings)
  .settings(
    scalaVersion := Dependencies.Scala213Version
  )

lazy val bloopgun = project
  .enablePlugins(GraalVMNativeImagePlugin)
  .dependsOn(`bloopgun-core`)
  .settings(
    scalaVersion := Dependencies.Scala212Version,
    target := (file("bloopgun") / "target" / "bloopgun-2.12").getAbsoluteFile
  )
  .settings(bloopgunSettings)

lazy val bloopgun213 = project
  .in(file("bloopgun"))
  .enablePlugins(GraalVMNativeImagePlugin)
  .dependsOn(`bloopgun-core-213`)
  .settings(
    scalaVersion := Dependencies.Scala213Version,
    target := (file("bloopgun") / "target" / "bloopgun-2.13").getAbsoluteFile
  )
  .settings(bloopgunSettings)

lazy val launcher = project
  .in(file("launcher"))
  .dependsOn(`bloopgun-core`)
  .settings(scalafixSettings)
  .settings(testSuiteSettings)
  .settings(
    sonatypeSetting,
    name := "bloop-launcher",
    target := (file("launcher") / "target" / "launcher-2.12").getAbsoluteFile
  )

lazy val launcher213 = project
  .in(file("launcher"))
  .disablePlugins(ScriptedPlugin)
  .dependsOn(`bloopgun-core-213`)
  .settings(scalafixSettings)
  .settings(testSuiteSettings)
  .settings(
    name := "bloop-launcher",
    scalaVersion := Dependencies.Scala213Version,
    target := (file("launcher") / "target" / "launcher-2.13").getAbsoluteFile
  )

lazy val launcherTest = project
  .in(file("launcher-test"))
  .disablePlugins(ScriptedPlugin)
  .dependsOn(launcher, frontend % "test->test")
  .settings(scalafixSettings)
  .settings(testSuiteSettings)
  .settings(
    name := "bloop-launcher-test",
    (Test / fork) := true,
    (Test / parallelExecution) := false,
    libraryDependencies ++= List(
      Dependencies.coursierInterface
    ),
    tmpDirSettings
  )

lazy val bloop4j = project
  .dependsOn(config.jvm)
  .settings(scalafixSettings)
  .settings(
    sonatypeSetting,
    name := "bloop4j",
    (run / fork) := true,
    (Test / fork) := true,
    libraryDependencies ++= List(
      Dependencies.bsp4j
    )
  )

lazy val jsBridge06 = project
  .dependsOn(frontend % Provided, frontend % "test->test")
  .in(file("bridges") / "scalajs-0.6")
  .settings(scalafixSettings)
  .settings(testSettings)
  .settings(
    sonatypeSetting,
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
  .settings(scalafixSettings)
  .settings(testSettings)
  .settings(
    sonatypeSetting,
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
  .settings(scalafixSettings)
  .settings(testSettings)
  .settings(
    sonatypeSetting,
    name := "bloop-native-bridge-0.4",
    libraryDependencies += Dependencies.scalaNativeTools04,
    (Test / javaOptions) ++= jvmOptions,
    (Test / fork) := true
  )

lazy val stuff = project
  .aggregate(
    frontend,
    backend,
    launcher,
    launcherTest,
    bloopgun,
    bloopgun213,
    `bloopgun-core`,
    `bloopgun-core-213`,
    shared,
    config.jvm,
    jsBridge1
  )
  .settings(
    sonatypeSetting,
    (publish / skip) := true
  )

(publish / skip) := true
sonatypeSetting
