---
sidebar_position: 5
title: Table joins
---

This article will introduce the table joins, it contains four parts

1. Dynamic join
2. Phantom join
3. Half join
4. Inverse join

## Dynamic join

:::tip
Dynamic table join is a very unique feature of jimmer-sql, and it is one of the features that distinguish jimmer-sql from other ORMs.

Dynamic table join is very useful, but it is not convenient to implement it by classical means, even if the controllability-oriented myBatis is used to implement this function.
:::

### Example

Let's look at an example of a dynamic join.

```java
public class BookRepository {

    private final SqlClient sqlClient;

    public BookRepository(SqlClient sqlClient) {
        this.sqlClient = sqlClient;
    }

    public List<Book> findBooks(
        @Nullable String name, 
        @Nullable String storeName,
        @Nullable String storeWebsite
    ) {
        return sqlClient
            .createQuery(
                BookTable.class, (q, book) -> {
                    if (name != null) {
                        q.where(book.name().eq(name));
                    }
                    if (storeName != null) {
                        q.where(
                            book
                                // highlight-next-line
                                .store() // α
                                .name()
                                .eq(storeName)
                        );
                    }
                    if (storeWebsite != null) {
                        q.where(
                            book
                                // highlight-next-line
                                .store() // β
                                .website()
                                .eq(storeWebsite)
                        );
                    }
                    return q.select(book);
                }
            )
            .execute();
    }
}
```

This is a typical dynamic query, and all three query parameters are allowed to be null.

- `name` is specified, but `storeName` and `storeWebsite` are still null.

    At this time, the code at `α` and `β` will not be executed, and the resulting SQL will not contain any joins.

    ```sql
    select 
        tb_1_.ID, 
        tb_1_.NAME, 
        tb_1_.EDITION, 
        tb_1_.PRICE, 
        tb_1_.STORE_ID 
    from BOOK as tb_1_ 
        where tb_1_.NAME = ?
    ```
- `name` and `storeName` are specified, but `storeWebsite` is still null.

    At this time, the table join at `α` takes effect but the code at `β` will not be executed, and the final generated SQL is as follows.

    ```sql
    select 
        tb_1_.ID, 
        tb_1_.NAME, 
        tb_1_.EDITION, 
        tb_1_.PRICE, 
        tb_1_.STORE_ID 
    from BOOK as tb_1_ 
    /* highlight-start */
    inner join BOOK_STORE as tb_2_ 
        on tb_1_.STORE_ID = tb_2_.ID
    /* highlight-end */ 
    where 
        tb_1_.NAME = ? 
    and 
        tb_2_.NAME = ?
    ```
- `name` and `storeWebsite` are specified, but `storeName` is still null.

    At this time, the table join at `β` takes effect but the code at `α` will not be executed, and the final generated SQL is as follows.

    ```sql
    select 
        tb_1_.ID, 
        tb_1_.NAME, 
        tb_1_.EDITION, 
        tb_1_.PRICE, 
        tb_1_.STORE_ID 
    from BOOK as tb_1_ 
    /* highlight-start */
    inner join BOOK_STORE as tb_2_ 
        on tb_1_.STORE_ID = tb_2_.ID 
    /* highlight-end */
    where 
        tb_1_.NAME = ? 
    and 
        tb_2_.WEBSITE = ?
    ```

- Specify all parameters, `name`, `storeName` and `storeWebsite` are non-null.
    
    At this time, the table joins at `α` and `β` take effect, this situation is called a join conflict.
    
    This conflict does not cause any problems, because in the final SQL, the conflicting table joins will be merged into one instead of join multiple times.

    ```sql
    select 
        tb_1_.ID, 
        tb_1_.NAME, 
        tb_1_.EDITION, 
        tb_1_.PRICE, 
        tb_1_.STORE_ID 
    from BOOK as tb_1_ 
        /* highlight-start */
        inner join BOOK_STORE as tb_2_ on 
            tb_1_.STORE_ID = tb_2_.ID 
        /* highlight-end */
    where 
        tb_1_.NAME = ? 
    and 
        tb_2_.NAME = ? 
    and 
        tb_2_.WEBSITE = ?
    ```

:::info
summary

1. There is no need to use local variables to remember join objects like other ORMs, temporary join objects can be created and used anywhere in SQL.

