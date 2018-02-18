package com.swoval.reflect

import scala.reflect.macros.blackbox
import scala.language.experimental.macros

object Thunk {
  def apply[T](thunk: T, strict: Boolean): T = macro ThunkMacros.implStrict[T]
  def apply[T](thunk: T): T = macro ThunkMacros.impl[T]
}

object ThunkMacros {
  def implStrict[T: c.WeakTypeTag](c: blackbox.Context)(thunk: c.Expr[T],
                                                        strict: c.Expr[Boolean]): c.Expr[T] = {
    import c.universe._
    val tree = q"if ($strict) $thunk else ${impl(c)(thunk)}"
    c.Expr(tree)
  }
  def impl[T: c.WeakTypeTag](c: blackbox.Context)(thunk: c.Expr[T]): c.Expr[T] = {
    import c.universe._
    val helpers = new MacroHelpers[c.type](c)
    import helpers._
    def loader = {
      c.inferImplicitValue(weakTypeOf[ChildFirstClassLoader], silent = true) match {
        case q"" =>
          q"""
          Thread.currentThread.getContextClassLoader match {
            case l: ChildFirstClassLoader => l.dup()
            case l                        => new ChildFirstClassLoader(Array.empty, l)
          }
          """
        case l => l
      }
    }
    def fresh(name: String) = TermName(c.freshName(name))
    type Args = Seq[Seq[Tree]]
    case class Arg(tree: c.Tree, name: TermName, clazz: c.Tree, boxed: c.Tree)
    object Arg {
      def apply(arg: c.Tree): Arg = {
        val tpe = arg.tpe
        val name = fresh(tpe.typeSymbol.name.encodedName.toString.toLowerCase)
        lazy val classOfType = q"classOf[${qualified(tpe, isType = true)}]"

        tpe.erasure match {
          case t if t <:< weakTypeOf[Boolean] => Arg(arg, name, classOfType, box(name, t))
          case t if t <:< weakTypeOf[Byte]    => Arg(arg, name, classOfType, box(name, t))
          case t if t <:< weakTypeOf[Char]    => Arg(arg, name, classOfType, box(name, t))
          case t if t <:< weakTypeOf[Double]  => Arg(arg, name, classOfType, box(name, t))
          case t if t <:< weakTypeOf[Float]   => Arg(arg, name, classOfType, box(name, t))
          case t if t <:< weakTypeOf[Int]     => Arg(arg, name, classOfType, box(name, t))
          case t if t <:< weakTypeOf[Long]    => Arg(arg, name, classOfType, box(name, t))
          case t if t <:< weakTypeOf[Short]   => Arg(arg, name, classOfType, box(name, t))
          case _                              => Arg(arg, name, q"$name.getClass", q"$name")
        }
      }
    }
    def moduleApply(obj: Tree, method: TermName, args: Args) = {
      val moduleName = s"${obj.tpe.termSymbol.asModule.fullName}$$"
      val loaderName = fresh("loader")
      val parsedArgs = args.flatten.map(Arg(_))
      val classes = parsedArgs.map(_.clazz)
      val module = fresh("module")
      val instanceName = fresh("instanceName")
      val methodName = fresh("methodName")
      val tree = q"""
        val $loaderName = $loader
        val $module = $loaderName.loadClass($moduleName)
        ..${parsedArgs.map(a => q"val ${a.name} = ${a.tree}")}
        val $instanceName = $module.getDeclaredField("MODULE$$").get(null)
        val $methodName = $module.getDeclaredMethod(${method.toString}, ..$classes)
        $methodName.invoke($instanceName, ..${parsedArgs.map(_.boxed)})
      """
      tree
    }
    def withClass(clazz: Tree, args: Args)(f: (TermName, TermName) => Tree) = {
      val className = clazz.tpe.typeSymbol.fullName
      val loaderName = fresh("loader")
      val classInstance = fresh("class")
      val instanceName = fresh("instance")
      val parsedArgs = args.flatten.map(Arg(_))
      val classes = parsedArgs.map(_.clazz)
      val boxed = parsedArgs.map(_.boxed)
      val tree = q"""
        val $loaderName = $loader
        val $classInstance = $loaderName.loadClass($className)
        val $instanceName = $classInstance.getConstructor(..$classes).newInstance(..$boxed)
        ${f(instanceName, classInstance)}
      """
      tree
    }
    def classApply(clazz: Tree, args: Args, method: TermName, methodArgs: Args) = {
      val parsedArgs = methodArgs.flatten.map(Arg(_))
      val methodClasses = parsedArgs.map(_.clazz)
      val className = fresh("method")
      withClass(clazz, args)((instanceName, routesClass) => q"""
        val $className = $routesClass.getDeclaredMethod(${method.toString}, ..$methodClasses)
        $className.invoke($instanceName, ..${parsedArgs.map(_.boxed)})
      """)
    }
    def cast(tree: Tree): Tree = q"$tree.asInstanceOf[${weakTypeOf[T]}]"
    val tree = cast(thunk.tree match {
      case q"${obj: Tree }.${method: TermName }(...${args: Args })"
          if obj.isTerm && obj.tpe.termSymbol.isModule =>
        moduleApply(obj, method, args)
      case q"new ${clazz: Tree }(...${args: Args })" if clazz.tpe <:< weakTypeOf[T] =>
        withClass(clazz, args)((i, _) => q"$i")
      case q"new ${clazz: Tree }(...${args: Args }).${method: TermName }(...${methodArgs: Args })" =>
        classApply(clazz, args, method, methodArgs)
    })
    println(tree)
    c.Expr[T](tree)
  }
}
