---
sidebar_position: 5
title: Query middle table
---

:::tip
Querying middle tables is a distinctive small feature of jimmer-sql.
:::

## Middle tables are hidden by the object model

Let's review this entity interface definition code

```java
@Entity
public interface Book {

    @ManyToMany
    @JoinTable(
        // highlight-next-line
        name = "BOOK_AUTHOR_MAPPING",
        joinColumns = 
            @JoinColumn(name = "BOOK_ID"),
        inverseJoinColumns = 
            @JoinColumn(name = "AUTHOR_ID")
    )
    List<Author> authors();

    ...omit other code...
}
```

In the above code, the `BOOK_AUTHOR_MAPPING` table is used as an middle table.

- The table `BOOK` of the database corresponds to Java's entity interface `Book`.
- The table `AUTHOR` of the database corresponds to Java's entity interface `Author`.
- However, the middle table `BOOK_AUTHOR_MAPPING` of the database has no corresponding entity interface in the Java code.

That is, the middle table is hidden by the object model.

## Query the middle table directly

jimmer-sql provides an interesting function, even if the middle table is hidden and has no corresponding entity, it can be directly queried.

```java
List<Association<Book, Author>> associations =
    sqlClient
        // highlight-next-line
        .createAssociationQuery(
            BookTableEx.class,
            BookTableEx::authors,
            (q, association) -> {
                q.where(
                    association.source().id().eq(
                        UUID.fromString(
                            "64873631-5d82-4bae-8eb8-72dd955bfc56"
                        )
                    )
                );
                return q.select(association);
            }
        ).execute();
associations.forEach(System.out::println);
```

Here, `createAssociation` means to create a query based on the middle table, not based on the entity table.

The first two parameters, `BookTableEx.class` and `BookTableEx::authors`, represent the middle table `BOOK_AUTHOR_MAPPING` corresponding to the association `Book.authors`.

The generated SQL is as follows
```sql
select 
    tb_1_.BOOK_ID, 
    tb_1_.AUTHOR_ID 
/* hight-next-line */
from BOOK_AUTHOR_MAPPING as tb_1_
where tb_1_.BOOK_ID = ?
```

Sure enough, this is a query based on an middle table.

The final print result is as follows (the original output is compact, formatted here for ease of reading):

```
Association{
    source={
        "id":"64873631-5d82-4bae-8eb8-72dd955bfc56"
    }, target={
        "id":"1e93da94-af84-44f4-82d1-d8a9fd52ea94"
    }
}
Association{
    source={
        "id":"64873631-5d82-4bae-8eb8-72dd955bfc56"
    }, 
    target={
        "id":"fd6bb6cf-336d-416c-8005-1ae11a6694b5"
    }
}
```

The return data is a list of `Association` objects

```java
package org.babyfish.jimmer.sql.association;

public class Association<S, T> {

    public Association(S source, T target) {
        this.source = source;
        this.target = target;
    }

    public S source() {
        return source;
    }

    public T target() {
        return target;
    }
}
```

`Association<S, T>` represents the middle table entity associated from the `S` type to the `T` type. middle table entities are pseudo-entities and have no id. It has only two properties:

- `source`: The object corresponding to the foreign key of the middle table pointing to the source table.(in this case, it is `Book`).
- `target`: The object corresponding to the foreign key of the middle table pointing to the target table. (in this case, it is`Author`).

:::note

1. In this example, the object format of the `Association` is not defined using an object fetcher (in fact, the `Association` does not support object fetcher either), so the `source` and `targate` return objects with only id.

2. `Author` also has a inverse many-to-many association `Author.books`, which is a mirror of `Book.authors`.
    ```
    @Entity
    public interface Author {

        // highlight-next-line
        @ManyToMany(mappedBy = "authors")
        List<Book> books();

        ...
    }
    ```
    Deveoper can also create middle table query based on `Author.books`, but `source` stands for `Author` and `target` stands for `Book`, contrary to the previous example.
:::

In this example, we only query the middle table itself. So, there are only ids in the `source` and `target` objects.

To get the complete `source` and `target` objects, you can join the tables and return a tuple, like this

```java
List<Tuple2<Book, Author>> tuples =
    sqlClient
        .createAssociationQuery(
            BookTableEx.class,
            BookTableEx::authors,
            (q, association) -> {
                q.where(
                    association.source().id().eq(
                        UUID.fromString(
                            "64873631-5d82-4bae-8eb8-72dd955bfc56"
                        )
                    )
                );
                // highlight-next-line
                return q.select(
                    association.source(),
                    association.target()
                );
            }
        ).execute();
tuples.forEach(System.out::println);
```

The generated SQL is as follows:
```sql
select 

    /* source() */
    tb_1_.BOOK_ID, 
    tb_2_.NAME, 
    tb_2_.EDITION, 
    tb_2_.PRICE, 
    tb_2_.STORE_ID, 

    /* target() */
    tb_1_.AUTHOR_ID, 
    tb_3_.FIRST_NAME, 
    tb_3_.LAST_NAME, 
    tb_3_.GENDER

from BOOK_AUTHOR_MAPPING as tb_1_ 
inner join BOOK as tb_2_ 
    on tb_1_.BOOK_ID = tb_2_.ID 
inner join AUTHOR as tb_3_ 
    on tb_1_.AUTHOR_ID = tb_3_.ID 
where tb_1_.BOOK_ID = ?
```

