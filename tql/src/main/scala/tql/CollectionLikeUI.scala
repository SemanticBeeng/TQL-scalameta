package tql

/**
 * Created by Eric on 16.11.2014.
 */

import scala.language.higherKinds
import scala.language.reflectiveCalls
import scala.language.experimental.macros

import scala.reflect.ClassTag
import NotEquivTypes._

/**
 * This trait allows to easily write simple traversals.
 * Instead of writing:
 *  t : T
 *  val x = topDown(collect{...})
 *  val result = x(t).result
 * We can wirte instead
 *  t.collect{...}
 *
 *  topDownBreak(focus{..} ~> topDown{transform{...}}) (t)
 *  becomes
 *  t.topDownBreak.focus{..}.topDown.transform{..}
 *  Which is essentially easier to read and to write
 *
 *  - transform return either
 *    - just a T => x: T = t.transform{case Lit.Int(a) => Lit.Int(a * 2)}
 *    - a tuple (T, A) => (x: T, y: List[Int]) = t.transform{case Lit.Int(a) => Lit.Int(a * 2) andCollect a}
 *  - it is possible to use it on
 *    - t: T => t.transform{case Lit.Int(a) => Lit.Int(a * 2)}: T
 *    - t: Option[T] => t.transform{case Lit.Int(a) => Lit.Int(a * 2)}: Option[T]
 *    - t: List[T] => t.transform{case Lit.Int(a) => Lit.Int(a * 2)}: List[T]
 *
 *  The drawbacks:
 *    - Every combinator have to be re-written in those 'Evaluator' classes (even macros)
 *    - Composition is lost.
 *
 * /!\ Beware: this code contains a lot of implcit magic tricks
 * */
trait CollectionLikeUI[T] { self: Combinators[T] with Traverser[T] with SyntaxEnhancer[T] =>

  /**Abstract class used to delay delay the time when the type parameter of
    * a meta combinator is decided.
    * Anonymous functions can't take type parameter, so this ugly stuff is required
    * */
  abstract class DelayedMeta{
    def apply[A : Monoid](m: Matcher[A]): Matcher[A]
  }

  /**
   * System needed to recover the correct type from a 'transfrom' call.
   * 1) x.transform{case x: T => x} : T
   * 2) x.transform{case x: T => (x, List(1))} : (T, List[Int])
   * */
  trait TransformResultTr[A, R]{
    def get(t: T, x: MatchResult[A]): R
  }

  object TransformResultTr{
    //for 1) the case where the returned type is Unit
    implicit val unitRes = new TransformResultTr[Unit, T] {
      def get(t: T, x: MatchResult[Unit]): T  = x.tree.getOrElse(t)
    }

    //for 2) the case where the returned type is not Unit
    implicit def withRes[A: Monoid](implicit ev: A =!= Unit) = new TransformResultTr[A, (T, A)] {
      def get(t: T, x: MatchResult[A]): (T, A)  = (x.tree.getOrElse(t), x.result)
    }
  }


  /**
   * Make it possible to use the collection like ui api inside different structures
   * A := Result of the Matcher
   * R := Some anonymous type which represents the result of a transformation on each MatchResult
   * V := T | Option[T] | List[T]
   * L := R | Option[R] | List[R]
   * */
  trait MatcherApply[A, R, V, L] {
    def apply(value: V)(m: Matcher[A])(f: (T, MatchResult[A]) => R): L
  }

  object MatcherApply {
    //Base case
    implicit def direct[A, R, U <: T, L] = new MatcherApply[A, R, U, R] {
      def apply(value: U)(m: Matcher[A])(f: (T, MatchResult[A]) => R): R = f(value, m.apply(value))
    }

    //Make generic to Monad, Functor, Applicative, Monoid.. ?
    implicit def toOpt[A, R, U <: T, L] = new MatcherApply[A, R, Option[U], Option[R]] {
      def apply(value: Option[U])(m: Matcher[A])(f: (T, MatchResult[A]) => R): Option[R] =
        value.map(x => f(x, m.apply(x)))
    }

