package org.babyfish.jimmer.sql.query;

import org.babyfish.jimmer.sql.common.AbstractQueryTest;
import org.babyfish.jimmer.sql.model.hr.DepartmentFetcher;
import org.babyfish.jimmer.sql.model.hr.DepartmentTable;
import org.junit.jupiter.api.Test;

public class LogicalDeletedTest extends AbstractQueryTest {

    @Test
    public void testQuery() {
        DepartmentTable table = DepartmentTable.$;
        executeAndExpect(
                getSqlClient()
                        .createQuery(table)
                        .where(table.asTableEx().employees().name().eq("Jessica"))
                        .select(
                                table.fetch(
                                        DepartmentFetcher.$
                                                .name()
                                                .employeeCount()
                                )
                        ),
                ctx -> {
                    ctx.sql(
                            "select " +
                                    "tb_1_.ID, " +
                                    "tb_1_.NAME, " +
                                    "(select count(*) from employee where department_id = tb_1_.id) " +
                                    "from DEPARTMENT tb_1_ " +
                                    "inner join EMPLOYEE tb_2_ on tb_1_.ID = tb_2_.DEPARTMENT_ID " +
                                    "where tb_2_.NAME = ? " +
                                    "and tb_1_.DELETED_TIME is null " +
                                    "and tb_2_.DELETED_UUID is null"
                    );
                    ctx.rows(
                            "[{\"id\":\"1\",\"name\":\"Market\",\"employeeCount\":2}]"
                    );
                }
        );
    }
}
