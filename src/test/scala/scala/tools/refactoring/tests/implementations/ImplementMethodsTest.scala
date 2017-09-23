package scala.tools.refactoring
package tests.implementations

import scala.tools.refactoring.implementations.ImplementMethods
import scala.tools.refactoring.tests.util.{TestHelper, TestRefactoring}

class ImplementMethodsTest extends TestHelper with TestRefactoring {
  outer =>

  def implementMethods(pro: FileSet) = new TestRefactoringImpl(pro) {
    override val refactoring = new ImplementMethods with TestProjectIndex
    val changes = performRefactoring(())
  }.changes

  @Test
  def implementMethodFromFirstMixing() = new FileSet() {
    """
      |package implementMethods
      |
      |trait T {
      |  def f(x: Int): String
      |}
      |
      |trait S {
      |  def g(x: Int): Int
      |}
      |
      |object Obj extends /*(*/T/*)*/ with S {
      |  val x: Int = ???
      |}
    """.stripMargin becomes
    """
      |package implementMethods
      |
      |trait T {
      |  def f(x: Int): String
      |}
      |
      |trait S {
      |  def g(x: Int): Int
      |}
      |
      |object Obj extends /*(*/T/*)*/ with S {
      |  val x: Int = ???
      |
      |  def f(x: Int): String = {
      |    ???
      |  }
      |}
    """.stripMargin
  } applyRefactoring implementMethods

  @Test
  def implementMethodFromSecondMixing() = new FileSet() {
    """
      |package implementMethods
      |
      |trait T {
      |  def f(x: Int): String
      |}
      |
      |trait S {
      |  def g(x: Int): Int
      |}
      |
      |object Obj extends T with /*(*/S/*)*/ {
      |  val x: Int = ???
      |}
    """.stripMargin becomes
    """
      |package implementMethods
      |
      |trait T {
      |  def f(x: Int): String
      |}
      |
      |trait S {
      |  def g(x: Int): Int
      |}
      |
      |object Obj extends T with /*(*/S/*)*/ {
      |  val x: Int = ???
      |
      |  def g(x: Int): Int = {
      |    ???
      |  }
      |}
    """.stripMargin
  } applyRefactoring implementMethods

  @Test
  def implementMethodsNotImplemented() = new FileSet() {
    """
      |package implementMethods
      |
      |trait T {
      |  def f(x: Int): String
      |  def g(x: Int): Int
      |}
      |
      |object Obj extends /*(*/T/*)*/ {
      |
      |  def g(x: Int): Int = 1
      |
      |}
    """.stripMargin becomes
    """
      |package implementMethods
      |
      |trait T {
      |  def f(x: Int): String
      |  def g(x: Int): Int
      |}
      |
      |object Obj extends /*(*/T/*)*/ {
      |
      |  def g(x: Int): Int = 1
      |
      |  def f(x: Int): String = {
      |    ???
      |  }
      |
      |}
    """.stripMargin
  } applyRefactoring implementMethods

}
