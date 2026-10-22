package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.global.interop.javaIsBlank
import com.zaxxer.hikari.HikariDataSource
import java.nio.charset.StandardCharsets
import java.nio.charset.StandardCharsets.UTF_8
import java.sql.Connection
import java.sql.SQLException
import java.util.Optional
import java.util.UUID
import java.util.function.Function
import javax.sql.DataSource
import org.springframework.stereotype.Component

@Component
class RelatedGamePrecomputeLock constructor(private val dataSource: DataSource) {
    // 트랜잭션용 연결과 분리해 빌린 세션을 작업이 끝날 때까지 유지한다.
    fun <T : Any> tryRun(action: Function<Runnable, T>): Optional<T> {
        try {
            dataSource.getConnection().use { connection ->
                val name = RelatedGamePrecomputeLock.lockName(connection.getCatalog())
                if (scalar(connection, "SELECT GET_LOCK(?, 0)", name) != 1L) {
                    return Optional.empty()
                }
                var workFailure: Throwable? = null
                try {
                    return Optional.of(action.apply({ verifyOwnership(connection, name) }))
                } catch (failure: RuntimeException) {
                    workFailure = failure
                    throw failure
                } catch (failure: Error) {
                    workFailure = failure
                    throw failure
                } finally {
                    try {
                        release(connection, name)
                    } catch (releaseFailure: SQLException) {
                        if (workFailure != null) {
                            workFailure.addSuppressed(releaseFailure)
                        } else {
                            throw releaseFailure
                        }
                    }
                }
            }
        } catch (failure: SQLException) {
            throw IllegalStateException("추천 계산 잠금 연결을 사용할 수 없습니다.", failure)
        }
    }

    private fun verifyOwnership(connection: Connection, name: String?) {
        try {
            kotlin.check(
                scalar(
                    connection,
                    "SELECT IS_USED_LOCK(?) = CONNECTION_ID()",
                    name,
                ) == 1L
            ) {
                "추천 계산 잠금 소유권을 잃었습니다."
            }
        } catch (failure: SQLException) {
            throw IllegalStateException("추천 계산 잠금 연결이 끊어졌습니다.", failure)
        }
    }

    @Throws(SQLException::class)
    private fun release(connection: Connection, name: String?) {
        try {
            scalar(connection, "SELECT RELEASE_LOCK(?)", name)
        } catch (failure: SQLException) {
            // 잠금을 가진 세션이 커넥션 풀로 돌아가지 않게 물리 연결을 폐기한다.
            try {
                if (dataSource.isWrapperFor(HikariDataSource::class.java)) {
                    dataSource.unwrap(HikariDataSource::class.java).evictConnection(connection)
                } else {
                    connection.abort(Runnable::run)
                }
            } catch (abortFailure: SQLException) {
                failure.addSuppressed(abortFailure)
            }
            throw failure
        }
    }

    @Throws(SQLException::class)
    private fun scalar(connection: Connection, sql: String?, name: String?): Long {
        connection.prepareStatement(sql).use { statement ->
            statement.setQueryTimeout(5)
            statement.setString(1, name)
            statement.executeQuery().use { result ->
                if (!result.next()) {
                    throw SQLException("잠금 조회 결과가 없습니다.")
                }
                val value = result.getLong(1)
                if (result.wasNull()) {
                    throw SQLException("잠금 조회에 실패했습니다.")
                }
                return value
            }
        }
    }

    companion object {
        @JvmStatic
        fun lockName(database: String?): String {
            kotlin.check(!(database == null || database.javaIsBlank())) {
                "추천 계산 대상 DB가 선택되지 않았습니다."
            }
            // MySQL 잠금 이름 길이 제한 안에서 DB별로 같은 이름을 만든다.
            return "related-precompute:" +
                UUID.nameUUIDFromBytes(database.toByteArray(StandardCharsets.UTF_8))
        }
    }
}
