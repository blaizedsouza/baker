package com.ing.baker.runtime.core

import com.ing.baker.il.EventType
import com.ing.baker.types.{Converters, Value}


object RuntimeEvent {

  def create(name: String, ingredients: Seq[(String, Any)]): RuntimeEvent = {
    val ingredientValues = ingredients.map {
      case (name, obj) => name -> Converters.toValue(obj)
    }

    RuntimeEvent(name, ingredientValues)
  }

}

case class RuntimeEvent(name: String,
                        providedIngredients: Seq[(String, Value)]) {

  /**
    * The provided ingredients in the form of a Map
    * This is useful when a EventListener is used in Java.
    */
  val providedIngredientsMap: Map[String, Value] = providedIngredients.toMap;

  /**
    * This checks if the runtime event is an instance of a event type.
    *
    * @param eventType
    * @return
    */
  def isInstanceOfEventType(eventType: EventType): Boolean = validateEvent(eventType).isEmpty

  /**
    *
    * Validates the runtime event against a event type and returns a sequence
    * of validation errors.
    *
    * @param eventType The event type to validate against.
    * @return
    */
  def validateEvent(eventType: EventType): Seq[String] = {

    if (eventType.name != name)
      Seq(s"Provided event with name '$name' does not match expected name '${eventType.name}'")
    else
      // we check all the required ingredient types, additional ones are ignored
      eventType.ingredientTypes.flatMap { ingredient =>
        providedIngredientsMap.get(ingredient.name) match {
          case None        =>
            Seq(s"no value was provided for ingredient '${ingredient.name}'")
          // we can only check the class since the type parameters are available on objects
          case Some(value) if !value.isInstanceOf(ingredient.`type`)  =>
            Seq(s"value is not of the correct type for ingredient '${ingredient.name}'")
          case _ =>
            Seq.empty
        }
    }
  }
}