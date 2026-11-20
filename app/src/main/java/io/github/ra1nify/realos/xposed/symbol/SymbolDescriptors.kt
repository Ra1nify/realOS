package io.github.ra1nify.realos.xposed.symbol

import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Locatable state of each hook point.
 *
 * Fail-closed semantics: only [FOUND] lets callers consume the handle; [MISSING],
 * [ERROR] and [DISABLED] short-circuit with no further guessing.
 */
enum class HookPointState {
    FOUND,
    MISSING,
    ERROR,
    DISABLED
}

/**
 * Serializable descriptor for a resolved class.
 *
 * Holds only DEX plain names and no `Class` handle; it can be written to the disk
 * cache and restored at any time through the host `ClassLoader`.
 */
data class ClassDescriptor(val className: String) {

    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_CLASS_NAME, className)
    }

    fun restore(classLoader: ClassLoader): Class<*>? = runCatching {
        classLoader.loadClass(className)
    }.getOrNull()

    companion object {
        private const val KEY_CLASS_NAME = "className"

        fun fromJson(json: JSONObject): ClassDescriptor = ClassDescriptor(
            className = json.getString(KEY_CLASS_NAME)
        )
    }
}

/**
 * Serializable descriptor for a resolved method.
 *
 * [returnType] and [parameterTypes] use `Class#name` form (primitives such as
 * `boolean`, arrays such as `[Ljava.lang.String;`); restore compares each name.
 */
data class MethodDescriptor(
    val className: String,
    val methodName: String,
    val returnType: String,
    val parameterTypes: List<String>
) {

    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_CLASS_NAME, className)
        put(KEY_METHOD_NAME, methodName)
        put(KEY_RETURN_TYPE, returnType)
        put(KEY_PARAMETER_TYPES, JSONArray().apply { parameterTypes.forEach { put(it) } })
    }

    /**
     * Restores the real `Method` from [classLoader].
     *
     * Name, parameter count, parameter types and return type must all match;
     * any mismatch returns null for the caller to mark and fail closed.
     */
    fun restore(classLoader: ClassLoader): Method? = runCatching {
        val clazz = classLoader.loadClass(className)
        clazz.declaredMethods.firstOrNull { it.matches(this) }?.apply { isAccessible = true }
    }.getOrNull()

    companion object {
        private const val KEY_CLASS_NAME = "className"
        private const val KEY_METHOD_NAME = "methodName"
        private const val KEY_RETURN_TYPE = "returnType"
        private const val KEY_PARAMETER_TYPES = "parameterTypes"

        fun fromJson(json: JSONObject): MethodDescriptor {
            val params = json.optJSONArray(KEY_PARAMETER_TYPES) ?: JSONArray()
            return MethodDescriptor(
                className = json.getString(KEY_CLASS_NAME),
                methodName = json.getString(KEY_METHOD_NAME),
                returnType = json.getString(KEY_RETURN_TYPE),
                parameterTypes = List(params.length()) { params.getString(it) }
            )
        }

        /** Builds the descriptor from a restored method, for the scan-stage cache write-back */
        fun of(method: Method): MethodDescriptor = MethodDescriptor(
            className = method.declaringClass.name,
            methodName = method.name,
            returnType = method.returnType.name,
            parameterTypes = method.parameterTypes.map { it.name }
        )
    }
}

/**
 * Serializable descriptor for a resolved field.
 */
data class FieldDescriptor(
    val className: String,
    val fieldName: String,
    val fieldType: String
) {

    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_CLASS_NAME, className)
        put(KEY_FIELD_NAME, fieldName)
        put(KEY_FIELD_TYPE, fieldType)
    }

    fun restore(classLoader: ClassLoader): Field? = runCatching {
        val clazz = classLoader.loadClass(className)
        clazz.declaredFields.firstOrNull { it.name == fieldName && it.type.name == fieldType }
            ?.apply { isAccessible = true }
    }.getOrNull()

    companion object {
        private const val KEY_CLASS_NAME = "className"
        private const val KEY_FIELD_NAME = "fieldName"
        private const val KEY_FIELD_TYPE = "fieldType"

        fun fromJson(json: JSONObject): FieldDescriptor = FieldDescriptor(
            className = json.getString(KEY_CLASS_NAME),
            fieldName = json.getString(KEY_FIELD_NAME),
            fieldType = json.getString(KEY_FIELD_TYPE)
        )

        /** Builds the descriptor from a restored field, for the scan-stage cache write-back */
        fun of(field: Field): FieldDescriptor = FieldDescriptor(
            className = field.declaringClass.name,
            fieldName = field.name,
            fieldType = field.type.name
        )
    }
}

/**
 * Serializable descriptor for a resolved constructor.
 */
data class ConstructorDescriptor(
    val className: String,
    val parameterTypes: List<String>
) {

    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_CLASS_NAME, className)
        put(KEY_PARAMETER_TYPES, JSONArray().apply { parameterTypes.forEach { put(it) } })
    }

    /**
     * Restores the real [Constructor] handle from the class loader.
     */
    fun restore(classLoader: ClassLoader): Constructor<*>? = runCatching {
        val clazz = classLoader.loadClass(className)
        clazz.declaredConstructors.firstOrNull { ctor ->
            ctor.parameterTypes.size == parameterTypes.size &&
                ctor.parameterTypes.indices.all { ctor.parameterTypes[it].name == parameterTypes[it] }
        }?.apply { isAccessible = true }
    }.getOrNull()

    companion object {
        private const val KEY_CLASS_NAME = "className"
        private const val KEY_PARAMETER_TYPES = "parameterTypes"

        fun fromJson(json: JSONObject): ConstructorDescriptor {
            val params = json.optJSONArray(KEY_PARAMETER_TYPES) ?: JSONArray()
            return ConstructorDescriptor(
                className = json.getString(KEY_CLASS_NAME),
                parameterTypes = List(params.length()) { params.getString(it) }
            )
        }

        fun of(ctor: Constructor<*>): ConstructorDescriptor = ConstructorDescriptor(
            className = ctor.declaringClass.name,
            parameterTypes = ctor.parameterTypes.map { it.name }
        )
    }
}

/** Whether the method signature matches the descriptor exactly (name + parameter types + return type) */
private fun Method.matches(descriptor: MethodDescriptor): Boolean {
    if (name != descriptor.methodName) return false
    if (parameterTypes.size != descriptor.parameterTypes.size) return false
    if (returnType.name != descriptor.returnType) return false
    return parameterTypes.indices.all { parameterTypes[it].name == descriptor.parameterTypes[it] }
}
