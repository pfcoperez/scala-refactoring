package scala.tools.refactoring.implementations

import scala.tools.refactoring.common.InteractiveScalaCompiler
import scala.tools.refactoring.transformation.TreeFactory
import scala.tools.refactoring.{MultiStageRefactoring, analysis}

abstract class ImplementMethods extends MultiStageRefactoring with analysis.Indexes with TreeFactory with InteractiveScalaCompiler {

  import global._

  case class PreparationResult(targetTemplate: Template, memberToImplement: Seq[ValOrDefDef])

  /* Helper class to box members so they can be
     compared in terms of their signature.
   */
  implicit class OverloadedMember(val member: ValOrDefDef) {

    private val key = member match {
      case method: DefDef =>
        import method.{name, tparams, vparamss}

        val vparamsTypes = for {
          paramList <- vparamss
          param <- paramList
        } yield param.tpt.tpe

        (name, tparams, vparamsTypes)

      case ValDef(_, name, _, _) => name.toString.trim
    }

    override def hashCode(): RunId =
      key.hashCode()

    override def equals(obj: scala.Any): Boolean = obj match {
      case that: OverloadedMember => key == that.key
      case _ => false
    }
  }

  private def templateAncestry(template: Template): List[Template] =
    template :: {
      for {
        parent <- template.parents
        parentImp <- index.declaration(parent.symbol).toList collect {
          case ClassDef(_, _, _, impl) => impl
        }
        ancestor <- templateAncestry(parentImp)
      } yield ancestor
    }

  override def prepare(s: Selection): Either[PreparationError, PreparationResult] = {


    // Expand the selection to the concrete type when a kind was initially selected.
    val maybeSelectedTemplate = (s::s.expandToNextEnclosingTree.toList) flatMap { sel: Selection =>
      index.declaration(sel.enclosingTree.symbol)
    } collectFirst {
      case templateDeclaration: ClassDef => templateDeclaration.impl
    }

    // Get a sequence of members (methods or values) found in the selected mixed trait
    val membersToImplement = {

      val rawList = for {
        selectedTemplate <- maybeSelectedTemplate.toList
        selectedDeclaration <- templateAncestry(selectedTemplate)
        unimplementedMember <- selectedDeclaration.body collect {
          case defOrValDef: ValOrDefDef if defOrValDef.rhs.isEmpty => defOrValDef
        }
      } yield unimplementedMember

      val (uniqueMethods, _) =
        rawList.foldRight((List.empty[ValOrDefDef], Set.empty[OverloadedMember])) {
          case (method, (l, visited)) if !visited.contains(method) =>
            (method::l, visited + method)
          case (_, st) => st
        }
      uniqueMethods
    }

    // Use the selection to find the template where the members should be implemented.
    val targetTemplate = s.expandToNextEnclosingTree.flatMap {
      _.selectedSymbolTree collect {
        case target: Template => target
      }
    }

    targetTemplate map { t => // If the selection has indeed a target template...
      if(membersToImplement.isEmpty) Left { //... as long as there are members in the mixed trait...
        PreparationError("There are not methods to implement")
      } else Right { //... these are selected to be defined in the target template.
        // If and only if they were not already defined there.
        val implementedMembers: Set[OverloadedMember] = {
          t.body collect {
            case memberDef: ValOrDefDef => new OverloadedMember(memberDef)
          } toSet
        }
       PreparationResult(t, membersToImplement.filterNot(implementedMembers contains _))
      }
    } getOrElse Left {
      PreparationError("No target class in selection")
    }
  }

  override type RefactoringParameters = Unit

  override def perform(selection: Selection, prepared: PreparationResult, params: RefactoringParameters) = {
    import prepared._

    val findTemplate = filter {
      case t: Template => t == targetTemplate
    }

    val addMethods = transform {
      case tpl @ Template(_,  _, body) if tpl == prepared.targetTemplate =>
        val unimplementedSentence = Ident("???")
        val methodWithRhs = memberToImplement collect {
          case methodDef: DefDef =>
            methodDef copy (rhs = Block(unimplementedSentence :: Nil, EmptyTree))
          case valueDef: ValDef =>
            valueDef copy (rhs = unimplementedSentence)
        }
        tpl.copy(body = body ++ methodWithRhs).replaces(tpl)
    }

    val transformation = topdown {
      matchingChildren {
        findTemplate &>
        addMethods
      }
    }
    Right(transformFile(selection.file, transformation))
  }
}
