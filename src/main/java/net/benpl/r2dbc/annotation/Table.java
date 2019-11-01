package net.benpl.r2dbc.annotation;

import java.lang.annotation.*;

/**
 * Associates a java class with a database table.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Inherited
public @interface Table {

    /**
     * The mapping table name.
     */
    String value() default "";

    /**
     * Should be "TABLE" or "VIEW".
     */
    String type();
}
