/*
 * MIT License
 *
 * Copyright © 2019 Ben Peng
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the “Software”), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package net.benpl.r2dbc.support;

import net.benpl.r2dbc.exception.R2dbcException;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

class Utils {
    /**
     * Get field value from object.
     */
    static Object getFieldValue(Object entity, Field field) {
        Class<?> clazz = entity.getClass();
        String className = clazz.getCanonicalName();
        String fieldName = field.getName();

        if (field.isAccessible()) {
            try {
                return field.get(entity);
            } catch (IllegalAccessException e) {
                throw new R2dbcException(String.format("%s: failed to get field [%s] value.", className, fieldName), e);
            }
        } else {
            Method method;
            String methodName = "get" + StringUtils.capitalize(fieldName);

            try {
                method = clazz.getMethod(methodName);
            } catch (NoSuchMethodException e) {
                throw new R2dbcException(String.format("%s: getter %s() not found.", className, methodName), e);
            }

            try {
                return method.invoke(entity);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new R2dbcException(String.format("%s: failed to invoke getter %s().", className, methodName), e);
            }
        }
    }

    /**
     * Set field value into object.
     */
    static void setFieldValue(Object entity, Field field, Object value) {
        Class<?> clazz = entity.getClass();
        String className = clazz.getCanonicalName();

        if (field.isAccessible()) {
            try {
                field.set(entity, value);
            } catch (IllegalAccessException e) {
                throw new R2dbcException(String.format("%s: failed to set field [%s] value.", className, field.getName()), e);
            }
        } else {
            Method method;
            String methodName = "set" + StringUtils.capitalize(field.getName());
            Class<?> fieldType = field.getType();

            try {
                method = clazz.getDeclaredMethod(methodName, fieldType);
            } catch (NoSuchMethodException e) {
                throw new R2dbcException(String.format("%s: setter %s(%s) not found.", className, methodName, fieldType.getCanonicalName()), e);
            }

            try {
                method.invoke(entity, value);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new R2dbcException(String.format("%s: failed to invoke setter %s(%s).", className, methodName, fieldType.getCanonicalName()), e);
            }
        }
    }

    /**
     * Converts string to camel case.
     * (This method is cloned from MyBatis)
     */
    static String toCamelCase(String inputString, boolean firstCharacterUppercase) {
        StringBuilder sb = new StringBuilder();

        boolean nextUpperCase = false;
        for (int i = 0; i < inputString.length(); i++) {
            char c = inputString.charAt(i);

            switch (c) {
                case '_':
                case '-':
                case '@':
                case '$':
                case '#':
                case ' ':
                case '/':
                case '&':
                    if (sb.length() > 0) {
                        nextUpperCase = true;
                    }
                    break;

                default:
                    if (nextUpperCase) {
                        sb.append(Character.toUpperCase(c));
                        nextUpperCase = false;
                    } else {
                        sb.append(Character.toLowerCase(c));
                    }
                    break;
            }
        }

        if (firstCharacterUppercase) {
            sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
        }

        return sb.toString();
    }

}
