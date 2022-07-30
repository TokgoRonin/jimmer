package org.babyfish.jimmer.sql.kt

import org.babyfish.jimmer.sql.SqlClient
import org.babyfish.jimmer.sql.cache.*
import org.babyfish.jimmer.sql.dialect.Dialect
import org.babyfish.jimmer.sql.kt.impl.KSqlClientImpl
import org.babyfish.jimmer.sql.kt.impl.toImmutableProp
import org.babyfish.jimmer.sql.meta.IdGenerator
import org.babyfish.jimmer.sql.runtime.*
import java.lang.IllegalStateException
import java.sql.Connection
import java.sql.PreparedStatement
import java.util.function.Function
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

class KSqlClientDsl internal constructor(
    private val javaBuilder: SqlClient.Builder
) {
    fun setDialect(dialect: Dialect) {
        javaBuilder.setDialect(dialect)
    }

    fun setDefaultBatchSize(size: Int) {
        javaBuilder.setDefaultBatchSize(size)
    }

    fun setDefaultListBatchSize(size: Int) {
        javaBuilder.setDefaultListBatchSize(size)
    }

    fun setConnectionManager(block: ConnectionManagerDsl.() -> Unit) {
        javaBuilder.setConnectionManager(ConnectionManagerImpl(block))
    }

    fun setExecutor(block: ExecutorDsl.() -> Unit) {
        javaBuilder.setExecutor(ExecutorImpl(block))
    }

    fun setIdGenerator(idGenerator: IdGenerator) {
        javaBuilder.setIdGenerator(idGenerator)
    }

    fun setIdGenerator(entityType: KClass<*>, idGenerator: IdGenerator) {
        javaBuilder.setIdGenerator(entityType.java, idGenerator)
    }

    fun addScalarProvider(scalarProvider: ScalarProvider<*, *>) {
        javaBuilder.addScalarProvider(scalarProvider)
    }

    fun setCaches(block: CacheDsl.() -> Unit) {
        javaBuilder.setCaches {
            CacheDsl(it).block()
        }
    }

    class ConnectionManagerDsl internal constructor(
        private val javaBlock: Function<Connection, *>
    ) {
        private var proceeded: Boolean = false

        private var result: Any? = null

        fun proceed(con: Connection) {
            if (proceeded) {
                throw IllegalStateException("ConnectionManagerDsl cannot be proceeded twice")
            }
            result = javaBlock.apply(con)
            proceeded = true
        }

        @Suppress("UNCHECKED_CAST")
        internal fun <R> get(): R {
            if (!proceeded) {
                throw IllegalStateException("ConnectionManagerDsl has not be proceeded")
            }
            return result as R
        }
    }

    private class ConnectionManagerImpl(
        private val dslBlock: ConnectionManagerDsl.() -> Unit
    ) : ConnectionManager {

        override fun <R> execute(block: Function<Connection, R>): R =
            ConnectionManagerDsl(block).let {
                it.dslBlock()
                it.get()
            }
    }

    class ExecutorDsl internal constructor(
        val con: Connection,
        val sql: String,
        val variables: List<Any>,
        private val javaBlock: SqlFunction<PreparedStatement, *>
    ) {
        private var proceeded: Boolean = false

        private var result: Any? = null

        fun proceed() {
            if (proceeded) {
                throw IllegalStateException("ExecutorDsl cannot be proceeded twice")
            }
            result = DefaultExecutor.INSTANCE.execute(con, sql, variables, javaBlock)
            proceeded = true
        }

        @Suppress("UNCHECKED_CAST")
        internal fun <R> get(): R {
            if (!proceeded) {
                throw IllegalStateException("ExecutorDsl has not be proceeded")
            }
            return result as R
        }
    }

    private class ExecutorImpl(
        private val dslBlock: ExecutorDsl.() -> Unit
    ) : Executor {
        override fun <R> execute(
            con: Connection,
            sql: String,
            variables: List<Any>,
            block: SqlFunction<PreparedStatement, R>
        ): R =
            ExecutorDsl(con, sql, variables, block).let {
                it.dslBlock()
                it.get()
            }
    }

    class CacheDsl internal constructor(
        private val javaCfg: CacheConfig
    ) {
        fun setCacheFactory(cacheFactory: CacheFactory) {
            javaCfg.setCacheFactory(cacheFactory)
        }

        fun <T: Any> setObjectCache(entityType: KClass<T>, cache: Cache<*, T>) {
            javaCfg.setObjectCache(entityType.java, cache)
        }

        fun setAssociatedIdCache(prop: KProperty1<*, *>, cache: Cache<*, *>) {
            javaCfg.setAssociatedIdCache(prop.toImmutableProp(), cache)
        }

        fun setAssociatedListIdCache(prop: KProperty1<*, *>, cache: Cache<*, List<*>>) {
            javaCfg.setAssociatedIdListCache(prop.toImmutableProp(), cache)
        }
    }

    internal fun buildKSqlClient(): KSqlClient =
        KSqlClientImpl(javaBuilder.build())
}