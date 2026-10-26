package com.gamelog.nbe121423355.domain.game.service

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.concurrent.Executor
import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class RelatedGamePrecomputeLockFailureTest {
    @Test
    fun `작업과 잠금 해제가 모두 실패하면 원래 예외에 해제 실패를 보존한다`() {
        // given: 잠금 획득은 성공하고 해제는 실패하는 연결과 작업 예외를 준비한다.
        val fixture = releaseFailureFixture()
        val workFailure = IllegalStateException("계산 실패")

        // when: 잠금 안에서 실행한 작업이 실패한다.
        val thrown = catchThrowable {
            fixture.lock.tryRun<Int> { throw workFailure }
        }

        // then: 원래 예외와 해제 실패를 함께 보존하고 잠금을 보유한 연결을 폐기한다.
        assertThat(thrown).isSameAs(workFailure)
        assertThat(thrown.suppressed).containsExactly(fixture.releaseFailure)
        verify(fixture.connection).abort(any(Executor::class.java))
        verify(fixture.connection).close()
    }

    @Test
    fun `작업이 성공해도 잠금 해제에 실패하면 실행 실패를 알린다`() {
        // given: 작업 후 잠금 해제에 실패하는 연결을 준비한다.
        val fixture = releaseFailureFixture()

        // when: 작업 결과를 반환한 뒤 잠금 해제를 시도한다.
        val thrown = catchThrowable { fixture.lock.tryRun { 1 } }

        // then: 해제 실패를 숨기지 않고 연결을 폐기하여 다음 실행에 잠금을 남기지 않는다.
        assertThat(thrown).isInstanceOf(IllegalStateException::class.java)
            .hasCause(fixture.releaseFailure)
        verify(fixture.connection).abort(any(Executor::class.java))
        verify(fixture.connection).close()
    }

    private fun releaseFailureFixture(): Fixture {
        val dataSource = mock(DataSource::class.java)
        val connection = mock(Connection::class.java)
        val statement = mock(PreparedStatement::class.java)
        val result = mock(ResultSet::class.java)
        val releaseFailure = SQLException("잠금 해제 실패")
        `when`(dataSource.connection).thenReturn(connection)
        `when`(connection.catalog).thenReturn("lock_failure_test")
        `when`(connection.prepareStatement("SELECT GET_LOCK(?, 0)")).thenReturn(statement)
        `when`(statement.executeQuery()).thenReturn(result)
        `when`(result.next()).thenReturn(true)
        `when`(result.getLong(1)).thenReturn(1L)
        `when`(connection.prepareStatement("SELECT RELEASE_LOCK(?)")).thenThrow(releaseFailure)
        return Fixture(RelatedGamePrecomputeLock(dataSource), connection, releaseFailure)
    }

    private data class Fixture(
        val lock: RelatedGamePrecomputeLock,
        val connection: Connection,
        val releaseFailure: SQLException,
    )
}
