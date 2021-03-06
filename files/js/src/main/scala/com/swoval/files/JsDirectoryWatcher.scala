package com.swoval.files

import java.nio.file.Paths

import com.swoval.files.DirectoryWatcher.{ Callback, Event }

import scala.collection.mutable
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel

@JSExportTopLevel("com.swoval.files.DirectoryWatcher")
class JsDirectoryWatcher(callback: js.UndefOr[js.Function2[String, String, Unit]])
    extends js.Object {
  private[this] val callbacks = new Callbacks()
  callback.toOption.foreach(addCallback)
  private[this] val inner: DirectoryWatcher = DirectoryWatcher.defaultWatcher(callbacks)
  def close(): Unit = inner.close()
  def register(path: String, recursive: Boolean = true): Unit =
    inner.register(Paths.get(path), recursive)
  def addCallback(callback: js.Function2[String, String, Unit]): Int =
    callbacks.addCallback(new Callback {
      override def apply(event: Event): Unit =
        callback.apply(event.path.toString, event.kind.toString)
    })
  def removeCallback(handle: Int): Unit = callbacks.removeCallback(handle)
}

private class Callbacks extends Callback {
  private[this] var id = 0
  private[this] val callbacks: mutable.Map[Int, Callback] = mutable.Map.empty
  override def apply(event: DirectoryWatcher.Event): Unit = {
    callbacks.values.foreach(_.apply(event))
  }
  def addCallback(callback: Callback): Int = {
    val newID = id
    id += 1
    callbacks += id -> callback
    id
  }
  def removeCallback(handle: Int): Unit = {
    callbacks -= handle
  }
}
