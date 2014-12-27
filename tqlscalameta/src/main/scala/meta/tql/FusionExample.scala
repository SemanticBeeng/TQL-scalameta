package scala.meta.tql

/**
 * Created by Eric on 09.12.2014.
 */

import scala.language.reflectiveCalls
import ScalaMetaFusionTraverser._
import scala.meta.internal.ast._

object FusionExample extends App{

  val x = {
    import scala.meta._
    q"""
       val a = 1
       val c = 3
       c = 5
       if (3 == 17) {
        val c = 1
       }
       else 2
       5
    """
  }


  val getAllIntLT5 = down(optimize{collect[Set]{case Lit.Int(a) if a < 5 => a}})
  val getAllIntGT5 = down(optimize{collect[Set]{case Lit.Int(a) if a > 5 => a}})

  val both = getAllIntLT5 + getAllIntGT5

  println(both(x).result)
}