package org.babyfish.jimmer.sql.json;

import org.babyfish.jimmer.sql.ast.Expression;
import org.babyfish.jimmer.sql.ast.tuple.Tuple2;
import org.babyfish.jimmer.sql.model.pg.JsonWrapper;
import org.babyfish.jimmer.sql.model.pg.JsonWrapperDraft;
import org.babyfish.jimmer.sql.model.pg.JsonWrapperTable;
import org.babyfish.jimmer.sql.model.pg.Point;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

public class ScalarProviderTest extends AbstractJsonTest {

    @Test
    public void test() {
        sqlClient().getEntities().save(
                JsonWrapperDraft.$.produce(draft -> {
                    draft.setId(1L);
                    draft.setPoint(new Point(3, 4));
                    draft.setTags(Arrays.asList("java", "kotlin"));
                    draft.setScores(Collections.singletonMap(1L, 100));
                })
        );
        JsonWrapper wrapper = sqlClient().getEntities().findById(JsonWrapper.class, 1L);
        Assertions.assertEquals(
                "{" +
                        "\"id\":1," +
                        "\"point\":{\"x\":3,\"y\":4}," +
                        "\"tags\":[\"java\",\"kotlin\"]," +
                        "\"scores\":{\"1\":100}}",
                wrapper.toString()
        );


        sqlClient().getEntities().save(
                JsonWrapperDraft.$.produce(draft -> {
                    draft.setId(1L);
                    draft.setPoint(new Point(4, 3));
                    draft.setTags(Arrays.asList("kotlin", "java"));
                })
        );
        wrapper = sqlClient().getEntities().findById(JsonWrapper.class, 1L);
        Assertions.assertEquals(
                "{" +
                        "\"id\":1," +
                        "\"point\":{\"x\":4,\"y\":3}," +
                        "\"tags\":[\"kotlin\",\"java\"]," +
                        "\"scores\":{\"1\":100}}",
                wrapper.toString()
        );

        JsonWrapperTable table = JsonWrapperTable.$;

        sqlClient()
                .createUpdate(table)
                .set(table.tags(), Arrays.asList("java", "kotlin", "scala"))
                .where(table.tags().eq(Arrays.asList("kotlin", "java")))
                .execute();
        wrapper = sqlClient().getEntities().findById(JsonWrapper.class, 1L);
        Assertions.assertEquals(
                "{" +
                        "\"id\":1," +
                        "\"point\":{\"x\":4,\"y\":3}," +
                        "\"tags\":[\"java\",\"kotlin\",\"scala\"]," +
                        "\"scores\":{\"1\":100}}",
                wrapper.toString()
        );

        sqlClient()
                .createUpdate(table)
                .set(table.scores(), Collections.singletonMap(2L, 200))
                .where(
                        Expression.tuple(table.tags(), table.scores()).eq(
                                new Tuple2<>(
                                        Arrays.asList("java", "kotlin", "scala"),
                                        Collections.singletonMap(1L, 100)
                                )
                        )
                )
                .execute();
        wrapper = sqlClient().getEntities().findById(JsonWrapper.class, 1L);
        Assertions.assertEquals(
                "{" +
                        "\"id\":1," +
                        "\"point\":{\"x\":4,\"y\":3}," +
                        "\"tags\":[\"java\",\"kotlin\",\"scala\"]," +
                        "\"scores\":{\"2\":200}}",
                wrapper.toString()
        );
    }
}