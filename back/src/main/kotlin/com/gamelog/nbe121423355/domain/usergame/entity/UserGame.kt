package com.gamelog.nbe121423355.domain.usergame.entity

import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.Platform
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.global.jpa.entity.BaseEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(
    name = "user_games",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "game_id"])],
)
class UserGame() : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    lateinit var user: User
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    lateinit var game: Game
        protected set

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "play_status")
    var playStatus: PlayStatus? = null
        protected set

    @Column(name = "is_playing", nullable = false)
    var playing: Boolean = false
        protected set

    @Column(name = "is_backlog", nullable = false)
    var backlog: Boolean = false
        protected set

    @Column(name = "is_wishlist", nullable = false)
    var wishlist: Boolean = false
        protected set

    @Column(name = "is_liked", nullable = false)
    var liked: Boolean = false
        protected set

    // 게임의 지원 플랫폼 제약은 통합 SQL의 복합 FK로 보장한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_id")
    var platform: Platform? = null
        protected set

    @Column(name = "play_time_hours", precision = 8, scale = 1)
    var playTimeHours: BigDecimal? = null
        protected set

    @Column(name = "finish_time_hours", precision = 8, scale = 1)
    var finishTimeHours: BigDecimal? = null
        protected set

    @Column(name = "master_time_hours", precision = 8, scale = 1)
    var masterTimeHours: BigDecimal? = null
        protected set

    @Column(name = "started_at")
    var startedAt: LocalDate? = null
        protected set

    @Column(name = "completed_at")
    var completedAt: LocalDate? = null
        protected set

    @Column(name = "last_played_at")
    var lastPlayedAt: LocalDateTime? = null
        protected set

    @Column(name = "is_in_library", nullable = false)
    var inLibrary: Boolean = false
        protected set

    val isPlaying: Boolean
        get() = playing

    val isBacklog: Boolean
        get() = backlog

    val isWishlist: Boolean
        get() = wishlist

    val isLiked: Boolean
        get() = liked

    val isInLibrary: Boolean
        get() = inLibrary

    constructor(user: User, game: Game) : this() {
        this.user = user
        this.game = game
        updateLibraryStatus()
    }

    constructor(
        user: User,
        game: Game,
        playStatus: PlayStatus?,
        playing: Boolean,
        backlog: Boolean,
        wishlist: Boolean,
        liked: Boolean,
        platform: Platform?,
        playTimeHours: BigDecimal?,
        finishTimeHours: BigDecimal?,
        masterTimeHours: BigDecimal?,
        startedAt: LocalDate?,
        completedAt: LocalDate?,
        lastPlayedAt: LocalDateTime?,
    ) : this() {
        this.user = user
        this.game = game
        updatePlayRecord(
            playStatus,
            playing,
            backlog,
            wishlist,
            liked,
            platform,
            playTimeHours,
            finishTimeHours,
            masterTimeHours,
            startedAt,
            completedAt,
            lastPlayedAt,
        )
    }

    fun updatePlayRecord(
        playStatus: PlayStatus?,
        playing: Boolean,
        backlog: Boolean,
        wishlist: Boolean,
        liked: Boolean,
        platform: Platform?,
        playTimeHours: BigDecimal?,
        finishTimeHours: BigDecimal?,
        masterTimeHours: BigDecimal?,
        startedAt: LocalDate?,
        completedAt: LocalDate?,
        lastPlayedAt: LocalDateTime?,
    ) {
        this.playStatus = playStatus
        this.playing = playing
        this.backlog = backlog
        this.wishlist = wishlist
        this.liked = liked
        this.platform = platform
        this.playTimeHours = playTimeHours
        this.finishTimeHours = finishTimeHours
        this.masterTimeHours = masterTimeHours
        this.startedAt = startedAt
        this.completedAt = completedAt
        this.lastPlayedAt = lastPlayedAt
        updateLibraryStatus()
    }

    fun updateLibraryStatus(hasActiveReview: Boolean = false) {
        inLibrary =
            hasActiveReview ||
                playStatus != null ||
                playing ||
                backlog ||
                wishlist ||
                platform != null ||
                hasPositiveHours(playTimeHours) ||
                hasPositiveHours(finishTimeHours) ||
                hasPositiveHours(masterTimeHours) ||
                startedAt != null ||
                completedAt != null ||
                lastPlayedAt != null
        // liked 제외
    }

    private fun hasPositiveHours(hours: BigDecimal?): Boolean =
        hours != null && hours.signum() > 0

    fun changePlayStatus(playStatus: PlayStatus?) {
        this.playStatus = playStatus
        updateLibraryStatus()
    }

    fun clearPlayStatus() = changePlayStatus(null)

    fun changePlaying(playing: Boolean) {
        this.playing = playing
        updateLibraryStatus()
    }

    fun changeBacklog(backlog: Boolean) {
        this.backlog = backlog
        updateLibraryStatus()
    }

    fun changeWishlist(wishlist: Boolean) {
        this.wishlist = wishlist
        updateLibraryStatus()
    }

    fun changeLiked(liked: Boolean) {
        this.liked = liked
        updateLibraryStatus()
    }

}
