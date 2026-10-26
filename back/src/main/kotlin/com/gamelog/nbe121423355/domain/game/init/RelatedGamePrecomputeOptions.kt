package com.gamelog.nbe121423355.domain.game.init

import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeMode
import com.gamelog.nbe121423355.global.interop.javaIsBlank
import org.springframework.boot.ApplicationArguments

@JvmRecord
data class RelatedGamePrecomputeOptions(
    val all: Boolean,
    val gameIds: List<Long>,
    val mode: RelatedGamePrecomputeMode,
) {
    companion object {
        private val SUPPORTED_OPTIONS = setOf("all", "game-ids", "mode")

        @JvmStatic
        fun parse(arguments: ApplicationArguments): RelatedGamePrecomputeOptions {
            require(arguments.nonOptionArgs.isEmpty() && SUPPORTED_OPTIONS.containsAll(arguments.optionNames)) {
                "지원 옵션: --all 또는 --game-ids=1,2 및 --mode=INITIAL_LOAD|REFRESH"
            }
            val all = arguments.containsOption("all")
            val specified = arguments.containsOption("game-ids")
            require(all != specified) { "--all과 --game-ids 중 하나만 지정하세요." }
            require(!all || arguments.getOptionValues("all").isNullOrEmpty()) {
                "--all은 값 없이 지정하세요."
            }
            val mode = try {
                RelatedGamePrecomputeMode.valueOf(singleValue(arguments, "mode"))
            } catch (failure: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "--mode=INITIAL_LOAD 또는 --mode=REFRESH를 지정하세요.",
                    failure,
                )
            }
            val ids = if (specified) parseGameIds(singleValue(arguments, "game-ids")) else emptyList()
            return RelatedGamePrecomputeOptions(all = all, gameIds = ids, mode = mode)
        }

        // 중복 ID는 한 번만 실행하고 입력 순서와 관계없이 ID 오름차순으로 계산한다.
        private fun parseGameIds(value: String): List<Long> {
            val ids = try {
                value.split(",")
                    .map { it.trim { character -> Character.isWhitespace(character) }.toLong() }
                    .distinct()
                    .sorted()
            } catch (failure: NumberFormatException) {
                throw IllegalArgumentException("게임 ID는 쉼표로 구분한 양수여야 합니다.", failure)
            }
            require(ids.all { it > 0 }) { "게임 ID는 양수여야 합니다." }
            return ids
        }

        private fun singleValue(arguments: ApplicationArguments, name: String): String {
            val value = arguments.getOptionValues(name)?.singleOrNull()
            require(value != null && !value.javaIsBlank()) { "--$name 값을 한 번 지정하세요." }
            return value
        }
    }
}
