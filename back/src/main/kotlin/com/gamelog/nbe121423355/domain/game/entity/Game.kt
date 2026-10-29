package com.gamelog.nbe121423355.domain.game.entity

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.global.interop.javaIsBlank
import com.gamelog.nbe121423355.global.jpa.entity.BaseEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.math.BigDecimal.ZERO
import java.math.RoundingMode
import java.math.RoundingMode.HALF_UP
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZoneOffset.UTC

@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "games")
class Game protected constructor() : BaseEntity() {
    @jakarta.persistence.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @jakarta.persistence.Column(name = "igdb_id", unique = true)
    var igdbId: Long? = null
        protected set

    @jakarta.persistence.Column(nullable = false, length = 255)
    var title: String? = null
        protected set

    @jakarta.persistence.Column(name = "cover_image_url", length = 500)
    var coverImageUrl: String? = null
        protected set

    @jakarta.persistence.Column(columnDefinition = "TEXT")
    var developer: String? = null
        protected set

    @jakarta.persistence.Column(name = "release_date")
    var releaseDate: LocalDate? = null
        protected set

    @jakarta.persistence.Column(columnDefinition = "TEXT")
    var description: String? = null
        protected set

    // 번역 전 원문을 보존하고 변경된 원문을 재번역할 때 사용합니다.
    @jakarta.persistence.Column(columnDefinition = "TEXT")
    var descriptionSource: String? = null
        protected set

    @jakarta.persistence.Column(length = 64)
    var descriptionTranslatedHash: String? = null
        protected set

    fun prepareDescriptionTranslation(): String? {
        if (descriptionSource == null) descriptionSource = description
        return descriptionSource
    }

    fun applyDescriptionTranslation(source: String?, translated: String?, hash: String?) {
        kotlin.check(descriptionSource == source) { "번역 중 원문이 변경되었습니다." }
        kotlin.require(!(translated == null || translated.javaIsBlank())) { "번역 결과가 비어 있습니다." }
        description = translated
        descriptionTranslatedHash = hash
    }

    @jakarta.persistence.Column(name = "igdb_rating", precision = 5, scale = 2)
    var igdbRating: BigDecimal? = null
        protected set

    @jakarta.persistence.Column(name = "avg_rating", precision = 2, scale = 1)
    var avgRating: BigDecimal? = null
        protected set

    @jakarta.persistence.Column(name = "review_count")
    var reviewCount: Int? = null
        protected set

    @jakarta.persistence.Column(name = "like_count")
    var likeCount: Int? = null
        protected set

    @jakarta.persistence.Column(name = "played_count")
    var playedCount: Int? = null
        protected set

    @jakarta.persistence.Column(name = "playing_count")
    var playingCount: Int? = null
        protected set

    @jakarta.persistence.Column(name = "backlog_count")
    var backlogCount: Int? = null
        protected set

    @jakarta.persistence.Column(name = "wishlist_count")
    var wishlistCount: Int? = null
        protected set

    // 기존 게임의 IGDB 정보 갱신
    fun updateFromIgdb(response: IgdbGameResponse) {
        kotlin.require(response.id != null && response.id == igdbId) {
            "IGDB 게임 ID가 일치하지 않습니다."
        }
        kotlin.require(!(response.name == null || response.name.javaIsBlank())) { "게임 이름은 필수입니다." }
        this.title = response.name
        if (response.summary != null && !response.summary.javaIsBlank()) {
            this.descriptionSource = response.summary
            // 번역 실패 시에도 기존 한국어 설명을 유지합니다.
            if (descriptionTranslatedHash == null) this.description = response.summary
        }
        val coverUrl = response.cover?.url
        this.coverImageUrl =
            if (coverUrl != null && coverUrl.startsWith("//")) "https:" + coverUrl else coverUrl
        this.releaseDate =
            if (response.first_release_date == null) null
            else
                Instant.ofEpochSecond(response.first_release_date)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate()
        this.igdbRating =
            response.rating?.setScale(2, RoundingMode.HALF_UP)
        val developers = response.involved_companies.orEmpty().asSequence()
            .filter { it.developer == true }
            .mapNotNull { it.company?.name }
            .filterNot { it.javaIsBlank() }
            .distinct()
            .joinToString(", ")
        this.developer = if (developers.javaIsBlank()) null else developers
    }

    companion object {
        // IGDB 데이터로 새 게임 생성

        fun createFromIgdb(response: IgdbGameResponse): Game {
            val game = Game()
            game.igdbId = response.id
            // 우리 서비스에서 관리하는 값은 최초 생성 시 초기화
            game.avgRating = BigDecimal.ZERO
            game.reviewCount = 0
            game.likeCount = 0
            game.playedCount = 0
            game.playingCount = 0
            game.backlogCount = 0
            game.wishlistCount = 0
            game.updateFromIgdb(response)
            return game
        }
    }
}
