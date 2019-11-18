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

import javafx.util.Pair;
import lombok.NonNull;
import net.benpl.r2dbc.R2dbc;
import net.benpl.r2dbc.annotation.Column;
import net.benpl.r2dbc.exception.R2dbcException;
import org.springframework.data.r2dbc.core.DatabaseClient;
import org.springframework.data.r2dbc.query.Criteria;
import org.springframework.data.r2dbc.query.Update;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

abstract class Abstract implements R2dbc {

    final DatabaseClient databaseClient;

    Abstract(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public <T> Mono<Long> count(Class<T> clazz) {
        TableInfo<T> tableInfo = TableInfo.of(clazz);
        return execute0("SELECT COUNT(*) FROM `" + tableInfo.tableName + "`")
                .map(row -> ((Number) Objects.requireNonNull(row.get(0))).longValue())
                .first();
    }

    @Override
    public <T> Mono<Boolean> existsById(Class<T> clazz, @NonNull Object id) {
        return byId("SELECT EXISTS(SELECT *", ")", clazz, id)
                .map(row -> ((Number) Objects.requireNonNull(row.get(0))).intValue() == 1)
                .first();
    }

    /**
     * 1.1: different implementations for cases of No Primary Key, Single Primary Key and Composite Primary Key.
     */
    @Override
    public <T> Mono<T> save(@NonNull T entity) {
        TableInfo<T> tableInfo = TableInfo.of(entity);

        if (tableInfo.allKeys.isEmpty()) {
            // No primary key.
            return insertSpec(tableInfo, entity)
                    .fetch()
                    .rowsUpdated()
                    .thenReturn(entity);
        } else {
            if (tableInfo.isKeyNull(entity)) {
                // Primary key is NULL
                if (tableInfo.aiField != null) {
                    // Primary key is NULL, and AUTO_INCREMENT
                    return insertSpec(tableInfo, entity)
                            .map(row -> {
                                Object value = tableInfo.aiValueFrom((Number) row.get(0));
                                Utils.setFieldValue(entity, tableInfo.aiField, value);
                                return entity;
                            })
                            .first();
                } else {
                    // Primary key is NULL, but NOT AUTO_INCREMENT
                    throw new R2dbcException("Table [" + tableInfo.tableName + "]: failed to save record. (primary key is NULL)");
                }
            } else {
                // Primary key is not NULL
                @SuppressWarnings("unchecked")
                Class<T> clazz = (Class<T>) entity.getClass();
                Object id = tableInfo.getId(entity);

                return existsById(clazz, id)
                        .flatMap((Function<Boolean, Mono<T>>) exists -> {
                            if (exists) {
                                // Primary key is not NULL, and record exists.
                                return updateSpec(tableInfo, entity)
                                        .fetch()
                                        .rowsUpdated()
                                        .thenReturn(entity);
                            } else {
                                // Primary key is not NULL, and record NOT exists.
                                if (tableInfo.aiField != null) {
                                    return insertSpec(tableInfo, entity)
                                            .map(row -> {
                                                Object value = tableInfo.aiValueFrom((Number) row.get(0));
                                                Utils.setFieldValue(entity, tableInfo.aiField, value);
                                                return entity;
                                            })
                                            .first();
                                } else {
                                    return insertSpec(tableInfo, entity)
                                            .fetch()
                                            .rowsUpdated()
                                            .thenReturn(entity);
                                }
                            }
                        })
                        .single();
            }
        }
    }

    @Override
    public <T> Mono<T> findById(Class<T> clazz, @NonNull Object id) {
        return byId("SELECT *", "", clazz, id)
                .map(new RowMapper<>(clazz))
                .first();
    }

    @Override
    public <T> Flux<T> findAll(Class<T> clazz) {
        TableInfo<T> tableInfo = TableInfo.of(clazz);
        String sql = "SELECT * FROM `" + tableInfo.tableName + "`";
        return execute0(sql)
                .map(new RowMapper<>(clazz))
                .all();
    }

    @Override
    public <T> Mono<Boolean> delete(@NonNull T entity) {
        TableInfo<T> tableInfo = TableInfo.of(entity);

        Collection<String> keys = tableInfo.allKeys;
        Map<String, Field> allFields = tableInfo.allFields;

        String clauseStr;
        List<Pair<? extends Class<?>, Object>> params;

        if (keys.isEmpty()) {
            keys = allFields.keySet();
        }

        // (x = ? AND y = ? , ...)
        clauseStr = keys.stream()
                .map(key -> "`" + key + "` = ?")
                .collect(Collectors.joining(" AND "));
        params = keys.stream()
                .map(key -> {
                    Field field = allFields.get(key);
                    return new Pair<>(field.getType(), Utils.getFieldValue(entity, field));
                })
                .collect(Collectors.toList());

        String sql = "DELETE FROM `" + tableInfo.tableName + "` WHERE " + clauseStr;

        return execute0(sql, params)
                .fetch()
                .rowsUpdated()
                .map(count -> count == 1);
    }

    @Override
    public <T> Mono<Integer> deleteAll(List<T> entities) {
        TableInfo<T> tableInfo = TableInfo.of(entities.get(0));

        List<String> allKeys = tableInfo.allKeys;
        Map<String, Field> allFields = tableInfo.allFields;

        String clauseStr;
        List<Pair<? extends Class<?>, Object>> params;

        if (allKeys.isEmpty()) {
            // No primary key.
            // (x = ? AND y = ? , ...)
            String valueStr = allFields.keySet().stream()
                    .map(key -> "`" + key + "` = ?")
                    .collect(Collectors.joining(" AND ", "(", ")"));
            // (x = ? AND y = ? , ...) OR (x = ? AND y = ? , ...) OR ...
            clauseStr = String.join(" OR ", Collections.nCopies(entities.size(), valueStr));
            params = entities.stream()
                    .flatMap(entity -> allFields.keySet().stream()
                            .map(key -> {
                                Field field = allFields.get(key);
                                return new Pair<>(field.getType(), Utils.getFieldValue(entity, field));
                            }))
                    .collect(Collectors.toList());
        } else if (allKeys.size() == 1) {
            // Single primary key.
            String key = allKeys.get(0);
            clauseStr = "`" + key + "` IN (" + String.join(", ", Collections.nCopies(entities.size(), "?")) + ")";
            params = entities.stream()
                    .map(entity -> {
                        Field field = allFields.get(key);
                        return new Pair<>(field.getType(), Utils.getFieldValue(entity, field));
                    })
                    .collect(Collectors.toList());
        } else {
            // Compite primary key.
            // (x = ? AND y = ? , ...)
            String valueStr = allKeys.stream()
                    .map(key -> "`" + key + "` = ?")
                    .collect(Collectors.joining(" AND ", "(", ")"));
            // (x = ? AND y = ? , ...) OR (x = ? AND y = ? , ...) OR ...
            clauseStr = String.join(" OR ", Collections.nCopies(entities.size(), valueStr));
            params = entities.stream()
                    .flatMap(entity -> allKeys.stream()
                            .map(key -> {
                                Field field = allFields.get(key);
                                return new Pair<>(field.getType(), Utils.getFieldValue(entity, field));
                            }))
                    .collect(Collectors.toList());
        }

        String sql = "DELETE FROM `" + tableInfo.tableName + "` WHERE " + clauseStr;

        return execute0(sql, params)
                .fetch()
                .rowsUpdated();
    }

    @Override
    public <T> Mono<Boolean> deleteById(Class<T> clazz, @NonNull Object id) {
        return byId("DELETE", "", clazz, id)
                .fetch()
                .rowsUpdated()
                .map(count -> count == 1);
    }

    @Override
    public <T> Mono<Integer> deleteAll(Class<T> clazz) {
        TableInfo<?> tableInfo = TableInfo.of(clazz);

        String sql = "DELETE FROM `" + tableInfo.tableName + "`";

        return execute0(sql)
                .fetch()
                .rowsUpdated();
    }

    @Override
    public <T> Flux<T> select(Class<T> clazz, String sql, Object... params) {
        return execute0(sql, params)
                .map(new RowMapper<>(clazz))
                .all();
    }

    @Override
    public Mono<Integer> update(String sql, Object... params) {
        return execute0(sql, params)
                .fetch()
                .rowsUpdated();
    }

    @Override
    public DatabaseClient.GenericExecuteSpec execute(String sql, Object... params) {
        return execute0(sql, params);
    }

    <T> DatabaseClient.GenericExecuteSpec byId(@NonNull String prefix, @NonNull String suffix, Class<T> clazz, @NonNull Object id) {
        TableInfo<T> tableInfo = TableInfo.of(clazz);

        Map<String, Object> values = tableInfo.getIdValues(id);
        Set<String> keys = values.keySet();

        String clauseStr = keys.stream().map(key -> "`" + key + "` = ?").collect(Collectors.joining(", "));
        String sql = prefix + " FROM `" + tableInfo.tableName + "` WHERE " + clauseStr + suffix;
        DatabaseClient.GenericExecuteSpec executeSpec = databaseClient.execute(sql);

        int i = 0;

        for (String key : keys) {
            Object value = values.get(key);

            if (value == null) {
                executeSpec = executeSpec.bindNull(i, tableInfo.allFields.get(key).getType());
            } else {
                executeSpec = executeSpec.bind(i, value);
            }

            i++;
        }

        return executeSpec;
    }

    DatabaseClient.GenericExecuteSpec execute0(String sql) {
        return databaseClient.execute(sql);
    }

    /**
     * @param params each parameter MUST NOT be null
     */
    DatabaseClient.GenericExecuteSpec execute0(String sql, Object... params) {
        List<Pair<? extends Class<?>, Object>> pairs = Stream.of(params).map(param -> new Pair<>(param.getClass(), param)).collect(Collectors.toList());
        return execute0(sql, pairs);
    }

    /**
     * Since 1.1.
     */
    private DatabaseClient.GenericExecuteSpec execute0(String sql, List<Pair<? extends Class<?>, Object>> params) {
        DatabaseClient.GenericExecuteSpec execute = databaseClient.execute(sql);

        for (int i = 0; i < params.size(); i++) {
            Pair<? extends Class<?>, Object> pair = params.get(i);
            Object value = pair.getValue();

            execute = (value == null) ? execute.bindNull(i, pair.getKey()) : execute.bind(i, value);
        }

        return execute;
    }

    <T> DatabaseClient.GenericInsertSpec<Map<String, Object>> insertSpec(TableInfo<T> tableInfo, T entity) {
        DatabaseClient.GenericInsertSpec<Map<String, Object>> insertSpec = databaseClient.insert().into(tableInfo.tableName);

        for (String key : tableInfo.allFields.keySet()) {
            Field field = tableInfo.allFields.get(key);
            Column column = tableInfo.allColumns.get(key);
            Object value = Utils.getFieldValue(entity, field);

            if (value == null) {
                if (column.nullable()) {
                    insertSpec = insertSpec.nullValue(key, field.getType());
                } else {
                    if (column.noDefault()) {
                        throw new R2dbcException("Table [" + tableInfo.tableName + "]: " + column.value() + " cannot be set to NULL.");
                    }
                }
            } else {
                insertSpec = insertSpec.value(key, value);
            }
        }

        return insertSpec;
    }

    /**
     * 1.1: Supports NULL in criteria.
     */
    <T> DatabaseClient.UpdateSpec updateSpec(TableInfo<T> tableInfo, T entity) {
        Update update = null;

        List<String> nonKeys = tableInfo.allFields.keySet().stream().filter(s -> !tableInfo.allKeys.contains(s)).collect(Collectors.toList());

        for (String key : nonKeys) {
            Object value = Utils.getFieldValue(entity, tableInfo.allFields.get(key));
            update = (update == null) ? Update.update(key, value) : update.set(key, value);
        }

        Criteria criteria = null;

        for (String key : tableInfo.allKeys) {
            Object value = Utils.getFieldValue(entity, tableInfo.allFields.get(key));
            if (value == null) {
                criteria = criteria == null ? Criteria.where(key).isNull() : criteria.and(key).isNull();
            } else {
                criteria = criteria == null ? Criteria.where(key).is(value) : criteria.and(key).is(value);
            }
        }

        assert update != null;
        assert criteria != null;

        return databaseClient
                .update()
                .table(tableInfo.tableName)
                .using(update)
                .matching(criteria);
    }

}
