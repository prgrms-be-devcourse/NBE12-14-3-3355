package com.gamelog.nbe121423355.domain.usergame.support

import java.sql.SQLException
import org.assertj.core.api.Assertions.assertThat
import org.springframework.dao.DataIntegrityViolationException

internal fun Throwable.assertMySqlDuplicateEntry() {
    assertThat(this).isInstanceOf(DataIntegrityViolationException::class.java)
    val rootCause = generateSequence(this) { it.cause }.last()
    assertThat(rootCause).isInstanceOfSatisfying(SQLException::class.java) { exception ->
        assertThat(exception.errorCode).isEqualTo(1062)
        assertThat(exception.sqlState).isEqualTo("23000")
    }
}
