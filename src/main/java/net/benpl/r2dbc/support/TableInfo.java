package net.benpl.r2dbc.support;

import lombok.NonNull;
import net.benpl.r2dbc.annotation.*;
import net.benpl.r2dbc.exception.R2dbcException;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class TableInfo<T> {

    /**
     * 表名：{@link Table#value()}
     */
    final String tableName;
    private final String className;

    /**
     * 多主键
     * 主键类：{@link IdClass#value()}
     */
    private final Class<?> idClass;

    /**
     * 自增量主键字段
     * 字段名： {@link Column#value()}
     * 同时：具备 {@link Id} & {@link AutoIncrement}
     */
    String aiKey = null;
    /**
     * 自增量主键POJO成员
     * 同时：具备 {@link Id} & {@link AutoIncrement}
     */
    Field aiField = null;

    /**
     * 所有包含 {@link Column} 的字段
     */
    final Map<String, Column> allColumns = new LinkedHashMap<>();
    /**
     * 所有包含 {@link Column} 的字段，对应的POJO成员
     */
    final Map<String, Field> allFields = new LinkedHashMap<>();
    /**
     * 所有包含 {@link Id} 的字段名（主键名）
     */
    final List<String> allKeys = new ArrayList<>();

    private TableInfo(Class<T> clazz) {
        this.className = clazz.getCanonicalName();

        Table table = clazz.getAnnotation(Table.class);
        if (table == null) {
            throw new R2dbcException(String.format("%s: annotation @Table not found.", className));
        }

        if (!"TABLE".equals(table.type())) {
            throw new R2dbcException(String.format("%s: is not TABLE.", className));
        }

        this.tableName = table.value();

        this.idClass = clazz.isAnnotationPresent(IdClass.class) ? clazz.getAnnotation(IdClass.class).value() : null;

        for (Field field : clazz.getDeclaredFields()) {
            Column column = field.getAnnotation(Column.class);

            if (column != null) {
                String key = column.value();
                boolean isKey = field.isAnnotationPresent(Id.class);
                boolean isAI = field.isAnnotationPresent(AutoIncrement.class);

                this.allColumns.put(key, column);
                this.allFields.put(key, field);

                if (isKey) {
                    if (isAI) {
                        this.aiKey = key;
                        this.aiField = field;
                    }

                    this.allKeys.add(key);
                }
            }
        }
    }

    static <T> TableInfo<T> of(T entity) {
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) entity.getClass();
        return of(clazz);
    }

    static <T> TableInfo<T> of(Class<T> clazz) {
        return new TableInfo<>(clazz);
    }

    boolean isKeyNull(T entity) {
        for (String key : allKeys) {
            if (getFieldValue(entity, allFields.get(key)) == null) return true;
        }
        return false;
    }

    Object aiValueFrom(Long id) {
        Class<?> classType = aiField.getType();

        if (classType.equals(Short.class)) {
            return id.shortValue();
        } else if (classType.equals(Integer.class)) {
            return id.intValue();
        } else if (classType.equals(Long.class)) {
            return id;
        } else {
            throw new R2dbcException(String.format("%s.%s: invalid type %s. (Valid: Short/Integer/Long)", className, aiField.getName(), classType.getSimpleName()));
        }
    }

    Object getFieldValue(Object entity, Field field) {
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

    <P> P setFieldValue(P entity, Field field, Object value) {
        Class<?> clazz = entity.getClass();
        String className = clazz.getCanonicalName();
        String fieldName = field.getName();

        if (field.isAccessible()) {
            try {
                field.set(entity, value);
            } catch (IllegalAccessException e) {
                throw new R2dbcException(String.format("%s: failed to set field [%s] value.", className, fieldName), e);
            }
        } else {
            Method method;
            String methodName = "set" + StringUtils.capitalize(fieldName);

            try {
                method = clazz.getDeclaredMethod(methodName, field.getType());
            } catch (NoSuchMethodException e) {
                throw new R2dbcException(String.format("%s: setter %s() not found.", className, methodName), e);
            }

            try {
                method.invoke(entity, value);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new R2dbcException(String.format("%s: failed to invoke setter %s().", className, methodName), e);
            }
        }

        return entity;
    }

    Map<String, Object> getIdValues(@NonNull Object id) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (idClass != null) {
            if (!id.getClass().equals(idClass)) {
                throw new R2dbcException(String.format("%s: invalid primary key class %s. (Should be %s)", className, id.getClass().getCanonicalName(), idClass.getCanonicalName()));
            }

            for (Field field : idClass.getDeclaredFields()) {
                String key = field.getAnnotation(Column.class).value();
                Object value = getFieldValue(id, field);
                result.put(key, value);
            }
        } else {
            result.put(allKeys.get(0), id);
        }

        return result;
    }

}
