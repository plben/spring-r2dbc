package net.benpl.r2dbc.support;

import org.springframework.data.r2dbc.core.DatabaseClient;

public class Mysql extends Abstract {

    public Mysql(DatabaseClient databaseClient) {
        super(databaseClient);
    }

}
