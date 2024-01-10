package com.termux.shared.reflection;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectionUtils {

    private static boolean HIDDEN_API_REFLECTION_RESTRICTIONS_BYPASSED = false;

    /**
     * Bypass android hidden API reflection restrictions.
     * <a href="https://github.com/LSPosed/AndroidHiddenApiBypass">...</a>
     * <a href="https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces">...</a>
     */
    public static void bypassHiddenAPIReflectionRestrictions() {
        if (!HIDDEN_API_REFLECTION_RESTRICTIONS_BYPASSED) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("");
            } catch (Throwable ignored) {
            }
            HIDDEN_API_REFLECTION_RESTRICTIONS_BYPASSED = true;
        }
    }

    /**
     * Get a {@link Field} for the specified class.
     *
     * @param clazz     The {@link Class} for which to return the field.
     * @param fieldName The name of the {@link Field}.
     * @return Returns the {@link Field} if getting the it was successful, otherwise {@code null}.
     */

    public static Field getDeclaredField(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get a value for a {@link Field} of an object for the specified class.
     * <p>
     * Trying to access {@code null} fields will result in {@link NoSuchFieldException}.
     *
     * @param clazz     The {@link Class} to which the object belongs to.
     * @param fieldName The name of the {@link Field}.
     * @param object    The {@link Object} instance from which to get the field value.
     * @return Returns the {@link FieldInvokeResult} of invoking the field. The
     * will be {@code true} if invoking the field was successful,
     * otherwise {@code false}. The {@link FieldInvokeResult#value} will contain the field
     * {@link Object} value.
     */

    public static <T> FieldInvokeResult invokeField(Class<? extends T> clazz, String fieldName, T object) {
        try {
            Field field = getDeclaredField(clazz, fieldName);
            if (field == null)
                return new FieldInvokeResult(null);
            return new FieldInvokeResult(field.get(object));
        } catch (Exception e) {
            return new FieldInvokeResult(null);
        }
    }

    /**
     * Wrapper for {@link #getDeclaredMethod(Class, String, Class[])} without parameters.
     */

    public static Method getDeclaredMethod(Class<?> clazz, String methodName) {
        return getDeclaredMethod(clazz, methodName, new Class<?>[0]);
    }

    /**
     * Get a {@link Method} for the specified class with the specified parameters.
     *
     * @param clazz          The {@link Class} for which to return the method.
     * @param methodName     The name of the {@link Method}.
     * @param parameterTypes The parameter types of the method.
     * @return Returns the {@link Method} if getting the it was successful, otherwise {@code null}.
     */

    public static Method getDeclaredMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        try {
            Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Wrapper for {@link #invokeMethod(Method, Object, Object...)} without arguments.
     */

    public static MethodInvokeResult invokeMethod(Method method, Object obj) {
        return invokeMethod(method, obj, new Object[0]);
    }

    /**
     * Invoke a {@link Method} on the specified object with the specified arguments.
     *
     * @param method The {@link Method} to invoke.
     * @param obj    The {@link Object} the method should be invoked from.
     * @param args   The arguments to pass to the method.
     * @return Returns the {@link MethodInvokeResult} of invoking the method. The
     * will be {@code true} if invoking the method was successful,
     * otherwise {@code false}. The {@link MethodInvokeResult#value} will contain the {@link Object}
     * returned by the method.
     */

    public static MethodInvokeResult invokeMethod(Method method, Object obj, Object... args) {
        try {
            method.setAccessible(true);
            return new MethodInvokeResult(method.invoke(obj, args));
        } catch (Exception e) {
            return new MethodInvokeResult(null);
        }
    }

    /**
     * Class that represents result of invoking a field.
     */
    public static class FieldInvokeResult {

        public final Object value;

        FieldInvokeResult(Object value) {
            this.value = value;
        }
    }

    /**
     * Class that represents result of invoking a method that has a non-void return type.
     */
    public static class MethodInvokeResult {

        public final Object value;

        MethodInvokeResult(Object value) {
            this.value = value;
        }
    }

}
