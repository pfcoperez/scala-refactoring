/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.refactoring
package tests.analysis

import tests.util.TestHelper
import org.junit.Assert._
import analysis._

class DeclarationIndexTest extends TestHelper with GlobalIndexes with TreeAnalysis {

  import global._
  
  var index: IndexLookup = null

  def mapAndCompareSelectedTrees(expected: String, src: String)(m: PartialFunction[Tree, String]) = {
    
    // because we assert one file offsets, we don't want to have any windows newlines in the code
    val testSource = src.replaceAll("\r\n", "\n")
    
    val tree = treeFrom(testSource)
    
    index = GlobalIndex(List(CompilationUnitIndex(tree)))
    
    val firstSelected = findMarkedNodes(testSource, tree).get.selectedTopLevelTrees.head
  
    if(m.isDefinedAt(firstSelected)) {
      val result = m(firstSelected)
      assertEquals(expected, result)
    } else {
      throw new Exception("found: "+ firstSelected + "(" + firstSelected.getClass.getSimpleName + ")")
    }
  }
  
  def assertDeclarationOfSelection(expected: String, src: String) = mapAndCompareSelectedTrees(expected, src) {
    case t @ (_: TypeTree | _: RefTree) => 
      index.declaration(t.symbol).head toString
  }
  
  def assertReferencesOfSelection(expected: String, src: String) = mapAndCompareSelectedTrees(expected, src) {
    
    def refs(s: Symbol): String = {
       val ranges = index.references(s).toList filter (_.pos.isRange) sortBy(_.pos.start)
       val strings = ranges map ( ref => ref.toString +" ("+ ref.pos.start +", "+ ref.pos.end +")" )
       strings mkString ", "
    }
    
    t => t match {
      case t @ (_: TypeTree | _: DefTree) => 
        refs(t.symbol)
      case t: Ident =>
        val syms = index.positionToSymbol(t.pos)
        val allRefs = syms map refs 
        allRefs.distinct mkString ", "
    }
  }
  
  @Test
  def findValReference() = {
    assertDeclarationOfSelection("private[this] val x: Int = 1", """
      object AfindValReference {
        private[this] val x = 1
        val y = /*(*/  x  /*)*/
      }
      """)
  }  
  
  @Test
  def findValReferenceFromMethod() = {
    assertDeclarationOfSelection("private[this] val x: Int = 1", """
      object AfindValReferenceFromMethod {
        private[this] val x = 1
        def go {
          val y = /*(*/  x  /*)*/
        }
      }
      """)
  }  
  
