package org.babyfish.jimmer.sql.ast.impl;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.impl.table.TableImplementor;
import org.babyfish.jimmer.sql.ast.impl.table.RootTableResolver;
import org.babyfish.jimmer.sql.ast.table.Table;
import org.babyfish.jimmer.sql.ast.table.spi.TableProxy;
import org.babyfish.jimmer.sql.runtime.TableUsedState;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class AstContext implements RootTableResolver {

    private final JSqlClient sqlClient;

    private final Map<Table<?>, TableUsedState> tableUsedStateMap = new HashMap<>();

    private final LinkedList<AbstractMutableStatementImpl> stack = new LinkedList<>();

    public AstContext(JSqlClient sqlClient) {
        this.sqlClient = sqlClient;
    }

    public JSqlClient getSqlClient() {
        return sqlClient;
    }

    public void useTableId(Table<?> table) {
        tableUsedStateMap.computeIfAbsent(table, t -> TableUsedState.ID_ONLY);
    }

    public void useTable(Table<?> table) {
        tableUsedStateMap.put(table, TableUsedState.USED);
    }

    public TableUsedState getTableUsedState(Table<?> table) {
        TableUsedState state = tableUsedStateMap.get(table);
        return state != null ? state : TableUsedState.NONE;
    }

    public void pushStatement(AbstractMutableStatementImpl statement) {
        stack.push(statement);
    }

    public void popStatement() {
        stack.pop();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <E> TableImplementor<E> resolveRootTable(Table<E> table) {
        if (table instanceof TableImplementor<?>) {
            return (TableImplementor<E>) table;
        }
        TableImplementor<E> tableImplementor = ((TableProxy<E>)table).__unwrap();
        if (tableImplementor != null) {
            return tableImplementor;
        }
        for (AbstractMutableStatementImpl statement : stack) {
            if (table == statement.getTable()) {
                return (TableImplementor<E>) statement.getTableImplementor();
            }
        }
        throw new IllegalArgumentException("Cannot resolve the root table");
    }

    public AbstractMutableStatementImpl getStatement() {
        return stack.peek();
    }
}
