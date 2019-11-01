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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
                .map(row -> (Long) row.get(0))
                .first();
    }

    @Override
    public <T> Mono<Boolean> existsById(Class<T> clazz, @NonNull Object id) {
        return byId("SELECT EXISTS(SELECT *", ")", clazz, id)
                .map(row -> (Long) row.get(0) == 1)
                .first();
    }

    @Override
    public <T> Mono<T> save(@NonNull T entity) {
        TableInfo<T> tableInfo = TableInfo.of(entity);

        if (tableInfo.isKeyNull(entity)) {
            if (tableInfo.aiField != null) {
                return insertSpec(tableInfo, entity)
                        .map(row -> {
                            Object value = tableInfo.aiValueFrom((Long) row.get(0));
                            return tableInfo.setFieldValue(entity, tableInfo.aiField, value);
                        })
                        .first();
            } else {
                throw new R2dbcException("Table [" + tableInfo.tableName + "]: failed to save record. (primary key is NULL)");
            }
        } else {
            return updateSpec(tableInfo, entity)
                    .fetch()
                    .rowsUpdated()
                    .map(count -> entity);
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

        List<String> allKeys = tableInfo.allKeys;
        Map<String, Field> allFields = tableInfo.allFields;

        // (x = ? AND y AND ?, ...)
        String clauseStr = allKeys.stream()
                .map(key -> "`" + key + "` = ?")
                .collect(Collectors.joining(" AND "));
        List<Object> params = allKeys.stream()
                .map(allFields::get)
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
        List<Object> params;

        if (allKeys.size() == 1) {
            String key = allKeys.get(0);
            clauseStr = key + " IN (" + String.join(", ", Collections.nCopies(entities.size(), "?")) + ")";
            params = entities.stream()
                    .map(entity -> tableInfo.getFieldValue(entity, allFields.get(key)))
                    .collect(Collectors.toList());
        } else {
            // (x = ? AND y AND ?, ...)
            String valueStr = allKeys.stream()
                    .map(key -> "`" + key + "` = ?")
                    .collect(Collectors.joining(" AND ", "(", ")"));
            // (x = ? AND y AND ?, ...) OR (x = ? AND y AND ?, ...) OR ...
            clauseStr = String.join(" OR ", Collections.nCopies(entities.size(), valueStr));
            params = entities.stream()
                    .flatMap(entity -> allKeys.stream()
                            .map(key -> tableInfo.getFieldValue(entity, allFields.get(key)))
                            .collect(Collectors.toList())
                            .stream())
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
     * @param params 成员不可以为NULL
     */
    DatabaseClient.GenericExecuteSpec execute0(String sql, Object... params) {
        List<Pair<Class<?>, Object>> pairs = Stream.of(params).map(param -> new Pair<Class<?>, Object>(param.getClass(), param)).collect(Collectors.toList());

        DatabaseClient.GenericExecuteSpec execute = databaseClient.execute(sql);

        for (int i = 0; i < pairs.size(); i++) {
            Pair<Class<?>, Object> pair = pairs.get(i);
            Object value = pair.getValue();

            if (value == null) {
                execute = execute.bindNull(i, pair.getKey());
            } else {
                execute = execute.bind(i, value);
            }
        }

        return execute;
    }

    <T> DatabaseClient.GenericInsertSpec<Map<String, Object>> insertSpec(TableInfo<T> tableInfo, T entity) {
        DatabaseClient.GenericInsertSpec<Map<String, Object>> insertSpec = databaseClient.insert().into(tableInfo.tableName);

        for (String key : tableInfo.allFields.keySet()) {
            Field field = tableInfo.allFields.get(key);
            Column column = tableInfo.allColumns.get(key);
            Object value = tableInfo.getFieldValue(entity, field);

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

    <T> DatabaseClient.UpdateSpec updateSpec(TableInfo<T> tableInfo, T entity) {
        Update update = null;

        // 不包括主键的其它字段
        List<String> nonKeys = tableInfo.allFields.keySet().stream().filter(s -> !tableInfo.allKeys.contains(s)).collect(Collectors.toList());

        for (String key : nonKeys) {
            Field field = tableInfo.allFields.get(key);
            Object value = tableInfo.getFieldValue(entity, field);

            if (value == null) {
                update = (update == null) ? Update.update(key, null) : update.set(key, null);
            } else {
                update = (update == null) ? Update.update(key, value) : update.set(key, value);
            }
        }

        Criteria criteria = null;

        for (String key : tableInfo.allKeys) {
            Object value = tableInfo.getFieldValue(entity, tableInfo.allFields.get(key));
            criteria = criteria == null ? Criteria.where(key).is(value) : criteria.and(key).is(value);
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