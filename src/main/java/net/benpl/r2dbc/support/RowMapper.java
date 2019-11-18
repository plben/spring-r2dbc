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

import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import net.benpl.r2dbc.annotation.Column;
import net.benpl.r2dbc.exception.R2dbcException;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Mapper to convert a Row data to java object
 */
class RowMapper<T> implements BiFunction<Row, RowMetadata, T> {

    private final Class<T> clazz;
    private final String className;

    private final Map<String, Field> allFields = new LinkedHashMap<>();
    private final Map<String, Field> allColumns = new LinkedHashMap<>();

    RowMapper(Class<T> clazz) {
        this.clazz = clazz;
        this.className = clazz.getCanonicalName();

        for (Field field : clazz.getDeclaredFields()) {
            this.allFields.put(field.getName(), field);
            Column column = field.getAnnotation(Column.class);
            if (column != null) {
                this.allColumns.put(column.value(), field);
            }
        }
    }

    /**
     * Converts a Row data to java object.
     */
    @Override
    public T apply(Row row, RowMetadata metadata) {
        Collection<String> columnNames = metadata.getColumnNames();

        if (columnNames.size() == 1) {
            Class<?> javaType = metadata.getColumnMetadata(0).getJavaType();
            assert javaType != null;
            if (javaType.equals(clazz)) {
                return clazz.cast(row.get(0));
            }
        }

        T instance;

        try {
            instance = clazz.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new R2dbcException(String.format("%s: field to create instance.", className), e);
        }

        for (String columnName : columnNames) {
            Object value = row.get(columnName);

            Field field = allColumns.get(columnName);

            if (field != null) {
                // 1. setFieldValue: @Column exists, set value to associated field.
                Utils.setFieldValue(instance, field, value);
            } else {
                // 2. setFieldValue: @Column not exists, set value to ColumnName corresponding field.

                // FieldName <= ColumnName
                String fieldName = Utils.toCamelCase(columnName, false);

                // Field <= FieldName
                field = allFields.get(fieldName);

                if (field == null) {
                    throw new R2dbcException(String.format("%s: field [%s] not found. (column %s)", className, fieldName, columnName));
                }

                // sets value to field.
                Utils.setFieldValue(instance, field, value);
            }
        }

        return instance;
    }

}
