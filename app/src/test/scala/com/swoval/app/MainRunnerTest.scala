package com.swoval.app

import com.swoval.reflect.Duck
import utest._

import scala.concurrent.duration._

object StaticMain {
  def shutdown(): Unit = {}
  def waitForShutdown(duration: Object): Boolean = {
    println(s"waiting for $duration")
    true
  }
  def run(args: Array[String]): Int = {
    args.head.toInt
  }
}

class SlowTask extends Shutdownable {
  var interrupted: Boolean = false
  var isShutdown = false
  override def shutdown(): Unit = isShutdown = true
  def doSlow(args: Array[String]): Int = {
    try {
      Thread.sleep(1.minute.toMillis)
      3
    } catch {
      case _: InterruptedException =>
        interrupted = true
        6
    }
  }
}

object MainRunnerTest extends TestSuite {
  val s = implicitly[Duck[StaticMain.type]]
  println(s.shutdownable(StaticMain).waitForShutdown(Duration.Inf))
  val tests = Tests {
    'run - {
      MainRunner.run[Int](Array("--main", "com.swoval.app.StaticMain", "3")) ==> 3
    }
    'cancel - {
      val handle = MainRunner.setup[SlowTask](Array("--main", "com.swoval.app.SlowTask")).run[Int]()
      handle.cancel()
      val instance = handle.getInstance
      assert(instance.interrupted)
      assert(instance.isShutdown)
      handle.result() ==> 6
    }
  }
}
