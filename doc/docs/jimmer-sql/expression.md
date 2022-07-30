---
sidebar_position: 6
title: Expression
---

## Literal

Let's look at an example first *(This query has no actual business meaning, just for demonstration)*
```java
List<
    Tuple5<
        String,
        Long,
        OffsetDateTime,
        String,
        Boolean
    >
> tuples = sqlClient
    .createQuery(BookTable.class, (q, store) -> {
        return q.select(
            Expression.string().value("String"),
            Expression.numeric().value(3L),
            Expression.comparable().value(OffsetDateTime.now()),
            Expression.any().value("String"),
            Expression.nullValue(Boolean.class)
        );
    }).execute();
tuples.forEach(System.out::println);
```

The generated SQL is as follows
```sql
select ?, ?, ?, ?, null from BOOK as tb_1_
```

Except for null, all other types of literals become JDBC parameters.

:::caution
In this demonstration, it can be seen that the `value()` method accepts many types of parameters.

It should be noted that no matter what the `value()` method parameter type is, it cannot be null, otherwise it will cause an exception.

To create a literal expression for null, you must use the `nullValue()` method, which requires specifying the expression type.
:::

:::note
This example uses the type bootstrap method:
1. Expression.string(), for string types.
2. Expression.numeric(), for numeric types.
3. Expression.comparble(), for comparable types, that is, types inherited from java.lang.Comparable.
4. Expression.any(), other types.

