package com.gamelog.nbe121423355.domain.game.init

import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeMode
import com.gamelog.nbe121423355.global.interop.javaIsBlank
import org.springframework.boot.ApplicationArguments

@JvmRecord
data class RelatedGamePrecomputeOptions(
    val all: Boolean,
    val gameIds: MutableList<Long>,
    val mode: RelatedGamePrecomputeMode,
) {

    companion object {
        @JvmStatic
        fun parse(arguments: ApplicationArguments): RelatedGamePrecomputeOptions {
            kotlin.require(
                !(!arguments.getNonOptionArgs().isEmpty() ||
                    !java.util.Set.of("all", "game-ids", "mode")
                        .containsAll(arguments.getOptionNames()))
            ) {
                "지원 옵션: --all 또는 --game-ids=1,2 및 --mode=INITIAL_LOAD|REFRESH"
            }
            val all = arguments.containsOption("all")
            val specified = arguments.containsOption("game-ids")
            kotlin.require(all != specified) { "--all과 --game-ids 중 하나만 지정하세요." }
            kotlin.require(!(all && !arguments.getOptionValues("all").orEmpty().isEmpty())) {
                "--all은 값 없이 지정하세요."
            }
            val mode: RelatedGamePrecomputeMode
            try {
                mode =
                    RelatedGamePrecomputeMode.valueOf(
                        RelatedGamePrecomputeOptions.singleValue(arguments, "mode")
                    )
            } catch (failure: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "--mode=INITIAL_LOAD 또는 --mode=REFRESH를 지정하세요.",
                    failure,
                )
            }
            var ids: MutableList<Long> = java.util.List.of()
            if (specified) {
                try {
                    ids =
                        RelatedGamePrecomputeOptions.singleValue(arguments, "game-ids")
                            .split(",")
                            .map { it.trim { c -> Character.isWhitespace(c) }.toLong() }
                            .distinct()
                            .sorted()
                            .toMutableList()
                } catch (failure: NumberFormatException) {
                    throw IllegalArgumentException("게임 ID는 쉼표로 구분한 양수여야 합니다.", failure)
                }
                kotlin.require(!ids.stream().anyMatch({ id -> id < 1 })) { "게임 ID는 양수여야 합니다." }
            }
            return RelatedGamePrecomputeOptions(all, ids, mode)
        }

        @JvmStatic
        private fun singleValue(arguments: ApplicationArguments, name: String): String {
            val values = arguments.getOptionValues(name)
            kotlin.require(!(values == null || values.size != 1 || values.first().javaIsBlank())) {
                "--" + name + " 값을 한 번 지정하세요."
            }
            return values.first()
        }
    }
}
