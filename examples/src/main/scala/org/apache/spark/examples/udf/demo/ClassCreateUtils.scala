package org.apache.spark.examples.udf.demo

import java.lang.reflect.Method
import java.util
import java.util.UUID
import scala.tools.reflect.ToolBox
import org.apache.spark.internal.Logging

case class ClassInfo(clazz: Class[_], instance: Any, defaultMethod: Method, methods: Map[String, Method], func: String) {
  def invoke[T](args: Object*): T = {
    defaultMethod.invoke(instance, args: _*).asInstanceOf[T]
  }
}

object ClassCreateUtils extends Logging {

  private val clazzs = new util.HashMap[String, ClassInfo]()
  private val classLoader = scala.reflect.runtime.universe.getClass.getClassLoader
  // private  val tb = scala.reflect.runtime.universe.runtimeMirror(getClass.getClassLoader).mkToolBox()
  private val toolBox = scala.reflect.runtime.universe.runtimeMirror(classLoader).mkToolBox()

  def apply(func: String): ClassInfo = this.synchronized {
    var clazz = clazzs.get(func)
    if (clazz == null) {
      val (className, classBody) = wrapClass(func)
      val zz = compile(prepareScala(className, classBody))
      val defaultMethod = zz.getDeclaredMethods.head
      val methods = zz.getDeclaredMethods
      clazz = ClassInfo(
        zz,
        zz.newInstance(),
        defaultMethod,
        methods = methods.map { m => (m.getName, m) }.toMap,
        func
      )
      clazzs.put(func, clazz)
      logInfo(s"dynamic load class => $clazz")
    }
    clazz
  }

  def compile(src: String): Class[_] = {
    val tree = toolBox.parse(src)
    toolBox.compile(tree).apply().asInstanceOf[Class[_]]
  }

  def prepareScala(className: String, classBody: String): String = {
    classBody + "\n" + s"scala.reflect.classTag[$className].runtimeClass"
  }

  def wrapClass(function: String): (String, String) = {
    val className = s"dynamic_class_${UUID.randomUUID().toString.replaceAll("-", "")}"
    val classBody =
      s"""
         |class $className{
         |  $function
         |}
            """.stripMargin
    (className, classBody)
  }
}
