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

import lombok.NonNull;
import net.benpl.r2dbc.annotation.Column;
import net.benpl.r2dbc.annotation.IdClass;
import net.benpl.r2dbc.annotation.Table;
import net.benpl.r2dbc.exception.R2dbcException;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Stream;

/**
 * Detail information of class contains annotation {@link Table}.
 */
class TableInfo<T> {

    /**
     * Full name of this class.
     */
    private final String className;

    /**
     * Name of associated table. {@link Table#value()}
     */
    final String tableName;

    /**
     * Class of composite primary key. {@link IdClass#value()}
     */
    private final Class<?> idClass;

    /**
     * Primary key with AUTO_INCREMENT attribute. {@link Column#primary()} & {@link Column#autoIncrement()}
     */
    Field aiField = null;

    /**
     * Columns of table. {@link Column}
     */
    final Map<String, Column> allColumns = new LinkedHashMap<>();

    /**
     * Column associated fields. {@link Column}
     */
    final Map<String, Field> allFields = new LinkedHashMap<>();

    /**
     * Column names of primary key. {@link Column#primary()}
     */
    final List<String> allKeys = new ArrayList<>();

    private TableInfo(Class<T> clazz) {
        this.className = clazz.getCanonicalName();

        Table table = clazz.getAnnotation(Table.class);
        if (table == null) {
            throw new R2dbcException(String.format("%s: annotation @Table not found.", className));
        }

        this.tableName = table.value();

        if (!"TABLE".equals(table.type())) {
            throw new R2dbcException(String.format("%s: [%s] is not a TABLE.", className, tableName));
        }

        this.idClass = clazz.isAnnotationPresent(IdClass.class) ? clazz.getAnnotation(IdClass.class).value() : null;

        Stream.of(clazz.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Column.class))
                .forEach(field -> {
                    Column column = field.getAnnotation(Column.class);
                    String key = column.value();

                    allColumns.put(key, column);
                    allFields.put(key, field);

                    if (column.primary()) {
                        if (column.autoIncrement()) {
                            aiField = field;
                        }
                        allKeys.add(key);
                    }
                });
    }

    static <T> TableInfo<T> of(T entity) {
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) entity.getClass();
        return of(clazz);
    }

    static <T> TableInfo<T> of(Class<T> clazz) {
        return new TableInfo<>(clazz);
    }

    /**
     * @return 1.1: returns TRUE only when all keys are NULL, otherwise FALSE.
     */
    boolean isKeyNull(T entity) {
        for (String key : allKeys) {
            if (Utils.getFieldValue(entity, allFields.get(key)) != null) return false;
        }
        return true;
    }

    Object aiValueFrom(Number id) {
        Class<?> classType = aiField.getType();

        if (classType.equals(Byte.class)) {
            return id.byteValue();
        } else if (classType.equals(Short.class)) {
            return id.shortValue();
        } else if (classType.equals(Integer.class)) {
            return id.intValue();
        } else if (classType.equals(Long.class)) {
            return id.longValue();
        } else {
            throw new R2dbcException(String.format("%s: field [%s] type %s invalid.", className, aiField.getName(), classType.getSimpleName()));
        }
    }

    Map<String, Object> getIdValues(@NonNull Object id) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (idClass != null) {
            if (!id.getClass().equals(idClass)) {
                throw new R2dbcException(String.format("%s: invalid primary key class %s. (Should be %s)", className, id.getClass().getCanonicalName(), idClass.getCanonicalName()));
            }

            Map<String, Field> fields = new HashMap<>();

            Stream.of(idClass.getDeclaredFields())
                    .filter(field -> field.isAnnotationPresent(Column.class))
                    .forEach(field -> fields.put(field.getAnnotation(Column.class).value(), field));

            allKeys.forEach(key -> {
                Field field = fields.get(key);
                if (field != null) {
                    result.put(key, Utils.getFieldValue(id, field));
                } else {
                    throw new R2dbcException(String.format("%s: column [%s] not found in primary key class %s.", className, key, idClass.getCanonicalName()));
                }
            });
        } else {
            result.put(allKeys.get(0), id);
        }

        return result;
    }

    /**
     * Since 1.1.
     */
    Object getId(@NonNull Object entity) {
        if (allKeys.isEmpty()) {
            // No primary key.
            return null;
        } else if (idClass == null) {
            // Single primary key.
            Field field = allFields.get(allKeys.get(0));
            return Utils.getFieldValue(entity, field);
        } else {
            // Composite primary key.
            Map<String, Field> idFields = new HashMap<>();

            Stream.of(idClass.getDeclaredFields())
                    .filter(field -> field.isAnnotationPresent(Column.class))
                    .forEach(field -> idFields.put(field.getAnnotation(Column.class).value(), field));

            try {
                Object idInstance = idClass.newInstance();

                allKeys.forEach(key -> {
                    Field field = allFields.get(key);
                    Object value = Utils.getFieldValue(entity, field);
                    Utils.setFieldValue(idInstance, idFields.get(key), value);
                });

                return idInstance;
            } catch (InstantiationException | IllegalAccessException e) {
                throw new R2dbcException(String.format("%s: failed to invoke newInstance() of primary key class %s.", className, idClass.getCanonicalName()), e);
            }
        }
    }

}
