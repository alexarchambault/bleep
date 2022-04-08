package bloop.bsp

import bloop.io.AbsolutePath

import org.junit.Test

class ProjectUrisSpec {
  @Test def ParseUriWindows(): Unit = {
    val path =
      """C:\Windows\System32\config\systemprofile\.sbt\1.0\staging\2c729f02b8060e8c0e9b\\utest"""
    ProjectUris.toURI(AbsolutePath.completelyUnsafe(path), "root-test")
    ()
  }
}