2. More importantly, there is no need to consider whether there is some conflict between these join objects, conflicting join objects will be automatically merged.

This feature makes jimmer-sql extremely suitable for implementing complex dynamic queries. It is also one of the motivations for the creation of the jimmer-sql project.
:::

Also, if a table join is created but not used, the table join will be ignored and will not be generated in the final SQL.

### Conflict merge rules

The above example is very simple, the table join in has only one layer. In fact, it is possible to create deeper join objects

```java
q.where(
    store // Assuming that the store is a TableEx, 
          // and collection association is allowed
        // highlight-next-line
        .books().authors() // multi-level join
        .firstName()
        .eq("X")
);
```

Or

```java
q.where(
    author // Assuming that the author is a TableEx, 
           // and collection association is allowed
        .books().store() // multi-level join
        .name()
        .eq("X")
);
```

It can be seen that the join object is actually a path of arbitrary length, which can also be called a join path. The join path contains from 1 to infinity of join nodes.

In order to make the description more general, let's look at three relatively long join paths *(there is no such long table join path in an actual project, just to illustrate)*.

1. a -> b -> c -> d -> e -> f -> g
2. a -> b -> c -> h -> i -> j
3. a -> x -> y -> z -> a-> b -> c -> d

In order to eliminate conflicts, jimmer-sql will merge the nodes in these paths into a tree
```
-+-a
 |
 +----+-b
 |    |
 |    \----+-c 
 |         |
 |         +----+-d
 |         |    |
 |         |    \----+-e
 |         |         |
 |         |         \----+-f
 |         |              |
 |         |              \------g
 |         |
 |         \----+-h
 |              |
 |              \----+-i
 |                   |
 |                   \------j
 |
 \----+-x
      |
      \----+-y
           |
           \----+-z
                |
                \----+-a
                     |
                     \----+-b
                          |
                          \----+-c
                               |
                               \------d
```

jimmer-sql will generate the join clause in the final SQL based on this tree.

Another rule that needs to be explained is the join type. The method that creates the join object has a parameter to specify the join type, for example. For example, deveoper can create left join like this:

```java
book.store(JoinType.LEFT);
```
> If this parameter is not specified, the default behavior is inner join.

The join type merging rules are as follows:

- If the join types of the conflicting join nodes are all the same, the join type remain unchanged after the merge.
- Otherwise, the merged join type is inner join.

## Phantom join

Phantom join is a very simple optimization concept, which can be understood by comparing it with ordinary join.

Let's first look at an example of a ordinary table join.

```java
List<Book> books = sqlClient
    .createQuery(BookTable.class, (q, book) -> {
        return q
            .where(book.store().name().eq("MANNING"))
            .select(book);
    })
    .execute();
books.stream().forEach(System.out::println);
```

The generated SQL is as follows:

```sql
select 
    tb_1_.ID, 
    tb_1_.NAME, 
    tb_1_.EDITION, 
    tb_1_.PRICE, 
    tb_1_.STORE_ID 
from BOOK as tb_1_ 
    /* highlight-start */
    inner join BOOK_STORE as tb_2_ 
        on tb_1_.STORE_ID = tb_2_.ID
    /* highlight-end */     
where 
    tb_2_.NAME = ?
```

Now, let's look at an example of phantom join

```java
List<Book> books = sqlClient
    .createQuery(BookTable.class, (q, book) -> {
        return q
            .where(
                book
                    .store()
                    // highlight-next-line
                    .id() // Only id is accessed
                    .eq(
                        UUID.fromString(
                            "2fa3955e-3e83-49b9-902e-0465c109c779"
                        )
                    )
            )
            .select(book);
    })
    .execute();
books.stream().forEach(System.out::println);
```

At this time, the generated SQL is as follows:

```sql
select 
    tb_1_.ID, 
    tb_1_.NAME, 
    tb_1_.EDITION, 
    tb_1_.PRICE, 
    tb_1_.STORE_ID 
from BOOK as tb_1_ 
    where tb_1_.STORE_ID = ?
```

We don't see any table joins in the SQL, we only see the condition `tb_1_.STORE_ID = ?` based on a foreign key.

Reason: For many-to-one associations based on foreign key, the id of the parent table is actually the foreign key of the child table.

:::info
If join object based on foreign key is created but no properties of the associated object other than id is accessed, the join object will be treated as a phantom join. 

