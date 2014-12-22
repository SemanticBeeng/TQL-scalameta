package tql

/**
 * Created by Eric on 20.12.2014.
 */

/**
 * Allows to specify that the type A must be different from type B.
 * Use the same as with =:=
 * */
object NotEquivTypes {
  //thanks to http://stackoverflow.com/questions/6909053/enforce-type-difference/17047288#17047288
  @annotation.implicitNotFound(msg = "Cannot prove that ${A} =!= ${B}.")
  trait =!=[A,B]
  object =!= {
    class Impl[A, B]
    object Impl {
      implicit def neq[A, B] : A Impl B = null
      implicit def neqAmbig1[A] : A Impl A = null
      implicit def neqAmbig2[A] : A Impl A = null
    }

    implicit def foo[A,B]( implicit e: A Impl B ): A =!= B = null
  }
}
