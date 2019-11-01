package net.benpl.r2dbc.support;

import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import net.benpl.r2dbc.exception.R2dbcException;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.function.BiFunction;

/**
 * Mapper to convert a Row data to java object
 */
class RowMapper<T> implements BiFunction<Row, RowMetadata, T> {

    private final Class<T> clazz;
    private final String className;

    RowMapper(Class<T> clazz) {
        this.clazz = clazz;
        this.className = clazz.getCanonicalName();
    }

    /**
     * Converts a Row data to java object.
     */
    @Override
    public T apply(Row row, RowMetadata metadata) {
        try {
            Collection<String> columnNames = metadata.getColumnNames();

            if (columnNames.size() == 1) {
                Class<?> javaType = metadata.getColumnMetadata(0).getJavaType();
                assert javaType != null;
                if (javaType.equals(clazz)) {
                    return clazz.cast(row.get(0));
                }
            }

            T instance = clazz.getDeclaredConstructor().newInstance();

            for (String columnName : columnNames) {
                setFieldValue(instance, columnName, row.get(columnName));
            }

            return instance;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new R2dbcException("Failed on row mapping", e);
        }
    }

    /**
     * Set column value into associated class field.
     */
    private void setFieldValue(T entity, String columnName, Object value) {
        String fieldName = toCamelCase(columnName, false);
        Field field;
        try {
            field = clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            throw new R2dbcException(String.format("%s: field [%s] not found. (column %s)", className, fieldName, columnName), e);
        }

        if (field.isAccessible()) {
            try {
                field.set(entity, value);
            } catch (IllegalAccessException e) {
                throw new R2dbcException(String.format("%s: failed to set field [%s] value. (column %s)", className, fieldName, columnName), e);
            }
        } else {
            Class<?> fieldType = field.getType();
            Method method;
            String methodName = "set" + StringUtils.capitalize(fieldName);

            try {
                method = clazz.getDeclaredMethod(methodName, fieldType);
            } catch (NoSuchMethodException e) {
                throw new R2dbcException(String.format("%s: setter %s() not found. (column %s)", className, methodName, columnName), e);
            }

            try {
                method.invoke(entity, value);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new R2dbcException(String.format("%s: failed to invoke setter %s(). (column %s)", className, methodName, columnName), e);
            }
        }
    }

    /**
     * Sourced from MyBatis
     */
    private String toCamelCase(String inputString, boolean firstCharacterUppercase) {
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
