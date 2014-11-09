package tql


/**
 * Created by Eric on 20.10.2014.
 */

trait Combinators[T] { self: Traverser[T] =>

  import scala.reflect.ClassTag
  import scala.collection.generic.CanBuildFrom

  /**
   * Traverse the children of the tree
   * */
  def children[A : Monoid](f: TreeMapper[A]) =  TreeMapper[A]{ tree =>
    traverse(tree, f)
  }

  /**
   * Traverse the tree in a TopDown manner, stop when a transformation/traversal has succeeded
   * */
  def downBreak[A : Monoid](m: TreeMapper[A]): TreeMapper[A] =
    m | children(downBreak[A](m))

  /**
   * Traverse the tree in a BottomUp manner, stop when a transformation/traversal has succeeded
   * */
  def upBreak[A : Monoid](m: TreeMapper[A]): TreeMapper[A] =
    children(upBreak[A](m)) | m

  /**
   * Same as TopDown, but does not sop when a transformation/traversal has succeeded
   * */
  def down[A : Monoid](m: TreeMapper[A]): TreeMapper[A] =
    m + children(down[A](m))

  /**
   * Same as upBreak, but does not sop when a transformation/traversal has succeeded
   * */
  def up[A : Monoid](m: TreeMapper[A]): TreeMapper[A] =
    children(up[A](m)) + m

  def flatMap[B](f: T => MatcherResult[B]) = TreeMapper[B] {tree =>
    f(tree)
  }

  def visit[A](f: PartialFunction[T, A])(implicit x: ClassTag[T]) =
    guard[T]{case t => f.isDefinedAt(t)} map(f(_))


  def stateful[A, B](init: => A)(f: (=>A) => TreeMapper[(B, A)]): TreeMapper[B] = {
    var state = init
    f(state) map {case (res, s) =>
      state = s
      res
    }
  }

  /**
   * Succeed if the partial function f applied on the tree is defined and return true
   * */
  def guard[U <: T : ClassTag](f: PartialFunction[U, Boolean]) = TreeMapper[U]{
    case t: U if f.isDefinedAt(t) && f(t) => Some((t, t))
    case _ => None
  }

  /**
   * Same as filter but puts the results into a list
   * */
  def collect[A](f: PartialFunction[T, A])(implicit x: ClassTag[T]): TreeMapper[List[A]] =
    guard[T]{case t => f.isDefinedAt(t)} map (x => List(f(x)))

  def collectIn[C[_]] = new {
    def apply[A](f: PartialFunction[T, A])(implicit  x: ClassTag[T], y: CanBuildFrom[C[A], A, C[A]]) =
      guard[T]{case t => f.isDefinedAt(t)} map(t => (y() += f(t)).result)
  }

  def collectIn2[V[_, _]] = new {
    def apply[A, B](f: PartialFunction[T, (A, B)])(implicit  x: ClassTag[T], y: CanBuildFrom[V[A, B], (A, B), V[A, B]]) =
      guard[T]{case t => f.isDefinedAt(t)} map(t => (y() += f(t)).result)
  }

  /**
   *  Transform a I into a T where both I and O are subtypes of T and where a transformation from I to O is authorized
   * */
  def transform[I <: T : ClassTag, O <: T](f: PartialFunction[I, O])(implicit x: AllowedTransformation[I, O]) =
    TreeMapper[Unit] {
      case t: I if f.isDefinedAt(t) => Some((f(t), Monoid.Void.zero))
      case _ => None
    }

  import scala.language.experimental.macros

  def filter(f: PartialFunction[T, Boolean]): TreeMapper[T] = macro CombinatorsSugar.filterSugarImpl[T]
  def update(f: PartialFunction[T, T]): TreeMapper[Unit] = macro CombinatorsSugar.updateSugarImpl[T]

}