    //TODO make more generic (if needed?) to take into account, Set, Seq, Vector..
    implicit def toList[A, R, U <: T, L] = new MatcherApply[A, R, List[U], List[R]] {
      def apply(value: List[U])(m: Matcher[A])(f: (T, MatchResult[A]) => R): List[R] =
        value.map(x => f(x, m.apply(x)))
    }
  }

  /**
   * Allows to call 'combinators' directly on T
   * For documentation see Combinators.scala
   * */
  implicit class Evaluator[V](value: V) {

    //combinator interfaces (see documentation for combinators)
    def collect[C[_]] = new  {
     def apply[A, R, L](f: PartialFunction[T, A])
                       (implicit x: ClassTag[T],
                        y: Collector[C[A], A, R],
                        z: Monoid[R],
                        l: MatcherApply[R, R, V, L]) =
       topDown.collect[C](f)
    }

    def focus(f: PartialFunction[T, Boolean]): EvaluatorAndThen[V, T] = macro CollectionLikeUISugar.filterSugarImpl[T]

    def transform(f: PartialFunction[T, Any]): Any =
      macro CollectionLikeUISugar.transformSugarImplWithTRtype[T]

    /**
     * Allows to use other combinators which are not defined in the CollectionLikeUI framework
     * */
    def combine[B](x: Matcher[B]) = topDown.combine(x)

    //traversal strategies
    def topDown =
      new EvaluatorMeta(value, new DelayedMeta{def apply[A : Monoid](x: Matcher[A]) = self.topDown(x)})
    def topDownBreak =
      new EvaluatorMeta(value, new DelayedMeta{def apply[A : Monoid](x: Matcher[A]) = self.topDownBreak(x)})
    def bottomUp =
      new EvaluatorMeta(value, new DelayedMeta{def apply[A : Monoid](x: Matcher[A]) = self.bottomUp(x)})
    def bottomUpBreak =
      new EvaluatorMeta(value, new DelayedMeta{def apply[A : Monoid](x: Matcher[A]) = self.bottomUpBreak(x)})
    def children =
      new EvaluatorMeta(value, new DelayedMeta{def apply[A : Monoid](x: Matcher[A]) = self.children(x)})

    //methods required to implement the above interface
    def guard[U <: T : ClassTag](f: PartialFunction[U, Boolean]) = topDown.guard(f)
    /**
     * This is required for the transform macro as it cannot access self.transformWithResult by itself
     * */
    def transformWithResult[I <: T : ClassTag, O <: T, A]
                            (f: PartialFunction[I, (O, A)])
                            (implicit x: AllowedTransformation[I, O]) = self.transformWithResult(f)

    def transforms[A : Monoid, R, L]
                  (f: Matcher[A])
                  (implicit r: TransformResultTr[A, R],
                  l: MatcherApply[A, R, V, L]) = topDown.transforms(f)

  }

  /**
   * Evaluator at which will be applied the traversal strategy defined in 'meta'
   * */
  class EvaluatorMeta[V](value: V, meta: DelayedMeta){

    def collect[C[_]] = new {
      def apply[A, R, L](f: PartialFunction[T, A])
                        (implicit x: ClassTag[T],
                         y: Collector[C[A], A, R],
                         z: Monoid[R],
                         l: MatcherApply[R, R, V, L])  =
        l(value)(meta(self.collect[C](f)))((t, m) => m.result)
    }

    def transform(f: PartialFunction[T, Any]): Any =
      macro CollectionLikeUISugar.transformSugarImplWithTRtype[T]

    def focus(f: PartialFunction[T, Boolean]): EvaluatorAndThen[V, T] =
      macro CollectionLikeUISugar.filterSugarImpl[T]

    /**
     * Allows to use other combinators which are not defined in the CollectionLikeUI framework
     * */
    def combine[B](x: Matcher[B]) = new EvaluatorAndThen[V, B](value, x, meta)

    /**
     * This is required for the transform macro as it cannot access self.transformWithResult by itself
     * */
    def transformWithResult[I <: T : ClassTag, O <: T, A]
                            (f: PartialFunction[I, (O, A)])
                            (implicit x: AllowedTransformation[I, O]) = self.transformWithResult(f)

