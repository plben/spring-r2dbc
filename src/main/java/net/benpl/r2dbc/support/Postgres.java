package net.benpl.r2dbc.support;

import org.springframework.data.r2dbc.core.DatabaseClient;

public class Postgres extends Abstract {

    public Postgres(DatabaseClient databaseClient) {
        super(databaseClient);
    }

}
