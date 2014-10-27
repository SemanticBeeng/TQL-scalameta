package scalameta

import scala.meta.Lit

/**
 * Created by Eric on 20.10.2014.
 */
object Example extends App{

  import tql._
  import scala.meta._
  import syntactic._
  import ScalaMetaTraverser._
  import CombinatorsSugar._
  import Monoids._

  def showTree(x: Tree) = show.ShowOps(x).show[syntactic.show.Raw]

  val x =
    q"""
       if (1 == 1) 1
       else 2
       5
       """

  val getAllIntLits = downBreak(collect{case Lit.Int(a) => 2})

  println(x)

  //val changeAllIntLits = deep(update{case q"${_ : Int}" => q"17"})
  val changeAllIntLits = downBreak(updateE[Term, Lit]{case Lit.Int(_) => q"165"})

  val withState = downBreak(stateful[List[Int]] { state =>
    collect{ case Lit.Int(a) => println(state);2}
  })

  println(withState(x))
  println(getAllIntLits(x))
  println(changeAllIntLits(x))


  //println(x \\ (filter{case i : Term.If=> true} \\ update{case Lit.Int(_) => q"15"}))

  //filtersugar({case i:Term.If => true; case i: Term.Apply => false})

}
