package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.client.DeepLClient
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameChangeReason
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.global.interop.javaIsBlank
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.HexFormat
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Service
class GameTranslationService(
    private val repository: GameRepository,
    private val changeRecorder: RelatedGameChangeRecorder,
    private val client: DeepLClient,
    manager: PlatformTransactionManager,
    @Value("\${game.translation.enabled:false}") private val enabled: Boolean,
    @Value("\${game.translation.max-games:10}") private val maxGames: Int,
) {
    private val transaction = TransactionTemplate(manager)

    @Synchronized
    fun translatePending(): Int {
        if (!enabled) return 0
        require(maxGames >= 1) { "max-games는 1 이상이어야 합니다." }
        var translated = 0
        var page = 0
        while (translated < maxGames) {
            val games =
                repository.findAll(
                    PageRequest.of(
                        page,
                        100,
                        Sort.by("id"),
                    )
                )
            for (game in games) {
                if (translated >= maxGames) break
                // 원문 백업은 번역 요청 전에 커밋합니다. 외부 HTTP 호출에는 DB 트랜잭션을 유지하지 않습니다.
                val source =
                    transaction.execute {
                        repository
                            .findById(requireNotNull(game.id))
                            .orElseThrow()
                            .let { current ->
                                val source = current.prepareDescriptionTranslation()
                                changeRecorder.record(
                                    requireNotNull(current.id),
                                    RelatedGameChangeReason.GAME_INFORMATION,
                                )
                                source
                            }
                    }
                if (source == null || source.javaIsBlank()) continue
                val hash = hash(source)
                if (hash == game.descriptionTranslatedHash) continue
                val korean = try {
                    client.translate(source)
                } catch (exception: RuntimeException) {
                    // 응답 본문/키를 로그에 남기지 않습니다. 실패 시 기존 설명과 완료 건은 유지합니다.
                    log.warn(
                        "번역 중단: gameId={}, 원인={}. 키·한도·네트워크를 확인 후 재실행하세요.",
                        game.id,
                        exception.javaClass.simpleName,
                    )
                    return translated
                }
                transaction.executeWithoutResult {
                    repository
                        .findById(requireNotNull(game.id))
                        .orElseThrow()
                        .also { current ->
                            current.applyDescriptionTranslation(source, korean, hash)
                            changeRecorder.record(
                                requireNotNull(current.id),
                                RelatedGameChangeReason.GAME_INFORMATION,
                            )
                        }
                }
                translated++
                log.info(
                    "한국어 설명 저장 완료: gameId={}, 이번 실행 {}개",
                    game.id,
                    translated,
                )
            }
            if (!games.hasNext()) break
            page++
        }
        return translated
    }

    companion object {
        private val log = LoggerFactory.getLogger(GameTranslationService::class.java)

        @JvmStatic
        fun hash(source: String): String {
            try {
                return HexFormat.of()
                    .formatHex(
                        MessageDigest.getInstance("SHA-256")
                            .digest(source.toByteArray(StandardCharsets.UTF_8))
                    )
            } catch (exception: NoSuchAlgorithmException) {
                throw IllegalStateException(exception)
            }
        }
    }
}
