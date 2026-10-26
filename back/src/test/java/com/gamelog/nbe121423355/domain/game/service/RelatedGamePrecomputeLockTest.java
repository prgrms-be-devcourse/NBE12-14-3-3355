package com.gamelog.nbe121423355.domain.game.service;

import com.gamelog.nbe121423355.support.MySqlTestConfiguration;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import(MySqlTestConfiguration.class)
@ActiveProfiles("test")
class RelatedGamePrecomputeLockTest {

    @Autowired
    private RelatedGamePrecomputeLock lock;
    @Autowired
    private DataSource dataSource;

    @Test
    void separateDatabaseSessionCannotRunUntilCurrentExecutionFinishes() {
        // given: 독립된 MySQL 연결로 실행 잠금을 사용하는 서비스를 준비한다.

        // when: 첫 실행이 잠금을 보유한 동안 두 번째 실행을 시도한다.
        var first = lock.tryRun(check -> {
            check.invoke();
            assertThat(lock.<String>tryRun(other -> "second session")).isNull();
            return "first session";
        });
        var next = lock.tryRun(check -> "next execution");

        // then: 첫 실행이 끝난 뒤에만 다음 실행이 잠금을 얻는다.
        assertThat(first).isEqualTo("first session");
        assertThat(next).isEqualTo("next execution");
    }

    @Test
    void exceptionReleasesLockForNextExecution() {
        // given: 잠금을 얻은 작업에서 발생시킬 예외를 준비한다.
        var failure = new IllegalStateException("작업 중단");

        // when: 작업 중 예외가 발생하고 같은 잠금으로 재실행을 시도한다.
        var thrown = org.assertj.core.api.Assertions.catchThrowable(() -> lock.tryRun(check -> {
            throw failure;
        }));
        var retry = lock.tryRun(check -> "retry");

        // then: 원래 예외를 유지하고 잠금을 해제하여 재실행을 허용한다.
        assertThat(thrown).isSameAs(failure).hasMessage("작업 중단");
        assertThat(retry).isEqualTo("retry");
    }

    @Test
    void falseAndZeroResultsStillMeanLockWasAcquired() {
        // given: false와 0을 정상 결과로 반환하는 작업을 준비한다.

        // when: 잠금을 얻어 각 작업을 실행한다.
        var booleanResult = lock.tryRun(check -> false);
        var integerResult = lock.tryRun(check -> 0);

        // then: 잠금 획득 실패인 null과 false 및 0을 구분한다.
        assertThat(booleanResult).isNotNull().isFalse();
        assertThat(integerResult).isNotNull().isZero();
    }

    @Test
    void terminatedDatabaseSessionDoesNotLeavePermanentLock() throws Exception {
        // given: 별도 물리 연결에서 서비스와 같은 이름의 MySQL 잠금을 획득한다.
        HikariDataSource pool = dataSource.unwrap(HikariDataSource.class);
        // 별도 프로세스에 해당하는 독립 연결을 사용한다.
        try (Connection owner = DriverManager.getConnection(pool.getJdbcUrl(), pool.getUsername(), pool.getPassword())) {
            try (PreparedStatement statement = owner.prepareStatement("SELECT GET_LOCK(?, 0)")) {
                statement.setString(1, RelatedGamePrecomputeLock.lockName(owner.getCatalog()));
                try (ResultSet result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt(1)).isEqualTo(1);
                }
            }
            // when: 서비스 실행을 시도한 뒤 잠금 소유 연결을 실제로 종료한다.
            assertThat(lock.<String>tryRun(check -> "blocked")).isNull();
            // 프로세스 종료처럼 풀 반환 대신 실제 DB 연결을 종료한다.
            owner.abort(Runnable::run);
        }

        // then: 연결 종료 후 잠금이 해제되어 서비스 실행을 다시 허용한다.
        // 소켓 종료가 서버에서 처리되어 잠금이 해제될 때까지 제한 시간 안에서 확인한다.
        await().atMost(Duration.ofSeconds(3)).pollInterval(Duration.ofMillis(10))
                .untilAsserted(() -> assertThat(lock.<String>tryRun(check -> "recovered")).isEqualTo("recovered"));
    }
}
