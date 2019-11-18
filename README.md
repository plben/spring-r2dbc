# R2DBC (Reactive Relational Database Connectivity) Client beyond spring-data-r2dbc

## What is it

This git looks much like the mixture of JdbcTemplate & CrudRepository of Spring-Data. Except the implementation is much straightforward, "I don't want to guess. Just show me what you need.".

I made this middle layer for my business projects because @Query is no longer the best choice for SQL mapping in reactive world, I think. On the other hand, ID makes CrudRepository too complicated, so I decoupled it.

## What Introduced

### 1. API

```java
// Returns the number of entities available.
<T> Mono<Long> count(Class<T> clazz);

// Returns whether an entity with the id exists.
<T> Mono<Boolean> existsById(Class<T> clazz, @NonNull Object id);

// Saves a given entity. Use the returned instance for further operations
// as the save operation might have changed the entity instance completely.
<T> Mono<T> save(T entity);

// Retrieves an entity by its id.
<T> Mono<T> findById(Class<T> clazz, @NonNull Object id);

// Returns all instances of the type.
<T> Flux<T> findAll(Class<T> clazz);

// Deletes the given entity.
<T> Mono<Boolean> delete(@NonNull T entity);

// Deletes the given entities.
<T> Mono<Integer> deleteAll(List<T> entities);

// Deletes the entity with the given id.
<T> Mono<Boolean> deleteById(Class<T> clazz, @NonNull Object id);

// Deletes all entities.
<T> Mono<Integer> deleteAll(Class<T> clazz);

// Performs SELECT operation with given SQL and parameters.
<T> Flux<T> select(Class<T> clazz, String sql, Object... params);

// Performs DELETE/UPDATE/...(update) operation with given SQL and parameters.
Mono<Integer> update(String sql, Object... params);

// Executes SQL with given parameters.
DatabaseClient.GenericExecuteSpec execute(String sql, Object... params);
```

### 2. Annotation

- @Table

  Maps a class to a database table.

- @Column

  Maps an attribute to a database column.
  
- @IdClass

  Refers to the composite primary key.
  
### 3. Dependency

Dependency of this git:

```xml
<dependency>
    <groupId>net.benpl</groupId>
    <artifactId>spring-r2dbc</artifactId>
    <version>1.1</version>
</dependency>
```

*Note: You need an under layer R2DBC driver as well, for example MySQL:*

```xml
<dependency>
    <groupId>dev.miku</groupId>
    <artifactId>r2dbc-mysql</artifactId>
    <version>0.8.0.RC2</version>
</dependency>
```

Existing R2DBC drivers can be found at:

- [Postgres](https://github.com/r2dbc/r2dbc-postgresql)
- [H2](https://github.com/r2dbc/r2dbc-h2)
- [Microsoft SQL Server](https://github.com/r2dbc/r2dbc-mssql)
- [MySQL](https://github.com/mirromutth/r2dbc-mysql)

## Getting Started

### 1. Spring Configuration

```java
@Configuration
@EnableTransactionManagement
public class R2dbcConfigure extends AbstractR2dbcConfiguration {
    @Bean
    @Override
    public ConnectionFactory connectionFactory() {
        MySqlConnectionConfiguration connectionConfiguration = MySqlConnectionConfiguration.builder()
                .host("localhost")
                .port(3306)
                .database("test")
                .username("username")
                .password("password")
                .build();

        return MySqlConnectionFactory.from(connectionConfiguration);

        // Connection pool r2dbc-pool can be configured here as well.
    }

    @Bean
    public R2dbcTransactionManager transactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }

    @Bean
    public R2dbc r2dbc(DatabaseClient databaseClient) {
        return R2dbc.of(databaseClient, Type.MYSQL);
    }
}
```

### 2. Table Entity

- Entity without composite primary key:

```java
@Data
@Table(value = "t_sys_user", type = "TABLE")
public class TSysUser {
    @Column(value = "id", primary = true, autoIncrement = true, precision = 11)
    private Integer id;

    @Column(value = "username", size = 100, noDefault = true)
    private String username;

    @Column(value = "password", size = 255, noDefault = true)
    private String password;

    @Column(value = "state", precision = 4)
    private Byte state;

    @Column(value = "phone", size = 20, nullable = true)
    private String phone;
}
```

- Entity with composite primary key:

```java
@Data
@Table(value = "t_sys_user_role", type = "TABLE")
@IdClass(TSysUserRoleKey.class)
public class TSysUserRole {
    @Column(value = "user_id", primary = true, precision = 11, noDefault = true)
    private Integer userId;

    @Column(value = "role_id", primary = true, precision = 11, noDefault = true)
    private Integer roleId;
}
```

- The composite primary key:

```java
@Data
public class TSysUserRoleKey {
    @Column(value = "user_id", precision = 11, noDefault = true)
    private Integer userId;

    @Column(value = "role_id", precision = 11, noDefault = true)
    private Integer roleId;
}
```

### 3. Using It

- R2dbc bean can be injected into the services.

```java
@Service
public class BusinessService {
    private final R2dbc r2dbc;

    public TestService(R2dbc r2dbc) {
        this.r2dbc = r2dbc;
    }

    @Transactional(rollbackFor = Exception.class)
    public Mono<TSysUser> func1(Integer userId) {
        return r2dbc.findById(TSysUser.class, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Mono<TSysUser> func2(Integer userId) {
        return r2dbc.select(TSysUser.class, "SELECT * FROM t_sys_user WHERE id = ?", userId)
                .single();
    }

    // ...

}
```

- Or, services inherit R2dbc adaptor directly.

```java
@Service
public class BusinessService extends Mysql /*H2/Mssql/Postgres*/ {

    @Transactional(rollbackFor = Exception.class)
    public Mono<TSysUser> func1(Integer userId) {
        return findById(TSysUser.class, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Mono<TSysUser> func2(Integer userId) {
        return select(TSysUser.class, "SELECT * FROM t_sys_user WHERE id = ?", userId)
                .single();
    }

    // ...
}
```

## Other Things

### 1. Table Entity

Usually, generating table entities from database by reverse engineering would be the best choice. The generated mapping will be more consistent. However, [r2dbc-spi](https://github.com/r2dbc/r2dbc-spi) doesn't provide such API. I did not see such mechanism in [r2dbc-mysql](https://github.com/mirromutth/r2dbc-mysql) as well.

So, to achieve table entities **auto-codegen** from database, you have to code it by yourself. Anyway, it is not a big deal. Not above 300 lines of code can make it.

Take [r2dbc-mysql](https://github.com/mirromutth/r2dbc-mysql) for example:

- Git clone [r2dbc-mysql](https://github.com/mirromutth/r2dbc-mysql)
- Enable relevant flags of ColumnDefinitions

  PRIMARY_PART, AUTO_INCREMENT, NO_DEFAULT

- Enable access to MySqlColumnMetadata
- Enable access to MySqlRowMetadata
- Spy on SyntheticMetadataMessage
- Your codegen

### 2. SQL Syntax

JetBrains IDEA:

File -> Settings -> Editor -> Language Injections -> + -> 7.Java Parameter -> ID: MySQL/H2/PostgreSQL/SQL -> Class Methods: net.benpl.r2dbc.R2dbc -> Check all 3 CheckBoxs -> OK -> OK.

*Certainly, you need to setup a datasource in the meantime.*

Other IDEs should have the similar setting.