The final print result is as follows (the original output is compact, formatted here for ease of reading):
```
Tuple2{
    _1={
        "id":"64873631-5d82-4bae-8eb8-72dd955bfc56",
        "name":"Learning GraphQL",
        "edition":3,
        "price":51.00,
        "store":{
            "id":"d38c10da-6be8-4924-b9b9-5e81899612a0"
        }
    }, 
    _2={
        "id":"1e93da94-af84-44f4-82d1-d8a9fd52ea94",
        "firstName":"Alex",
        "lastName":"Banks",
        "gender":"MALE"
    }
}
Tuple2{
    _1={
        "id":"64873631-5d82-4bae-8eb8-72dd955bfc56",
        "name":"Learning GraphQL",
        "edition":3,
        "price":51.00,
        "store":{"id":"d38c10da-6be8-4924-b9b9-5e81899612a0"}
    }, 
    _2={
        "id":"fd6bb6cf-336d-416c-8005-1ae11a6694b5",
        "firstName":"Eve",
        "lastName":"Procello",
        "gender":"MALE"
    }
}
```

:::note
The association object `Association<S, T>` is simple and special and does not need to support [object fetcher](./fetcher).

Note that this only refers to the `Association<S, T>` object <b> itself</b> does not support, its association property `source` and `target` still support [object fetcher](./fetcher), like this
```java
return q.select(
    association
        .source()
        // highlight-next-line
        .fetch(
            BookFetcher.$.store(
                BookStoreFetcher.$.allScalarFields()
            )
        ),
    association.target()
);
```
:::

## Compare with ordinary query

Readers may think that the value of queries based on middle table is to allow developers to write more performant queries.

But that's not the case. Due to the existence of the two optimization features [phantom join](../table-join#phantom-join) and [half join](../table-join#half-join), application can get ideal performance whether the query is based on an middle table or not. Whether or not to use a query based on an middle table is entirely up to the user's own preferences.

### 1. Implement a function based on middle table subquery

In the previous code, we demonstrated a top-level query based on an middle table; this example demonstrates a subquery based on an middle table.

```java
List<Book> books = sqlClient
    .createQuery(BookTable.class, (q, book) -> {
        q.where(
            book.id().in(
                // highlight-next-line
                q.createAssociationSubQuery(
                    BookTableEx.class,
                    BookTableEx::authors,
                    (sq, association) -> {
                        sq.where(
                            association.target() // α
                                .firstName().eq("Alex")
                        );
                        return sq.select(
                            association.source() // β
                                .id()
                        );
                    }
                )
            )
        );
        return q.select(book);
    }).execute();
books.forEach(System.out::println);
```

- `createAssociationSubQuery` is used to create a subquery based on an middle table. The query looks for all books that contain authors whose `firstName` is "Alex".

- `association.target()` at `α` is a real table join, which will generate a JOIN clause to the `AUTHOR` table in SQL.

- `association.source()` at `β` is a [phantom join](../table-join#phantom-join), JOIN clause will not be generated in SQL.

The generated SQL is as follows: 

```sql
select 
    tb_1_.ID, 
    tb_1_.NAME, 
    tb_1_.EDITION, 
    tb_1_.PRICE, 
    tb_1_.STORE_ID 
from BOOK as tb_1_ 
where 
    tb_1_.ID in (
        /* highlight-next-line */
        select 
            tb_2_.BOOK_ID 
        from BOOK_AUTHOR_MAPPING as tb_2_ 
        inner join AUTHOR as tb_3_ 
            on tb_2_.AUTHOR_ID = tb_3_.ID 
        where tb_3_.FIRST_NAME = ?
    )
```

### 2. Implement the same functionality based on ordinary subquery

```java
List<Book> books = sqlClient
        .createQuery(BookTable.class, (q, book) -> {
            q.where(
                book.id().in(
                    // highlight-next-line
                    q.createSubQuery(
                        AuthorTableEx.class,
                        (sq, author) -> {
                            sq.where(
                                author.firstName()
                                    .eq("Alex")
                            );
                            return sq.select(
                                author.books() // α
                                    .id()
                            );
                        }
                    )
                )
            );
            return q.select(book);
        }).execute();
books.forEach(System.out::println);
```

- `createSubQuery` is used to create a ordinary subquery without using middle table. achieve the exact same functionality.

- `author.books()` at `α` is a [half join](../table-join#half-join), so only need JOIN from `AUTHOR` table to middle table `BOOK_AUTHOR_MAPPING` is generated, without further JOIN to the `BOOK table`.

The generated SQL is as follows: 

```sql
select 

    tb_1_.ID, 
    tb_1_.NAME, 
    tb_1_.EDITION, 
    tb_1_.PRICE, 
    tb_1_.STORE_ID 

from BOOK as tb_1_ 
where 
    tb_1_.ID in (
        /* highlight-next-line */
        select 
            tb_3_.BOOK_ID 
        from AUTHOR as tb_2_ 
        inner join BOOK_AUTHOR_MAPPING as tb_3_ 
            on tb_2_.ID = tb_3_.AUTHOR_ID 
        where tb_2_.FIRST_NAME = ?
    )
```

Comparing these two SQL statements, it is not difficult to find that they have the same function and the same performance.

Therefore, whether or not to use queries based on middle tables has no impact on performance. Feel free to choose the style you like.