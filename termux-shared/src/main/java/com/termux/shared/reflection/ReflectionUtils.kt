package com.termux.shared.reflection

import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Field
import java.lang.reflect.Method

object ReflectionUtils {
    private var HIDDEN_API_REFLECTION_RESTRICTIONS_BYPASSED = false

    /**
     * Bypass android hidden API reflection restrictions.
     * [...](https://github.com/LSPosed/AndroidHiddenApiBypass)
     * [...](https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces)
     */
    @JvmStatic
    fun bypassHiddenAPIReflectionRestrictions() {
        if (!HIDDEN_API_REFLECTION_RESTRICTIONS_BYPASSED) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("")
            } catch (ignored: Throwable) {
            }
            HIDDEN_API_REFLECTION_RESTRICTIONS_BYPASSED = true
        }
    }

    /**
     * Get a [Field] for the specified class.
     *
     * @param clazz     The [Class] for which to return the field.
     * @param fieldName The name of the [Field].
     * @return Returns the [Field] if getting the it was successful, otherwise `null`.
     */
    private fun getDeclaredField(clazz: Class<*>, fieldName: String): Field? {
        try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            return field
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Get a value for a [Field] of an object for the specified class.
     *
     *
     * Trying to access `null` fields will result in [NoSuchFieldException].
     *
     * @param clazz     The [Class] to which the object belongs to.
     * @param fieldName The name of the [Field].
     * @param object    The [Object] instance from which to get the field value.
     * @return Returns the [FieldInvokeResult] of invoking the field. The
     * will be `true` if invoking the field was successful,
     * otherwise `false`. The [value] will contain the field
     * [Object] value.
     */
    @JvmStatic
    fun <T> invokeField(clazz: Class<out T>, fieldName: String, `object`: T): FieldInvokeResult {
        try {
            val field = getDeclaredField(clazz, fieldName) ?: return FieldInvokeResult(null)
            return FieldInvokeResult(field[`object`])
        } catch (e: Exception) {
            return FieldInvokeResult(null)
        }
    }

    /**
     * Wrapper for [.getDeclaredMethod] without parameters.
     */
    @JvmStatic
    fun getDeclaredMethod(clazz: Class<*>, methodName: String): Method? {
        return getDeclaredMethod(clazz, methodName, *arrayOfNulls<Class<*>>(0))
    }

    /**
     * Get a [Method] for the specified class with the specified parameters.
     *
     * @param clazz          The [Class] for which to return the method.
     * @param methodName     The name of the [Method].
     * @param parameterTypes The parameter types of the method.
     * @return Returns the [Method] if getting the it was successful, otherwise `null`.
     */
    @JvmStatic
    fun getDeclaredMethod(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>?
    ): Method? {
        try {
            val method = clazz.getDeclaredMethod(methodName, *parameterTypes)
            method.isAccessible = true
            return method
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Wrapper for [.invokeMethod] without arguments.
     */
    @JvmStatic
    fun invokeMethod(method: Method, obj: Any?): MethodInvokeResult {
        return invokeMethod(method, obj, *arrayOfNulls<Any>(0))
    }

    /**
     * Invoke a [Method] on the specified object with the specified arguments.
     *
     * @param method The [Method] to invoke.
     * @param obj    The [Object] the method should be invoked from.
     * @param args   The arguments to pass to the method.
     * @return Returns the [MethodInvokeResult] of invoking the method. The
     * will be `true` if invoking the method was successful,
     * otherwise `false`. The [value] will contain the [Object]
     * returned by the method.
     */
    @JvmStatic
    fun invokeMethod(method: Method, obj: Any?, vararg args: Any?): MethodInvokeResult {
        try {
            method.isAccessible = true
            return MethodInvokeResult(method.invoke(obj, *args))
        } catch (e: Exception) {
            return MethodInvokeResult(null)
        }
    }

    /**
     * Class that represents result of invoking a field.
     */
    class FieldInvokeResult internal constructor(@JvmField val value: Any?)

    /**
     * Class that represents result of invoking a method that has a non-void return type.
     */
    class MethodInvokeResult internal constructor(@JvmField val value: Any?)
}
