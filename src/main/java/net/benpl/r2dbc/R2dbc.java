/*
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

package net.benpl.r2dbc;

import lombok.NonNull;
import net.benpl.r2dbc.exception.R2dbcException;
import net.benpl.r2dbc.support.H2;
import net.benpl.r2dbc.support.Mssql;
import net.benpl.r2dbc.support.Mysql;
import net.benpl.r2dbc.support.Postgres;
import org.springframework.data.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface R2dbc {
    /**
     * Returns the number of entities available.
     *
     * @param clazz the entity type.
     * @return {@link Mono} emitting the number of entities.
     */
    <T> Mono<Long> count(Class<T> clazz);

    /**
     * Returns whether an entity with the id exists.
     *
     * @param clazz the entity type.
     * @param id    must not be {@literal null}.
     * @return {@link Mono} emitting {@literal true} if an entity with the given id exists, {@literal false} otherwise.
     */
    <T> Mono<Boolean> existsById(Class<T> clazz, @NonNull Object id);

    /**
     * Saves a given entity. Use the returned instance for further operations as the save operation might have changed the
     * entity instance completely.
     *
     * @param entity must not be {@literal null}.
     * @return {@link Mono} emitting the saved entity.
     */
    <T> Mono<T> save(T entity);

    /**
     * Retrieves an entity by its id.
     *
     * @param clazz the entity type.
     * @param id    must not be {@literal null}.
     * @return {@link Mono} emitting the entity with the given id or {@link Mono#empty()} if none found.
     */
    <T> Mono<T> findById(Class<T> clazz, @NonNull Object id);

    /**
     * Returns all instances of the type.
     *
     * @param clazz the entity type.
     * @return {@link Flux} emitting all entities.
     */
    <T> Flux<T> findAll(Class<T> clazz);

    /**
     * Deletes the given entity.
     *
     * @param entity must not be {@literal null}.
     * @return {@link Mono} emitting {@literal true} if success, {@literal false} otherwise.
     */
    <T> Mono<Boolean> delete(@NonNull T entity);

    /**
     * Deletes the given entities.
     *
     * @param entities must not be {@literal null}.
     * @return {@link Mono} the number of entities deleted.
     */
    <T> Mono<Integer> deleteAll(List<T> entities);

    /**
     * Deletes the entity with the given id.
     *
     * @param clazz the entity type.
     * @param id    must not be {@literal null}.
     * @return {@link Mono} emitting {@literal true} if success, {@literal false} otherwise.
     */
    <T> Mono<Boolean> deleteById(Class<T> clazz, @NonNull Object id);

    /**
     * Deletes all entities.
     *
     * @param clazz the entity type.
     * @return {@link Mono} the number of entities deleted.
     */
    <T> Mono<Integer> deleteAll(Class<T> clazz);

    /**
     * Performs SELECT operation with given SQL and parameters.
     *
     * @param clazz  the entity type.
     * @param sql    the SQL.
     * @param params the parameters. each parameter must not be {@literal null}
     * @return {@link Flux} emitting the selected entities.
     */
    <T> Flux<T> select(Class<T> clazz, String sql, Object... params);

    /**
     * Performs DELETE/UPDATE/...(update) operation with given SQL and parameters.
     *
     * @param sql    the SQL.
     * @param params the parameters. each parameter must not be {@literal null}
     * @return {@link Mono} the number of entities updated.
     */
    Mono<Integer> update(String sql, Object... params);

    /**
     * Executes SQL with given parameters.
     *
     * @param sql    the SQL.
     * @param params the parameters. each parameter must not be {@literal null}
     * @return {@link DatabaseClient.GenericExecuteSpec} user to decide how to handle the execution result.
     */
    DatabaseClient.GenericExecuteSpec execute(String sql, Object... params);

    /**
     * Initializes a {@link R2dbc} instance.
     *
     * @param databaseClient DatabaseClient instance.
     * @param type           R2dbc type
     * @return Created {@link R2dbc} instance.
     */
    static R2dbc of(DatabaseClient databaseClient, Type type) {
        switch (type) {
            case MYSQL:
                return new Mysql(databaseClient);

            case PG:
                return new Postgres(databaseClient);

            case MSSQL:
                return new Mssql(databaseClient);

            case H2:
                return new H2(databaseClient);

            default:
                throw new R2dbcException("R2dbc: invalid type " + type + ".");
        }
    }
}
