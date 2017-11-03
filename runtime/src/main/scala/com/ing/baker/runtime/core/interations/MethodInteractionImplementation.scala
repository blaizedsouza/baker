package com.ing.baker.runtime.core.interations

import java.lang.reflect.{Method, ParameterizedType, Type}
import java.util.UUID

import com.ing.baker.il.petrinet.InteractionTransition
import com.ing.baker.il.types.{Converters, Value}
import com.ing.baker.runtime.core.BakerException
import org.slf4j.{Logger, LoggerFactory}

import scala.util.Try

object MethodInteractionImplementation {
  /**
    * This method looks for any valid name that this interaction implements
    * This is its own class name
    * The class name of any interface it implements
    * The value of the field "name"
    *
    * @param obj
    * @return List of possible interaction names this obj can be implementing
    */
  def getPossibleInteractionNamesForImplementation(obj: Any): Set[String] = {
    val nameField: String = Try {
      obj.getClass.getDeclaredField("name")
    }.toOption match {
      case Some(field) if field.getType == classOf[String] => {
        field.setAccessible(true)
        field.get(obj).asInstanceOf[String]
      }
      case None => ""
    }
    val interfaceNames: Seq[String] = obj.getClass.getInterfaces.map(_.getSimpleName).toSeq
    Set[String](obj.getClass.getSimpleName, nameField).filterNot(s => s equals "") ++ interfaceNames
  }


  def anyRefToInteractionImplementations(any: AnyRef): Seq[InteractionImplementation] = {
    any.getClass.getMethods.count(m => m.getName == "apply") match {
      case 0 => throw new BakerException("Method does not have a apply function")
      case n if n > 1 => throw new BakerException("Method has multiple apply functions")
      case _ => ()
    }
    val applyMethod: Method = any.getClass.getMethods.find(m => m.getName == "apply").get
    getPossibleInteractionNamesForImplementation(any).map { name =>
      new MethodInteractionImplementation(name, any, applyMethod.getParameterTypes.toSeq, applyMethod.getReturnType)
    }.toSeq
  }
}

case class MethodInteractionImplementation(override val name: String,
                                           implementation: AnyRef,
                                           override val requiredIngredients: Seq[Type],
                                           override val returnType: Type) extends InteractionImplementation {

  val log: Logger = LoggerFactory.getLogger(MethodInteractionImplementation.getClass)

  val method = implementation.getClass().getMethods.find(_.getName == "apply").get

  override def isValidForInteraction(interaction: InteractionTransition[_]): Boolean = interaction.originalInteractionName == name

  override def execute(input: Seq[Value]): Value = interactionTask.apply(input)

  private val interactionTask: Seq[Value] => Value = inputValues => {

    val invocationId = UUID.randomUUID().toString

    println(s"name: $name")
    println("types: " + method.getGenericParameterTypes.mkString(","))

    val inputArgs = inputValues.zip(method.getGenericParameterTypes).map {
      case (value, targetType) =>
        println(s"input: $value")
        println(s"target type: $targetType")
        println(s"is generic type: ${targetType.isInstanceOf[ParameterizedType]}")

        value.toJava(targetType)
    }

    log.trace(s"[$invocationId] invoking '$name' with parameters ${inputArgs.toString}")

    val result = method.invoke(implementation, inputArgs.asInstanceOf[Seq[AnyRef]]: _*)
    log.trace(s"[$invocationId] result: $result")

    val value = Converters.toValue(result)
    value
  }
}
