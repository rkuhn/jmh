package rs

import org.openjdk.jmh.annotations._
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.concurrent.atomic.AtomicLong
import java.util.Queue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import uk.co.real_logic.OneToOneConcurrentArrayQueue

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(3)
@Warmup(iterations = 8)
@Measurement(iterations = 5)
class Bounded {

  trait M
  case object Message extends M

  class Pub extends Publisher[M] {
    override def subscribe(s: Subscriber[M]): Unit = {
      sub = new Subscr(s)
      s.onSubscribe(sub)
    }

    private var sub: Subscr = _
    private var nodemand = 0L
    def push(elem: M): Unit = {
      if (sub.hasDemand) sub.subscriber.onNext(elem)
      else nodemand += 1
    }
    
    def end(): Unit = Stats.nodemand = nodemand

    private class Subscr(val subscriber: Subscriber[M]) extends AtomicLong with Subscription {
      override def cancel(): Unit = {}
      override def request(n: Int): Unit = getAndAdd(n)

      private var cachedDemand = 0L
      def hasDemand(): Boolean = {
        var c = cachedDemand
        if (c == 0) {
          cachedDemand = getAndSet(0)
          c = cachedDemand
        }
        if (c > 0) {
          cachedDemand = c - 1
          true
        } else false
      }
    }
  }

  class Sub(batchSize: Int) extends Subscriber[M] {
    private var consumer: Thread = _
    private val queue: Queue[M] = new OneToOneConcurrentArrayQueue[M](batchSize * 2)
    private val started = new CountDownLatch(1)
    private var full = 0L

    override def onSubscribe(sub: Subscription): Unit = {
      consumer = new Consumer(sub)
      consumer.start()
      started.await()
    }
    override def onNext(elem: M): Unit = {
      if (!queue.offer(elem)) full += 1
    }
    override def onError(cause: Throwable): Unit = {
      consumer.interrupt()
    }
    override def onComplete(): Unit = {
      consumer.interrupt()
      consumer.join()
      Stats.queuefull = full
    }

    class Consumer(sub: Subscription) extends Thread {
      private var consumed = 0
      override def run(): Unit = {
        sub.request(batchSize * 2)
        started.countDown()
        while (!Thread.currentThread().isInterrupted()) {
          queue.poll() match {
            case null ⇒ spin()
            case m ⇒
              assert(m == Message)
              consumed += 1
              if (consumed == batchSize) {
                consumed = 0
                sub.request(batchSize)
              }
          }
        }
      }
      @volatile private var x = 0
      private def spin(): Unit = {
        x = 1000
        while (x > 0) x -= 1
      }
    }
  }

  var publisher: Pub = null
  @Param(Array("1", "10", "100", "1000", "10000", "100000")) var batchSize = 0
  var subscriber: Sub = null
  var offered = 0L
  
  object Stats {
    var offered = 0L
    var queuefull = 0L
    var nodemand = 0L
    
    override def toString = s"elements offered: $offered   no demand: $nodemand (${100*nodemand/offered}%)   queue full: $queuefull (${100*queuefull/offered}%)"
  }

  @Setup(Level.Iteration) def setup(): Unit = {
    publisher = new Pub
    subscriber = new Sub(batchSize)
    publisher.subscribe(subscriber)
    offered = 0
  }

  @TearDown(Level.Iteration) def teardown(): Unit = {
    publisher.end()
    subscriber.onComplete()
    Stats.offered = offered
    println(Stats)
  }

  @Benchmark def push(): Unit = {
    publisher.push(Message)
    offered += 1
  }

}