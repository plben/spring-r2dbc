package net.benpl.r2dbc.support;

import org.springframework.data.r2dbc.core.DatabaseClient;

public class Mssql extends Abstract {

    public Mssql(DatabaseClient databaseClient) {
        super(databaseClient);
    }

}
