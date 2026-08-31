package com.github.stimur1709.cloudops;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;

public final class SqlStatementRecorder implements StatementInspector {

    private final List<String> statements = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(String sql) {
        statements.add(sql);
        return sql;
    }

    public void clear() {
        statements.clear();
    }

    public List<String> statements() {
        return List.copyOf(statements);
    }
}
