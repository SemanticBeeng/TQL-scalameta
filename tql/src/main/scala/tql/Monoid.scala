package tql

/**
 * Created by Eric on 20.10.2014.
 */

import scala.language.higherKinds
/**
 * https://en.wikipedia.org/wiki/Monoid
 * */
trait Monoid[A]{
  def zero : A
  def append(a: A, b: A): A
}

object Monoid {

  import scala.collection.generic._

  //note: http://stackoverflow.com/questions/15623585/why-is-list-a-semigroup-but-seq-is-not
  implicit def listMonoid[A] = new Monoid[List[A]] {
    def zero = Nil
    def append(a: List[A], b: List[A]) = a ::: b
  }

  implicit def setMonoid[A] = new Monoid[Set[A]] {
    def zero = Set.empty[A]
    def append(a: Set[A], b: Set[A]) = a ++ b
  }


  implicit def traversableMonoid[A, B[A] <: Traversable[A]](implicit y: CanBuildFrom[B[A], A, B[A]]) =
    new Monoid[B[A]] {
      def zero = y.apply.result
      def append(a: B[A], b: B[A]): B[A] =
        if (a.size == 0) b
        else if (b.size == 0) a
        else {
          val x = y.apply
          x ++= a
          x ++= b
          x.result
        }
  }

  implicit def mapMonoid[A, C, B[A, C] <: Traversable[(A, C)]](implicit y: CanBuildFrom[B[A, C], (A, C), B[A, C]]) =
    new Monoid[B[A, C]] {
      def zero = y.apply.result
      def append(a: B[A, C], b: B[A, C]): B[A, C] = {
        val x = y.apply
        x ++= a
        x ++= b
        x.result
      }
  }

  implicit object Void extends Monoid[Unit]{
    def zero = ()
    def append(a: Unit, b: Unit) = ()
  }

  //TODO create a macro ?
  implicit def tupleMonoid[A : Monoid, B : Monoid] = new Monoid[(A, B)] {
    import MonoidEnhencer._

    def zero = (implicitly[Monoid[A]].zero,  implicitly[Monoid[B]].zero)
    def append(a: (A, B), b: (A, B)) = (a._1 + b._1, a._2 + b._2)
  }
}