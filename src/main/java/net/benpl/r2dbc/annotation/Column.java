package net.benpl.r2dbc.annotation;

import java.lang.annotation.*;

/**
 * Associates a class field with a table column.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
@Documented
public @interface Column {

    /**
     * The mapping column name.
     */
    String value() default "";

    /**
     * The string size.
     */
    long size() default 0;

    /**
     * The number of digits after the decimal point.
     */
    int scale() default 0;

    /**
     * The number of significant digits.
     */
    int precision() default 0;

    /**
     * If this column is nullable.
     */
    boolean nullable() default false;

    /**
     * Is default value configured or not.
     */
    boolean noDefault() default false;
}