  @ScalaVersion(matches="2.8")
  @Test
  def findShadowed28() = {
    assertDeclarationOfSelection("""val x: java.lang.String = "a"""", """
      object AfindShadowed {
        private[this] val x = 1
        def go  = {
          val x = "a"
          val y = /*(*/  x  /*)*/
          y
        }
      }
      """)
  }
  
  @Test
  @ScalaVersion(matches="2.9")
  def findShadowed29() = {
    assertDeclarationOfSelection("""val x: java.lang.String = "a"""", """
      object AfindShadowed {
        private[this] val x = 1
        def go  = {
          val x = "a"
          val y = /*(*/  x  /*)*/
          y
        }
      }
      """)
  }
  
  @Test
  @ScalaVersion(matches="2.10")
  def findShadowed() = {
    assertDeclarationOfSelection("""val x: String = "a"""", """
      object AfindShadowed {
        private[this] val x = 1
        def go  = {
          val x = "a"
          val y = /*(*/  x  /*)*/
          y
        }
      }
      """)
  }

  @Test
  def findShadowedWithThis() = {
    assertDeclarationOfSelection("""private[this] val x: Int = 1""", """
      object AfindShadowedWithThis {
        private[this] val x = 1
        def go = {
          val x = "a"
         /*(*/  this.x  /*)*/
        }
      }
      """)
  }  
    
  @Test
  def findMethod() = {
    assertDeclarationOfSelection("""def x(): Int = 5""", """
      object AfindMethod {
        def x() = 5
        def go  = {
          val y = /*(*/  x  /*)*/ ()
          y
        }
      }
      """)
  }
  
  @Test
  def findMethodFromOtherClass() = {
    assertDeclarationOfSelection("""def x: Int = 5""", """
    package findMethodFromOtherClass {
      class N {
        def x: Int = 5
      }
      object M {
        def go  = {
          val a = new N
          val y = /*(*/  a.x  /*)*/
          y
        }
      }
    }
      """)
  }  
  
  @Test
  def findReferencesToLocal() = {
    assertReferencesOfSelection("a (86, 87), a (98, 99)", """
      class H {
        def go  = {
 /*(*/    val a = 5      /*)*/
          val y = a
          a
        }
      }
      """)
  }
  
  @Test
  def findReferencesToMethod() = {
    assertReferencesOfSelection("""G.this.go (96, 98)""", """
      class G {
 /*(*/       
        def go() = {
          5
        } /*)*/
        val g = go()
      } 

      """)
  }
  
  @Test
  def findReferencesToSuperConstructorParameter() = {
    assertReferencesOfSelection("""a (83, 84)""", """
  
      class Base(s: String)

      class Sub(/*(*/a: String/*)*/) extends Base(a)
      """)
  }
  
  @Test
  def findReferencesToClass() = {
    assertReferencesOfSelection("""Z (71, 72), Z (91, 92), Z (115, 116), Z (119, 120), Z (127, 128)""", """
      package xyz
    
 /*(*/  class Z   /*)*/

      class B extends Z

      class C(a: Z) {
        def get(a: Z): Z = new Z
      }
      """)
  }
    
  @Test
  def findClassDeclarationToMethodParameter() = {
    assertReferencesOfSelection("""scala.this.Predef.String (59, 65), scala.this.Predef.String (91, 97)""", """
      class Xy
      
      object I {
        def go(xy: String) = {
          xy: /*(*/ String /*)*/
        }
      }
      """)
  }
    
  @Test
  def findClassDeclarationFromMethodParameter() = {
    assertReferencesOfSelection("""scala.this.Predef.String (65, 71), scala.this.Predef.String (97, 103)""", """
      class Xy
      
      object J {
        def go(xy: /*(*/ String /*)*/) = {
          xy: String
        }
      }
      """)
  }
  
    
  @Test
  def referencesToLazyVal() = {
    val tree =  """      
      object L {
        def go = {
          lazy val x = 5

          /*(*/ x /*)*/
        }
      }
      """
    assertReferencesOfSelection("""x (85, 86)""", tree)
    assertDeclarationOfSelection("""lazy var x$lzy: Int = 5""", tree)
  }

    
  @Test
  def referencesToTypes() = {
    val tree =  """      
      object L {
        def go[T](t: /*(*/ T /*)*/) = {
          t: T
        }
      }
      """
    assertReferencesOfSelection("""T (51, 52), T (77, 78)""", tree)
    assertDeclarationOfSelection("""type T>: Nothing <: Any""", tree)
  }

  @ScalaVersion(matches="2.8")
  @Test
  def referencesToTypesInAppliedTypes28() = {    
    assertReferencesOfSelection("""scala.this.Predef.String (54, 60), scala.this.Predef.String (91, 97), scala.this.Predef.String (121, 127)""", """      
      object U {
        def go(t: List[ /*(*/ String /*)*/ ]) = {
          val s: String = ""
          t: List[String]
        }
      }
      """)
  }
  
  @ScalaVersion(matches="2.9")
  @Test
  def referencesToTypesInAppliedTypes() = {
    assertReferencesOfSelection("""scala.this.Predef.String (54, 60), scala.this.Predef.String (91, 97), scala.this.Predef.String (121, 127)""", """      
      object U {
        def go(t: List[ /*(*/ String /*)*/ ]) = {
          val s: String = ""
          t: List[String]
        }
      }
      """)
  }
}

