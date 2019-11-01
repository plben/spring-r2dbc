package net.benpl.r2dbc.exception;

public class R2dbcException extends io.r2dbc.spi.R2dbcException {
    public R2dbcException(String reason) {
        super(reason);
    }

    public R2dbcException(String reason, Throwable cause) {
        super(reason, cause);
    }

    public R2dbcException(Throwable cause) {
        super(cause);
    }
}
