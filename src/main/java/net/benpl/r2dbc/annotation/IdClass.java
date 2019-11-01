package net.benpl.r2dbc.annotation;

import java.lang.annotation.*;

/**
 * Indicates the composite primary key of this table.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Inherited
public @interface IdClass {
    /**
     * The composite primary key class.
     */
    Class<?> value();
}
