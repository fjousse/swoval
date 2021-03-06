package com.swoval.files

import utest.framework.ExecutionContext.RunNow

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ Future, Promise }
import scala.util.{ Success, Try }
import java.nio.file.{ Path => JPath }

package object test {
  class CountDownLatch(private[this] var i: Int) {
    private[this] val promise = Promise.apply[Boolean]
    private[this] val lock = new Object
    def countDown(): Unit = lock.synchronized {
      i -= 1
      if (i == 0) {
        promise.complete(Success(true))
      }
    }
    def getCount = i
    def waitFor[R](duration: FiniteDuration)(f: => R): Future[R] = {
      val tp: TimedPromise[Boolean] = platform.newTimedPromise(promise, duration)
      promise.future.map { r =>
        tp.tryComplete(Success(r))
        f
      }
    }
  }
  trait TimedPromise[T] {
    def tryComplete(r: Try[T]): Unit
  }
  class ArrayBlockingQueue[T](size: Int) {
    private[this] val queue: mutable.Queue[T] = mutable.Queue.empty
    private[this] val promises: mutable.Queue[TimedPromise[T]] = mutable.Queue.empty
    private[this] val lock = new Object
    def poll[R](timeout: FiniteDuration)(f: T => R): Future[R] =
      lock.synchronized(queue.headOption match {
        case Some(_) => Future.successful(f(queue.dequeue()))
        case _ =>
          val p = Promise[T]
          promises.enqueue(platform.newTimedPromise(p, timeout))
          p.future.map(f)(RunNow)
      })
    def add(t: T): Unit = lock.synchronized {
      promises.dequeueFirst(_ => true) match {
        case Some(promise) =>
          promise.tryComplete(Success(t))
        case _ =>
          queue.enqueue(t)
      }
    }
  }
  def withTempFile[R](dir: JPath)(f: JPath => Future[R]): Future[Unit] =
    com.swoval.test.Files.withTempFile(dir.toString)(s => f(Path(s)).map(_ => ()))

  def withTempFile[R](f: JPath => Future[R]): Future[Unit] =
    com.swoval.test.Files.withTempFile(s => f(Path(s)).map(_ => ()))

  def withTempDirectory[R](f: JPath => Future[R]): Future[Unit] =
    com.swoval.test.Files.withTempDirectory(d => f(Path(d)).map(_ => ()))

  def withTempDirectory[R](dir: JPath)(f: JPath => Future[R]): Future[Unit] =
    com.swoval.test.Files.withTempDirectory(dir.toString)(d => f(Path(d)).map(_ => ()))

  def withDirectory[R](dir: JPath)(f: => Future[R]): Future[Unit] =
    com.swoval.test.Files.withDirectory(dir.toString)(f.map(_ => ()))

  def wrap[R](f: JPath => R): JPath => Future[Unit] = (path: JPath) => {
    val p = Promise[Unit]()
    p.tryComplete(util.Try { f(path); () })
    p.future
  }
  def withTempFileSync[R](dir: JPath)(f: JPath => R): Future[Unit] =
    withTempFile(dir)(wrap(f))

  def withTempFileSync[R](f: JPath => R): Future[Unit] = withTempFile(wrap(f))

  def withTempDirectorySync[R](f: JPath => R): Future[Unit] = withTempDirectory(wrap(f))

  def withTempDirectorySync[R](dir: JPath)(f: JPath => R): Future[Unit] =
    withTempDirectory(dir)(wrap(f))

  def withDirectorySync[R](dir: JPath)(f: => R): Future[Unit] =
    com.swoval.test.Files.withDirectory(dir.toString) {
      val p = Promise[Unit]
      p.tryComplete(util.Try { f; () })
      p.future
    }
}
