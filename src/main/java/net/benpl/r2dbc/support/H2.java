package net.benpl.r2dbc.support;

import org.springframework.data.r2dbc.core.DatabaseClient;

public class H2 extends Abstract {

    public H2(DatabaseClient databaseClient) {
        super(databaseClient);
    }

}
