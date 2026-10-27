package com.gamelog.nbe121423355.domain.game.init

import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeMode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.boot.DefaultApplicationArguments

class RelatedGamePrecomputeOptionsTest {
    @Test
    fun parsesAllAndRequiresExplicitMode() {
        // given: 전체 계산과 최초 적재 모드를 명시한 실행 인자를 준비한다.
        val args = arrayOf("--all", "--mode=INITIAL_LOAD")

        // when: CLI 실행 옵션을 해석한다.
        val options = parse(*args)

        // then: 전체 대상과 빈 지정 ID 및 명시한 모드를 반환한다.
        assertThat(options.all).isTrue()
        assertThat(options.gameIds).isEmpty()
        assertThat(options.mode).isEqualTo(RelatedGamePrecomputeMode.INITIAL_LOAD)
    }

    @Test
    fun normalizesSpecifiedIds() {
        // given: 공백·중복·역순이 섞인 지정 ID와 갱신 모드를 준비한다.
        val args = arrayOf("--game-ids=30, 10,30,20", "--mode=REFRESH")

        // when: CLI 실행 옵션을 해석한다.
        val options = parse(*args)

        // then: 전체 실행을 끄고 ID를 중복 없이 정렬하여 갱신 모드로 반환한다.
        assertThat(options.all).isFalse()
        assertThat(options.gameIds).containsExactly(10L, 20L, 30L)
    }

    @ParameterizedTest
    @MethodSource("invalidArguments")
    fun rejectsInvalidTargetsBeforeDatabaseAccess(args: Array<String>) {
        // given: 대상·모드·ID 또는 지원 옵션 조건을 위반한 인자를 준비한다.
        val arguments = DefaultApplicationArguments(*args)

        // when: DB에 접근하기 전 CLI 실행 옵션을 해석한다.
        val failure = catchThrowable { RelatedGamePrecomputeOptions.parse(arguments) }

        // then: 모든 잘못된 실행 조합을 거절한다.
        assertThat(failure).isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun parse(vararg args: String): RelatedGamePrecomputeOptions =
        RelatedGamePrecomputeOptions.parse(DefaultApplicationArguments(*args))

    companion object {
        @JvmStatic
        fun invalidArguments(): List<Arguments> = listOf(
            arrayOf("--mode=REFRESH"),
            arrayOf("--all", "--game-ids=1", "--mode=REFRESH"),
            arrayOf("--all=false", "--mode=REFRESH"),
            arrayOf("--all"),
            arrayOf("--all", "--mode=invalid"),
            arrayOf("--all", "--mode=REFRESH", "--mode=INITIAL_LOAD"),
            arrayOf("--game-ids=", "--mode=REFRESH"),
            arrayOf("--game-ids=1,", "--mode=REFRESH"),
            arrayOf("--game-ids=-1", "--mode=REFRESH"),
            arrayOf("--game-ids=0", "--mode=REFRESH"),
            arrayOf("--game-ids=abc", "--mode=REFRESH"),
            arrayOf("--all", "--mode=REFRESH", "--unknown=true"),
        ).map { Arguments.of(it) }
    }
}