    def transforms[A : Monoid, R, L]
                  (f: Matcher[A])
                  (implicit r: TransformResultTr[A, R],
                   l: MatcherApply[A, R, V, L]) =
                    l(value)(meta(f))((t, m) => r.get(t, m))

    def guard[U <: T : ClassTag](f: PartialFunction[U, Boolean]) =
      new EvaluatorAndThen(value, self.guard(f), meta)
  }


  /**
   * Evaluator at which will be applied m andThen the traversal strategy defined in 'meta'
   * */
  class EvaluatorAndThen[V, +A]( private[CollectionLikeUI] val value: V,
                                 private[CollectionLikeUI] val m: Matcher[A],
                                 private[CollectionLikeUI] val meta: DelayedMeta){
    def collect[C[_]] = new {
      def apply[A, R, L](f: PartialFunction[T, A])
                        (implicit x: ClassTag[T],
                         y: Collector[C[A], A, R],
                         z: Monoid[R],
                         l: MatcherApply[R, R, V, L])  =
        l(value)(meta(self.collect[C](f)))((t, m) => m.result)
    }


    def focus(f: PartialFunction[T, Boolean]): EvaluatorAndThen[V, T] =
      macro CollectionLikeUISugar.filterSugarImpl[T]


    def transform(f: PartialFunction[T, Any]): Any =
      macro CollectionLikeUISugar.transformSugarImplWithTRtype[T]

    def combine[B](x: Matcher[B]) = new EvaluatorAndThen[V, B](value, m ~> x, meta)


    def topDown =
      new EvaluatorMeta(value, new DelayedMeta{def apply[A : Monoid](x: Matcher[A]) = meta(m ~> self.topDown(x))})
    def topDownBreak =
      new EvaluatorMeta(value, new DelayedMeta{def apply[A : Monoid](x: Matcher[A]) = meta(m ~> self.topDownBreak(x))})
    def bottomUp =
      new EvaluatorMeta(value, new DelayedMeta{def apply[A : Monoid](x: Matcher[A]) = meta(m ~> self.bottomUp(x))})
    def bottomUpBreak =
      new EvaluatorMeta(value, new DelayedMeta{def apply[A : Monoid](x: Matcher[A]) = meta(m ~> self.bottomUpBreak(x))})
    def children =
      new EvaluatorMeta(value, new DelayedMeta{def apply[A : Monoid](x: Matcher[A]) = meta(m ~> self.children(x))})


    /**
     * This is required for the transform macro as it cannot access self.transformWithResult by itself
     * */
    def transformWithResult[I <: T : ClassTag, O <: T, A]
                          (f: PartialFunction[I, (O, A)])
                          (implicit x: AllowedTransformation[I, O]) = self.transformWithResult(f)

    def transforms[A : Monoid, R, L]
                  (f: Matcher[A])
                  (implicit r: TransformResultTr[A, R],
                   l: MatcherApply[A, R, V, L]) =
      l(value)(meta(f))((t, m) => r.get(t, m))

    def guard[U <: T : ClassTag](f: PartialFunction[U, Boolean]) =
      new EvaluatorAndThen(value, m ~> self.guard(f), meta)
  }

  /**
   * This has to be outside of EvaluatorAndThen because of covarience stuff it is not possible to write
   * def force(implicit x: Monoid[A]) = ...inside EvaluatorAndThen[A]
   * We should write def force[B >: A](implicit x: Monoid[B]) but Monoid should be made contravarient in A,
   * which is not possible (in part because it is not logical and because contravarient stuff does not work well
   * with implicits)
   * */
  implicit class ForceResult[V, A : Monoid, R](x : EvaluatorAndThen[V, A]){
    def force[L](implicit l: MatcherApply[A, MatchResult[A], V, L]) = l(x.value)(x.meta(x.m))((t, m) => m)
    def result[L](implicit l: MatcherApply[A, A, V, L]) = l(x.value)(x.meta(x.m))((t, m) => m.result)
    def tree[L](implicit l: MatcherApply[A, T, V, L]) = l(x.value)(x.meta(x.m))((t, m) => m.tree.getOrElse(t))
  }
}
