package bloop.reporter

import bloop.io.AbsolutePath

/**
 * Interface for a reporter that has a configuration.
 * This is the API visible from a `ReporterFormat`.
 */
trait ConfigurableReporter {

  /** The current working directory of the user who started compilation. */
  def cwd: AbsolutePath

  /** The configuration for this reporter. */
  def config: ReporterConfig

  /** All the `Problems` seen by this reporter. */
  def allProblems: Seq[Problem]

  /** `true` if this reporter has received errors, `false` otherwise. */
  def hasErrors(): Boolean

  /** `true` if this reporter has received warnings, `false` otherwise. */
  def hasWarnings(): Boolean
}
