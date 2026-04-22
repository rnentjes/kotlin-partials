package nl.astraeus.partials.util

import nl.astraeus.partials.web.RenderFunction
import java.lang.reflect.Field
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KFunction

object LambdaIdRegistry {
  private val nextId = AtomicLong(1)
  private val ids = Collections.synchronizedMap(IdentityHashMap<Any, String>())

  fun idFor(lambda: RenderFunction, prefix: String = "id-"): String {
    return ids.getOrPut(lambda) {
      "$prefix${nextId.getAndIncrement()}"
    }
  }
}

object RenderFunctionIds {
  var debug = false
  private val cache = ConcurrentHashMap<Class<*>, List<Field>>()

  fun String.camelToDash(): String {
    val result = StringBuilder()
    var capital = false

    for (ch in this) {
      if (ch.isUpperCase() && !capital) {
        result.append("-")
        result.append(ch.lowercase())
        capital = true
      } else if (ch.isLowerCase() && capital) {
        result.append(ch)
        capital = false
      } else {
        result.append(ch)
      }
    }

    return result.toString()
  }

  fun idFor(owner: Any, func: Any, prefix: String = "id-"): String {
    val key = if (func is KFunction<*>) {
      func.name.camelToDash()
    } else {
      val ownerClass = owner.javaClass
      val fields = cache.getOrPut(ownerClass) {
        ownerClass.declaredFields.onEach { it.isAccessible = true }.toList()
      }

      val field = fields.firstOrNull { it.get(owner) === func }

      if (field != null) {
        field.name.camelToDash()
      } else {
        error(
          "Unable to resolve render function to a field on ${ownerClass.name}. " +
              "Declare the lambda as a property."
        )
      }
    }

    if (debug) {
      return key
    } else {
      return Hasher.stableShortId(key, prefix = prefix)
    }
  }
}
