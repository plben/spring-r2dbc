package net.benpl.r2dbc.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

/**
 * Indicates the column as primary key.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {FIELD})
public @interface Id {
}
