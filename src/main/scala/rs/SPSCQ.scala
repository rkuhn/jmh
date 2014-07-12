package rs

import java.util.Queue
import sun.misc.Contended
import java.security.PrivilegedExceptionAction
import sun.misc.Unsafe
import java.security.AccessController

/**
 * This should be a nice SPSC queue, but alas, it does not really work as fast
 * as it should; maybe @Contended does not do (yet?) what I think it should?
 * (Will need to research)
 */
class SPSCQ[T <: AnyRef](size: Int) extends Queue[T] {
  import SPSCQ._

  @Contended private var headCache = 0L
  @Contended private var tailCache = 0L
  @Contended @volatile private var _head = 0L
  @Contended @volatile private var _tail = 0L
  private def head = unsafe.getLongVolatile(this, headOffset)
  private def head_=(l: Long) = unsafe.putOrderedLong(this, headOffset, l)
  private def tail = unsafe.getLongVolatile(this, tailOffset)
  private def tail_=(l: Long) = unsafe.putOrderedLong(this, tailOffset, l)

  private val capacity = 1 << (32 - Integer.numberOfLeadingZeros(size - 1))
  private val mask = capacity - 1
  private val buffer = new Array[AnyRef](capacity)

  override def offer(elem: T): Boolean = {
    if (tail - headCache >= capacity) headCache = head
    if (tail - headCache >= capacity) false
    else {
      if (elem eq null) throw new NullPointerException("elem must not be NULL")
      buffer((tail & mask).toInt) = elem
      tail += 1
      true
    }
  }
  override def poll(): T = {
    if (tailCache <= head) tailCache = tail
    if (tailCache <= head) null.asInstanceOf[T]
    else {
      val pos = (head & mask).toInt
      val elem = buffer(pos).asInstanceOf[T]
      buffer(pos) = null
      head += 1
      elem
    }
  }
  override def size: Int = ???
  override def isEmpty: Boolean = ???
  override def add(elem: T): Boolean = ???
  override def remove(): T = ???
  override def peek: T = ???
  override def element: T = ???
  override def addAll(x$1: java.util.Collection[_ <: T]): Boolean = ???
  override def clear(): Unit = ???
  override def contains(x$1: Any): Boolean = ???
  override def containsAll(x$1: java.util.Collection[_]): Boolean = ???
  override def iterator(): java.util.Iterator[T] = ???
  override def remove(x$1: Any): Boolean = ???
  override def removeAll(x$1: java.util.Collection[_]): Boolean = ???
  override def retainAll(x$1: java.util.Collection[_]): Boolean = ???
  override def toArray[T](x$1: Array[T with Object]): Array[T with Object] = ???
  override def toArray(): Array[Object] = ???
}

object SPSCQ {
  val unsafe =
    try {
      val action = new PrivilegedExceptionAction[Unsafe] {
        override def run: Unsafe = {
          val f = classOf[Unsafe].getDeclaredField("theUnsafe")
          f.setAccessible(true)
          f.get(null).asInstanceOf[Unsafe]
        }
      }
      AccessController.doPrivileged(action)
    } catch {
      case t: Throwable ⇒ throw new RuntimeException(t)
    }
  val headOffset = unsafe.objectFieldOffset(classOf[SPSCQ[_]].getDeclaredField("_head"))
  val tailOffset = unsafe.objectFieldOffset(classOf[SPSCQ[_]].getDeclaredField("_tail"))
}