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

package net.benpl.r2dbc.annotation;

import java.lang.annotation.*;

/**
 * Maps an attribute to a database column.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
@Documented
public @interface Column {

    /**
     * The mapping column.
     */
    String value() default "";

    /**
     * If this column is primary key.
     */
    boolean primary() default false;

    /**
     * If this column is AUTO_INCREMENT.
     */
    boolean autoIncrement() default false;

    /**
     * The string size. (For reference only, not used internally)
     */
    long size() default 0;

    /**
     * The number of digits after the decimal point. (For reference only, not used internally)
     */
    int scale() default 0;

    /**
     * The number of significant digits. (For reference only, not used internally)
     */
    int precision() default 0;

    /**
     * If this column is nullable.
     */
    boolean nullable() default false;

    /**
     * Is default value is available.
     */
    boolean noDefault() default false;
}