Phantom join will not generate join clause in SQL.
:::

## Half join

Half join is a similar concept to a phantom join, but for associations based on middle table.

Let's first look at a normal join based on middle table

```java
List<UUID> bookIds = sqlClient
    .createQuery(BookTable.class, (q, book) -> {
        return q
            .where(
                ((BookTableEx)book)
                    .authors()
                    .firstName()
                    .eq("Alex")
            )
            .select(book.id());
    })
    .distinct()
    .execute();
bookIds.forEach(System.out::println);
```

The generated SQL is as follows:
```sql
select 
    distinct tb_1_.ID 
from BOOK as tb_1_ 
/* highlight-start */
inner join BOOK_AUTHOR_MAPPING as tb_2_ 
    on tb_1_.ID = tb_2_.BOOK_ID 
inner join AUTHOR as tb_3_ on 
    tb_2_.AUTHOR_ID = tb_3_.ID
/* highlight-end */ 
where tb_3_.FIRST_NAME = ?
```

We see that the association based on the middle table will generate two SQL join clauses:

- Step1: Join to middle table
    `inner join BOOK_AUTHOR_MAPPING as tb_2_ on tb_1_.ID = tb_2_.BOOK_ID `

- Step2: join to target table
    `inner join AUTHOR as tb_3_ on tb_2_.AUTHOR_ID = tb_3_.ID`

Next, let's look at half join

```java
List<UUID> bookIds = sqlClient
    .createQuery(BookTable.class, (q, book) -> {
        return q
            .where(
                ((BookTableEx)book)
                    .authors()
                    // highlight-next-line
                    .id() // Only access id
                    .eq(
                        UUID.fromString(
                            "1e93da94-af84-44f4-82d1-d8a9fd52ea94"
                        )
                    )
            )
            .select(book.id());
    })
    .distinct()
    .execute();
bookIds.forEach(System.out::println);
```

At this time, the generated SQL is as follows:

```sql
select 
    distinct tb_1_.ID 
from BOOK as tb_1_ 
/* highlight-start */
inner join BOOK_AUTHOR_MAPPING as tb_2_ 
    on tb_1_.ID = tb_2_.BOOK_ID 
/* highlight-end */
where tb_2_.AUTHOR_ID = ?
```

At this time, we only see one SQL join clause, not two

Reason: The id of the associated object is actually a foreign key of middle table.

:::info
If join object based on middle table is created but no properties of the associated object other than id is accessed, the join object will be treated as a half join. 

Half join will generate only one join clause point to middle table in SQL, not two.
:::

## Inverse join

All table joins we've discussed so far only apply when java property is already defined in the entity interface.

If the developer defines a bidirectional association between entity interfaces

`A <--> B`

We can join from either end to the other, either from `A` to `B` or from `B` to `A`.

However, sometimes developers only define one-way associations in entity interface.

`A --> B`
Now, we can only join from `A` to B, not from `B` to `A`.

Admittedly, subqueries can solve all problems. However, jimmer-sql still allows you to solve this problem with special table joins, which are called inverse joins.

To better illustrate the inverse join, let's first look at normal join.

```java
q.where(
    book
        // Normal join
        // highlight-next-line
        .authors()
        .firstName()
        .eq("Alex")
);
```

There are two ways to write the inverse join that is completely equivalent to it

1. Weak typing
    ```java
    q.where(
        book
            // Reverse `Auhtor.books`,
            // it's actually `Book.authors`
            // highlight-next-line
            .inverseJoin(Author.class, "books")
            .firstName()
            .eq("Alex")
    );
    ```

2. Strong typing
    ```java
    q.where(
        book
            /// Reverse `Auhtor.books`,
            // it's actually `Book.authors`
            .inverseJoin(
                AuthorTableEx.class, 
                AuthorTableEx::books
            )
            // highlight-end
            .firstName()
            .eq("Alex")
    );
    ```

:::info

Notice

While inverse joins are easy to understand, the code is relatively obscure to read. Because of this, it should not to be abused.

It should only be used in some special cases, such as

1. The definition of the entity interface belongs to the third party, not the code that can be controlled by your team, and the third-party entity only defines a one-way association, no bidrectional association.

2. When developing some generic frameworks, it cannot be assumed that users defined bidirectional associations.

However, in business system development, you should defining bidirectional associations in entity interfaces instead of using inverse joins.
:::