The type boostrap method is designed for the inadequacy of the java language and is not our current focus, see [type boot](#type-bootstrap) at the end of this article to learn more.
:::

In most cases, developers do not need to manually create literal expressions.

Take the equality judgment to be discussed below as an example, this is the relatively cumbersome way
```java
q.where(
    book.name().eq(
        Expression.string().value("Java in Action")
    )
);
```
It can be replaced by a more convenient way, like this
```java
q.where(book.name().eq("Java in Action"));
```

Other APIs provide overloaded methods to avoid having developers build literal expressions themselves.

However, this cannot be done 100%. In rare cases, developers are still required to build literal expressions themselves.

## Constant

Constant expressions are highly similar to literal expressions, let's look at the example first *(this query has no actual business meaning, just for demonstration)*
```java
List<Integer> constants = sqlClient
    .createQuery(BookTable.class, (q, store) -> {
        return q.select(
            // highlight-next-line
            Expression.constant(1)
        );
    }).execute();
constants.forEach(System.out::println);
```

The generated SQL is as follows

```sql
select 
    /* highlight-next-line */
    1 
from BOOK as tb_1_
```

It's not hard to see that, unlike literal expressions that always use JDBC parameters, constant expressions directly hardcode the value into the SQL statement.

In order to eliminate the problem of injection attacks, constant expressions only support numeric types, which is a hard limit.

:::info
Although constant expressions only support numeric types so that there is no need to worry about injection attacks, please don't abuse it.

The only reason why constant expressions exist: Some databases support functional index, if there is a numeric constant inside the SQL expression that defines the function index, in order to match such a function index, constant expression can be very useful.

If your project doesn't have this scenario, never use constant expressions, you should always use literal expressions.

Using constant expressions incorrectly can have serious consequences. Implanting the fickle numbers as constant expressions into SQL will destroy the stability of the SQL string, and eventually lead to a very low cache hit rate of the internal execution plan of the database, affecting performance.
:::

## Comparison

1. Equal to
```java
q.where(book.name().eq("SQL in Action"));
```

2. Not equal to
```java
q.where(book.name().ne("SQL in Action"));
```

3. Greater than
```java
q.where(book.price().gt(new BigDecimal(50)));
```

4. Greater than or equal to
```java
q.where(book.price().ge(new BigDecimal(50)));
```

5. Less than
```java
q.where(book.price().lt(new BigDecimal(50)));
```

6. Less than or equal to
```java
q.where(book.price().le(new BigDecimal(50)));
```

7. Between
```java
q.where(
    book.price().between(
        new BigDecimal(40), 
        new BigDecimal(40)
    )
);
```

## Like

### Tet case

1. like: Case sensitive
    ```java
    q.where(book.name().like("Ab"));
    ```
    The final generated SQL condition
    ```sql
    where tb_1_.NAME like ?
    ```
    Corresponding JDBC parameters: `%Ab%`

2. ilike: Case insensitive
    ```java
    q.where(book.name().ilike("Ab"));
    ```
    The final generated SQL condition
    ```sql
    where lower(tb_1_.NAME) like ?
    ```
    Corresponding JDBC parameters: `%ab%`

### Like mode

1. `LikeMode.ANYWHERE`(Default behavior when not specified): appear anywhere
    ```java
    q.where(book.name().like("Ab", LikeMode.ANYWHERE));
    ```
    or
    ```java
    q.where(book.name().like("Ab"));
    ```
    Corresponding JDBC parameters: `'%Ab%'`

2. `LikeMode.START`: as a start
    ```java
    q.where(book.name().like("Ab", LikeMode.START));
    ```
    Corresponding JDBC parameters: `'Ab%'`

3. `LikeMode.END`: as an end
    ```java
    q.where(book.name().like("Ab", LikeMode.END));
    ```
    Corresponding JDBC parameters: `'%Ab'`

4. `LikeMode.EXACT`: exact match
    ```java
    q.where(book.name().like("Ab", LikeMode.EXACT));
    ```
    Corresponding JDBC parameters: `'Ab'`

## Nullity

```java
q.where(book.store().isNull());
```

```java
q.where(book.store().isNotNull());
```

## IN LIST

### Use single column

```java
q.where(
    book.name().in(
        "SQL in Action",
        "Java in Action"
    )
);
```
Generated SQL condition
```sql
where tb_1_.NAME in (?, ?)
```

### Use column

Using the tuple requires the static method `org.babyfish.jimmer.sql.ast.Expression.tuple`.

```java
q.where(
    Expression.tuple(
        book.name(),
        book.edition()
    ).in(
        Arrays.asList(
            new Tuple2<>("SQL in Action", 1),
            new Tuple2<>("SQL in Action", 2),
            new Tuple2<>("Java in Action", 1),
            new Tuple2<>("Java in Action", 2)
        )
    )
);
```

Generated SQL condition
```sql
where (tb_1_.NAME, tb_1_.EDITION) in (
    (?, ?), (?, ?), (?, ?), (?, ?)
)
```

:::note
In addition to being used with collections, in can also be used with subqueries.

This part of the content will be introduced in detail in the related documents of [subquery](./query/sub-query), and this article will not repeat the introduction.
:::

## And, Or, Not

### And
```java
q.where(
    Predicate.and(
        book.name().like("Ab"),
        book.price().ge(new BigDecimal(40)),
        book.price().le(new BigDecimal(60))
    )
);
```
:::note
Note that if the logical AND expression is directly used as the parameter of the `where` method, the following two equivalent ways are more recommended.
:::

1. Using the variadic version of the `where` method
    ```java
    q.where(
        book.name().like("Ab"),
        book.price().ge(new BigDecimal(40)),
        book.price().le(new BigDecimal(60))
    );
    ```

1. Multiple calls to `where` method
    ```java
    q
        .where(book.name().like("Ab"))
        .where(book.price().ge(new BigDecimal(40)))
        .where(book.price().le(new BigDecimal(60)));
    ```

Therefore, the direct use of `and` should not be common in actual projects.

### Or

```java
q.where(
    Predicate.or(
        book.name().like("Ab"),
        book.price().ge(new BigDecimal(40)),
        book.price().le(new BigDecimal(60))
    );
);
```

### Not

```java
q.where(
    book.name().like("Ab").not()
);
```

It is not always necessary to call the `not()` function. Many times there are shortcuts available, such as
1. `.eq(value).not()` can be abbreviated as `.ne(value)`
2. `.isNull().not()` can be abbreviated as `.isNotNull(value)`
3. `.exists().not()` can be abbreviated as `.notExists()` *(exists will be introduced in [sub-query](./query/sub-query), but will not be introduced in this article)*

Even if the developer explicitly uses `not()`, the `not` expression does not necessarily appear in the final SQL, such as
```java
q.where(
    book.price().ge(new BigDecimal(40))
        .not()
);
```
The actual generated SQL is
```sql
where tb_1_1.PRICE < ?
```
jimmer-sql tries to avoid using `not` directly in SQL, but anyway, in the end the SQL logic is equivalent to what you want.

## Computation

1. +
```java
q.select(book.price.plus(BigDecimal.TWO));
```
2. -
```java
q.select(book.price.minus(BigDecimal.TWO));
```
3. *
```java
q.select(book.price.times(BigDecimal.TWO));
```

4. /
```java
q.select(book.price.div(BigDecimal.TWO));
```

5. %
```java
q.select(book.price.rem(BigDecimal.TWO));
```

## Aggregate

```java
List<
    Tuple6<
        Long, 
        Long, 
        BigDecimal, 
        BigDecimal, 
        BigDecimal, 
        BigDecimal
    >
> tuples = sqlClient
    .createQuery(BookTable.class, (q, book) -> {
        return q.select(
                book.count(),
                book.id().count(/* disintct */ true),
                book.price().sum(),
                book.price().min(),
                book.price().max(),
                book.price().avg()
        );
    }).execute();
tuples.forEach(System.out::println);
```

The generated SQL is as follows

```sql
select 
    count(tb_1_.ID), 
    count(distinct tb_1_.ID), 
    sum(tb_1_.PRICE), 
    min(tb_1_.PRICE), 
    max(tb_1_.PRICE), 
    avg(tb_1_.PRICE) 
from BOOK as tb_1_
```

## Coalesce

The `coalesce` expression returns the first non-null value in a sequence of expressions.

```java
List<String> results = sqlClient
    .createQuery(BookStoreTable.class, (q, store) -> {
        return q.select(
            store.website() // 1
                // highlight-next-line
                .coalesceBuilder()
                .or(store.name()) // 2
                .or("Default Value") // 3
                .build()
        );
    }).execute();
results.forEach(System.out::println);
```

The generated SQL is as follows

```sql
select 
    /* highlight-next-line */
    coalesce(
        tb_1_.WEBSITE, 
        tb_1_.NAME, 
        ?
    ) 
from BOOK_STORE as tb_1_
```

In particular, if the SQL `coalesce` function has only two parameters, that is, the `or()` method will only be called once, there is a shortcut:

```java
List<String> results = sqlClient
    .createQuery(BookStoreTable.class, (q, store) -> {
        return q.select(
            store.website().coalesce(store.name())
        );
    }).execute();
results.forEach(System.out::println);
```

The generated SQL is as follows

```sql
select 
    /* highlight-next-line */
    coalesce(tb_1_.WEBSITE, tb_1_.NAME) 
from BOOK_STORE as tb_1_
```

In fact, this way is the most common.

## Concat

Contact expressions are used for string concatenation.

This example uses a space as separator to concat the author's firstName and lastName

```java
List<String> fullNames = sqlClient
    .createQuery(AuthorTable.class, (q, author) -> {
        return q.select(
            author.firstName()
                .concat(
                        Expression.string().value(" "),
                        author.lastName()
                )
        );
    }).execute();
fullNames.forEach(System.out::println);
```

The generated SQL is as follows

```sql
select 
    concat(
        tb_1_.FIRST_NAME, 
        ?, 
        tb_1_.LAST_NAME
    ) 
from AUTHOR as tb_1_
```

## Case

There are two types of case expressions, simple case and ordinary case

### Simple case expression

A simple case expression needs to specify an expression at the beginning, and each subsequent judgment branch specifies an expected value to check whether a target expression of one branch matches an expected value.

```java
List<String> results = sqlClient
    .createQuery(BookTable.class, (q, book) -> {
        return q.select(
            Expression.string()
                // highlight-next-line
                .caseBuilder(book.name())
                .when("Java in Action", "matched")
                .when("SQL in Action", "matched")
                .otherwise("not matched")
        );
    }).execute();
results.forEach(System.out::println);
```

The generated SQL is as follows

```sql
select 
    /* highlight-next-line */
    case tb_1_.NAME 
        when ? then ? 
        when ? then ? 
        else ? 
    end 
from BOOK as tb_1_
```

### Ordinary case expression

Ordinary case expressions do not need to specify any parameters at the beginning, but each subsequent judgment branch can specify a logical judgment expression of arbitrary complexity to check whether the branch is matched.

```java
List<String> results = sqlClient
    .createQuery(BookTable.class, (q, book) -> {
        return q.select(
            Expression.string()
                // highlight-next-line
                .caseBuilder()
                .when(
                    book.price().lt(new BigDecimal(30)),
                    "Cheap"
                )
                .when(
                    book.price().gt(new BigDecimal(70)),
                    "Expensive"
                )
                .otherwise("Appropriate")
        );
    }).execute();
results.forEach(System.out::println);
```

The generated SQL is as follows
```sql
select 
    /* highlight-next-line */
    case 
        when tb_1_.PRICE < ? then ? 
        when tb_1_.PRICE > ? then ? 
        else ? 
    end 
from BOOK as tb_1_
```

## Native SQL expression

NativeSQL expression is an important function. Database products always have unique capabilities, and it is necessary to bring out the unique capabilities of data products.

This example demonstrates how to use regular match expression of Oracle and HSQLDB.

```java
List<Author> authors = sqlClient.
    .createQuery(AuthorTable.class, (q, author) -> {
        q.where(
            // highlight-next-line
            Predicate.sql(
                "regexp_like(%e, %v)",
                it -> it
                        .expression(author.firstName())
                        .value("^Ste(v|ph)en$")
            )
        );
    })
    .execute();
authors.forEach(System.out::println);
```

Here, we call `Predicate.sql` to create query conditions based on native SQL. In fact, other expression types also support NativeSQL expressions, there are 5 `sql` functions in total

1. Predicate.sql(...)
2. Expression.string().sql(...)
3. Expression.numeric().sql(...)
4. Expression.comparable().sql(...)
5. Expression.any().sql(...)

The first parameter of the `sql(...)` method is the SQL string template, which can contain the special symbols "%e" and "%v".

- %e: an expression
- %v: a value

The second parameter of the `sql(...)` method is optional and is a lambda expression. The parameter of the lambda expression is an object that supports two methods:
- `expression(Expresion<?>)`: implants an expression that matches "%e" of the SQL template.
- `value(Object)`: implants a value that matches "%v" of the SQL template.

The generated SQL is as follows

```sql
select 
    tb_1_.ID, 
    tb_1_.FIRST_NAME, 
    tb_1_.LAST_NAME, 
    tb_1_.GENDER 
from AUTHOR as tb_1_ 
where
    /* highlight-next-line */
    regexp_like(tb_1_.FIRST_NAME, ?)
```

## Type bootstrap

The above example occurs multiple times with these method calls

1. Expression.string(), for string types.
2. Expression.numeric(), for numeric types.
3. Expression.comparable(), for comparable types, that is, types inherited from java.lang.Comparable.
4. Expression.any(), other types.

These methods are called type bootstrap methods. Due to the defects of java itself, it is necessary to help the DSL to perform better type inference at the cost of slightly affecting the development experience.

:::info
This section explains the difficulties encountered in SQL DSL design, and the reasons for these type bootstrap methods, independent of the functionality that jimmer-sql wants to expose. Readers who are not interested in this can choose to ignore it.
:::

Strongly typed SQL DSL needs to define an important basic type Expression to represent SQL expression of any type.

```java
public interface Expression<T> {
    ...Public behaviors of any type, slightly...
}
```
It is clear
- For `Expression<String>`, we expect to support like, ilike, etc.
- For `Expresion<? extends Number>`, we expect to support plus, minus, etc.

That is, the DSL expects that the behavior exposed by the `Expression<T>` type can vary depending on the generic parameter.

This is a very classic question, supported by many languages, and generally speaking, there are two schools of thought

1. The genre represented by C++ destroys the unity of types

    ```cpp
    template <typename T> class Expression {
    public:
        ...Common behaviors...
    };

    template <> class Epxression<std::string> {
    public:
        ...Common behaviors...
        Predicate like(const char* pattern) const;
        Predicate ilike(const char* pattern) const;
    }

    template <> class Epxression<int> {
    public:
        ...Common behaviors...
        Expression<int> operator +(const Expression<int> &right) const;
        Expression<int> operator -(const Expression<int> &right) const;
    }
    ```

    This feature of C++ is called template specialization. Although it achieves the design goal, different generic parameters will lead to different type definitions and destroy the unity of the type system, so it is unacceptable for a platform such as the JVM that supports reflection based on unified type system. I used C++ as an example just to show that this is actually an ancient requirement.

2. The genre represented by kotlin maintains the unity of the type

    In order not to break the already defined `Expression<T>` type, kotlin can use extension functions for the same purpose.

    ```kotlin
    infix fun Expression<String>.like(String pattern): Predicate { ... }
    infix fun Expression<String>.ilike(String pattern): Predicate { ... }

    operator fun <N: Number> Expression<N>.plus(
        Expression<N> right
    ): Expression<N> { ... }
    operator fun <N: Number> Expression<N>.minus(
        Expression<N> right
    ): Expression<N> { ... }
    ```

Obviously, such a capability is not available in current java. So, take the next step and use it in conjunction with inheritance

```java
public interface Expression<T> {
    ...Common behaviors...
}

public interface NumericExpression<
    N extends Number
> extends Expression<N> {

    NumericExpression<N> plus(
        Expression<N> right
    );
    NumericExpression<N> minus(
        Expression<N> right
    );

    ...
}

public interface ComparableExpression<
    T extends Compariable<T>
> extends Expression<N> {

    ...
}

public interface StringExpression 
extends ComparableExpression<String> {

    Predicate like(String pattern);
    Predicate ilike(String pattern);

    ...
}
```

Since the expression type is split from an `Expression<T>` into `Expression<T>`, `StringExpression`, `NumericExpression<N>` and `Comparable<T>`, the 4 type bootstrap methods appeared.

```java
interface Expression {

    static StringFactory string() {...}

    static NumericFactory numeric() {...}

    static ComparableFactory comparable() {...}

    static AnyFactory any() {...}

    interface StringFactory {

        StringExpression value(String value);

        ...
    }

    interface NumericFactory {

        <N extends Number> NumericExpression<N> value(N value);

        ...
    }

    interface ComparableFactory {

        <T extends Comparable<T>> ComparableExpression<T> value(T value);

        ...
    }

    interface AnyFactory {

        <T> Expression<T> value(T value);

        ...
    }
}
```

The construction API of literal expressions is listed here. Users can guide the type so that different types of literal expressions can perform different subsequent operations.

In fact, when designing Java DSL, in order to solve this problem, there is another way: instead of designing `like`, `+`, `-` these operations as member methods of `Expression<T>`, they are designed as a methods of the global object, even some static methods.

JPA Criteria is designed such a global object, the code snippet is as follows:

```java
package javax.persistence.criteria;

public interface CriteriaBuilder {

    // For string expressions...
    Predicate like(Expression<String> x, String pattern);

    // For numeric expressions...
    <N extends Number> Expression<N> sum(
        Expression<? extends N> x, 
        Expression<? extends N> y
    );

    ....
}
```

This design circumvents this defect of the java language, but the user must obtain this global object when writing any expression (if it is a static method design, static import is required). Then use the methods of this global object to create all expression objects. for example:

`cb.like(book.get(Book_.name), "a")`, where `cb` is that global object.

Obviously, the method of the object itself is more natural and more convenient:

`book.name().like("a")`

This more natural and convenient way is used in most cases, and type bootstrap method is not used frequently in development. Therefore, in order to provide a convenient design as much as possible under the limited expressive ability of java, after careful consideration and choice, the expression system of jimmer-sql is designed as you see it now.