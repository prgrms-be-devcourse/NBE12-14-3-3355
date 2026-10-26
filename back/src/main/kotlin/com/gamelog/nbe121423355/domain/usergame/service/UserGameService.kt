package com.gamelog.nbe121423355.domain.usergame.service

import com.gamelog.nbe121423355.domain.game.entity.RelatedGameChangeReason
import com.gamelog.nbe121423355.domain.game.service.RelatedGameChangeRecorder
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.Platform
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.game.repository.PlatformRepository
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.review.repository.ReviewRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.entity.UserFavoriteGame
import com.gamelog.nbe121423355.domain.user.repository.UserFavoriteGameRepository
import com.gamelog.nbe121423355.domain.user.repository.UserFollowRepository
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.domain.usergame.dto.*
import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.domain.usergame.repository.UserGameRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.global.interop.javaIsBlank
import com.gamelog.nbe121423355.global.interop.strip
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation.MANDATORY
import org.springframework.transaction.annotation.Transactional

@Service
class UserGameService(
    private val changeRecorder: RelatedGameChangeRecorder,
    private val userGameRepository: UserGameRepository,
    private val userRepository: UserRepository,
    private val gameRepository: GameRepository,
    private val platformRepository: PlatformRepository,
    private val reviewRepository: ReviewRepository,
    private val userFollowRepository: UserFollowRepository,
    private val userFavoriteGameRepository: UserFavoriteGameRepository,
) {

    @Transactional(readOnly = true)
    fun getUserGameList(
        userId: Long,
        request: UserGameSearchRequest,
    ): Page<UserGameListResponse> {
        log.debug(
            "라이브러리 게임 목록 조회 - userId={}, page={}, size={}, keyword={}, status={}, sort={}",
            userId,
            request.page,
            request.size,
            request.keyword,
            request.status,
            request.sort,
        )
        val keyword: String? = request.keyword
        val pattern =
            if (keyword == null || keyword.javaIsBlank()) null
            else
                "%" + keyword.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"
        val platforms = !request.platformIds.isNullOrEmpty()
        val genres = !request.genreIds.isNullOrEmpty()
        return userGameRepository
            .findByLibraryFilters(
                userId,
                requireNotNull(request.status).name,
                pattern,
                platforms,
                if (platforms) requireNotNull(request.platformIds) else listOf(0L),
                genres,
                if (genres) requireNotNull(request.genreIds) else listOf(0L),
                requireNotNull(request.sort).name,
                PageRequest.of(request.page, request.size),
            )
            .map(::UserGameListResponse)
    }

    @Transactional(readOnly = true)
    fun getPublicUserGameList(
        userId: Long,
        request: UserGameSearchRequest,
    ): Page<UserGameListResponse> {
        log.debug(
            "공개 프로필 게임 목록 조회 - targetUserId={}, page={}, size={}",
            userId,
            request.page,
            request.size,
        )
        if (!userRepository.existsByIdAndDeletedAtIsNull(userId)) {
            throw ServiceException("404-1", "존재하지 않는 유저입니다.")
        }
        return getUserGameList(userId, request)
    }

    // 생성 또는 수정
    @Transactional
    fun addOrUpdateGameToLibrary(
        userId: Long,
        gameId: Long,
        reqBody: UserGameReqBody,
    ): UserGameSaveResult {
        val result = saveGameRecord(userId, gameId, reqBody)
        synchronizeLibraryState(result.userGame)
        return result
    }

    // 상세 저장에서는 리뷰까지 저장한 뒤 최종 라이브러리/인생 게임 상태를 판단한다.
    @Transactional(propagation = MANDATORY)
    fun prepareGameRecordForReview(
        userId: Long,
        gameId: Long,
        reqBody: UserGameReqBody,
    ): UserGameSaveResult {
        return saveGameRecord(userId, gameId, reqBody).also {
            changeRecorder.record(gameId, RelatedGameChangeReason.USER_GAME_RECORD)
        }
    }

    private fun saveGameRecord(
        userId: Long,
        gameId: Long,
        reqBody: UserGameReqBody,
    ): UserGameSaveResult {
        log.info("게임 기록 저장 요청 - userId={}, gameId={}", userId, gameId)
        validatePlayDates(reqBody)
        val platform = findPlatform(reqBody.platformId)
        return userGameRepository
            .findByUser_IdAndGame_Id(userId, gameId)
            ?.let { userGame ->
                applyPlayRecord(userGame, reqBody, platform)
                log.info("게임 기록 수정 완료 - userId={}, gameId={}", userId, gameId)
                UserGameSaveResult(userGame, false)
            }
            ?: run {
                val userGame = createUserGame(userId, gameId, reqBody, platform)
                log.info("게임 라이브러리 등록 완료 - userId={}, gameId={}", userId, gameId)
                UserGameSaveResult(userGame, true)
            }
    }

    @Transactional
    fun updatePlayRecord(userId: Long, gameId: Long, reqBody: UserGameReqBody): UserGame {
        validatePlayDates(reqBody)
        val userGame = findUserGame(userId, gameId)
        val platform = findPlatform(reqBody.platformId)
        applyPlayRecord(userGame, reqBody, platform)
        synchronizeLibraryState(userGame)
        log.info("게임 플레이 기록 수정 완료 - userId={}, gameId={}", userId, gameId)
        return userGame
    }

    private fun createUserGame(
        userId: Long,
        gameId: Long,
        reqBody: UserGameReqBody,
        platform: Platform?,
    ): UserGame {
        val user = findUser(userId)
        val game: Game = findGame(gameId)
        val userGame =
            UserGame(
                user = user,
                game = game,
                playStatus = reqBody.playStatus,
                playing = reqBody.isPlaying,
                backlog = reqBody.isBacklog,
                wishlist = reqBody.isWishlist,
                liked = reqBody.isLiked,
                platform = platform,
                playTimeHours = reqBody.playTimeHours,
                finishTimeHours = reqBody.finishTimeHours,
                masterTimeHours = reqBody.masterTimeHours,
                startedAt = reqBody.startedAt,
                completedAt = reqBody.completedAt,
                lastPlayedAt = reqBody.lastPlayedAt,
            )
        return userGameRepository.save(userGame)
    }

    private fun applyPlayRecord(
        userGame: UserGame,
        reqBody: UserGameReqBody,
        platform: Platform?,
    ) {
        userGame.updatePlayRecord(
            playStatus = reqBody.playStatus,
            playing = reqBody.isPlaying,
            backlog = reqBody.isBacklog,
            wishlist = reqBody.isWishlist,
            liked = reqBody.isLiked,
            platform = platform,
            playTimeHours = reqBody.playTimeHours,
            finishTimeHours = reqBody.finishTimeHours,
            masterTimeHours = reqBody.masterTimeHours,
            startedAt = reqBody.startedAt,
            completedAt = reqBody.completedAt,
            lastPlayedAt = reqBody.lastPlayedAt,
        )
    }

    @Transactional
    fun synchronizeLibraryState(userGame: UserGame) {
        val hasActiveReview =
            userGame.id != null &&
                reviewRepository
                    .findByUserGame_Id(checkNotNull(userGame.id))
                    .filter { obj: Review -> obj.hasVisibleContent() }
                    .isPresent
        userGame.updateLibraryStatus(hasActiveReview)
        changeRecorder.record(requireNotNull(userGame.game.id), RelatedGameChangeReason.USER_GAME_RECORD)
        if (!userGame.isInLibrary) {
            userFavoriteGameRepository.deleteByUser_IdAndGame_Id(userGame.user.id, userGame.game.id)
        }
    }

    @Transactional(propagation = MANDATORY)
    fun synchronizeLibraryState(userGameId: Long) {
        val userGame =
            userGameRepository
                .findById(userGameId)
                .orElseThrow { ServiceException("404-2", "게임 기록을 찾을 수 없습니다.") }
        synchronizeLibraryState(userGame)
    }

    private fun validatePlayDates(reqBody: UserGameReqBody) {
        val today = LocalDate.now()
        if (reqBody.startedAt != null && reqBody.startedAt.isAfter(today)) {
            throw ServiceException("400-9", "시작일은 오늘보다 미래일 수 없습니다.")
        }
        if (
            reqBody.startedAt != null &&
                reqBody.completedAt != null &&
                reqBody.completedAt.isBefore(reqBody.startedAt)
        ) {
            throw ServiceException("400-6", "완료일은 시작일보다 빠를 수 없습니다.")
        }
        if (reqBody.completedAt != null && reqBody.completedAt.isAfter(today)) {
            throw ServiceException("400-8", "완료일은 오늘보다 미래일 수 없습니다.")
        }
        if (reqBody.lastPlayedAt != null && reqBody.lastPlayedAt.toLocalDate().isAfter(today)) {
            throw ServiceException("400-7", "마지막 플레이 날짜는 오늘보다 미래일 수 없습니다.")
        }
        if (
            reqBody.startedAt != null &&
                reqBody.lastPlayedAt != null &&
                reqBody.lastPlayedAt.toLocalDate().isBefore(reqBody.startedAt)
        ) {
            throw ServiceException("400-10", "마지막 플레이 날짜는 시작일보다 빠를 수 없습니다.")
        }
    }

    private fun findUser(userId: Long): User {
        return userRepository
            .findById(userId)
            .orElseThrow { ServiceException("404-1", "사용자를 찾을 수 없습니다.") }
    }

    private fun findGame(gameId: Long): Game {
        return gameRepository
            .findById(gameId)
            .orElseThrow { ServiceException("404-2", "게임을 찾을 수 없습니다.") }
    }

    private fun findUserGame(userId: Long, gameId: Long): UserGame {
        return userGameRepository
            .findByUser_IdAndGame_Id(userId, gameId)
            ?: throw ServiceException("404-3", "라이브러리에 등록되지 않은 게임입니다.")
    }

    private fun findPlatform(platformId: Long?): Platform? =
        platformId?.let {
            platformRepository
                .findById(it)
                .orElseThrow { ServiceException("404-4", "플랫폼을 찾을 수 없습니다.") }
        }

    private fun createUserGame(userId: Long, gameId: Long): UserGame {
        val user = findUser(userId)
        val game = findGame(gameId)
        return UserGame(
            user = user,
            game = game,
        )
    }

    private fun getOrCreateUserGame(userId: Long, gameId: Long): UserGame {
        return userGameRepository
            .findByUser_IdAndGame_Id(userId, gameId)
            ?: userGameRepository.save(createUserGame(userId, gameId))
    }

    private fun findOrCreateUserGameForBoolean(
        userId: Long,
        gameId: Long,
        status: Boolean,
    ): UserGame {
        val userGame = userGameRepository.findByUser_IdAndGame_Id(userId, gameId)
        if (userGame != null) {
            return userGame
        }
        if (!status) {
            log.warn(
                "라이브러리에 등록되지 않은 게임의 상태 해제 요청 - userId={}, gameId={}",
                userId,
                gameId,
            )
            throw ServiceException("404-3", "라이브러리에 등록되지 않은 게임입니다.")
        }
        return userGameRepository.save(createUserGame(userId, gameId))
    }

    @Transactional
    fun changePlayed(userId: Long, gameId: Long, playStatus: PlayStatus?): PlayStatus? {
        if (playStatus == null) {
            val userGame = findUserGame(userId, gameId)
            userGame.clearPlayStatus()
            synchronizeLibraryState(userGame)
            log.info("플레이 상태 해제 - userId={}, gameId={}", userId, gameId)
            return null
        }
        val userGame = getOrCreateUserGame(userId, gameId)
        userGame.changePlayStatus(playStatus)
        synchronizeLibraryState(userGame)
        log.info(
            "플레이 상태 변경 - userId={}, gameId={}, playStatus={}",
            userId,
            gameId,
            playStatus,
        )
        return playStatus
    }

    @Transactional
    fun changePlaying(userId: Long, gameId: Long, playing: Boolean): Boolean {
        val userGame = findOrCreateUserGameForBoolean(userId, gameId, playing)
        userGame.changePlaying(playing)
        synchronizeLibraryState(userGame)
        log.info(
            "플레이 중 상태 변경 - userId={}, gameId={}, playing={}",
            userId,
            gameId,
            playing,
        )
        return playing
    }

    @Transactional
    fun changeWishlist(userId: Long, gameId: Long, wishlist: Boolean): Boolean {
        val userGame = findOrCreateUserGameForBoolean(userId, gameId, wishlist)
        userGame.changeWishlist(wishlist)
        synchronizeLibraryState(userGame)
        log.info(
            "위시리스트 상태 변경 - userId={}, gameId={}, wishlist={}",
            userId,
            gameId,
            wishlist,
        )
        return wishlist
    }

    @Transactional
    fun changeBacklog(userId: Long, gameId: Long, backlog: Boolean): Boolean {
        val userGame = findOrCreateUserGameForBoolean(userId, gameId, backlog)
        userGame.changeBacklog(backlog)
        synchronizeLibraryState(userGame)
        log.info(
            "백로그 상태 변경 - userId={}, gameId={}, backlog={}",
            userId,
            gameId,
            backlog,
        )
        return backlog
    }

    @Transactional
    fun changeLiked(userId: Long, gameId: Long, liked: Boolean): Boolean {
        val userGame = findOrCreateUserGameForBoolean(userId, gameId, liked)
        userGame.changeLiked(liked)
        synchronizeLibraryState(userGame)
        log.info(
            "좋아요 상태 변경 - userId={}, gameId={}, liked={}",
            userId,
            gameId,
            liked,
        )
        return liked
    }

    // 프로필 탭
    @Transactional(readOnly = true)
    fun profileTab(targetUserId: Long, currentUserId: Long?): UserProfileResponse {
        log.debug(
            "프로필 조회 - targetUserId={}, currentUserId={}",
            targetUserId,
            currentUserId,
        )
        if (!userRepository.existsByIdAndDeletedAtIsNull(targetUserId)) {
            throw ServiceException("404-1", "존재하지 않는 유저입니다.")
        }
        val playedGames = userGameRepository.findPlayedGames(targetUserId)
        val ratings = reviewRepository.findPlayedGameRatings(targetUserId)
        // 인생게임
        val favoriteGames = getFavoriteGames(targetUserId)
        // 플레이 요약 카운터
        val playedGameCount = playedGames.size
        val averageRating = reviewRepository.findAverageRating(targetUserId)
        val totalPlayTime =
            playedGames
                .mapNotNull(UserGame::playTimeHours)
                .filter { it.signum() > 0 }
                .fold(BigDecimal.ZERO, BigDecimal::add)
        // 산점도
        val scatterData = userGameRepository.findPlayedGameScatterData(targetUserId)
        // 내 게임 취향 한번에 보기
        // 30시간 이상 플레이 비율
        val longPlay = calculateLongPlay(playedGames)
        // 별점 기반
        val rating = calculateRating(ratings)
        // 완료율 기반
        val completion = calculateCompletion(playedGames)
        // 장르 분포
        val genreDistribution = getGenreDistribution(targetUserId)
        // 최근 플레이 게임
        val recentPlayedGames = getRecentPlayedGames(targetUserId)
        // 최근 리뷰
        val recentReviews = getRecentReviews(targetUserId)
        val isMe = currentUserId != null && targetUserId == currentUserId
        val isFollowing =
            currentUserId != null &&
                !isMe &&
                userFollowRepository.existsByFollowerIdAndFolloweeId(
                    currentUserId,
                    targetUserId,
                )
        val statsResponse =
            ProfileStatsDto(
                playedGameCount = playedGameCount.toLong(),
                averageRating = averageRating,
                totalPlayTime = totalPlayTime,
            )
        val tasteResponse =
            UserGameTasteDto(
                longPlay = longPlay,
                rating = rating,
                completion = completion,
            )
        return UserProfileResponse(
            userId = targetUserId,
            isMe = isMe,
            isFollowing = isFollowing,
            favorite = favoriteGames,
            stats = statsResponse,
            scatterData = scatterData,
            tasteResponse = tasteResponse,
            genreDistribution = genreDistribution,
            recentGames = recentPlayedGames,
            recentReviews = recentReviews,
        )
    }

    fun getFavoriteGames(userId: Long): List<UserFavoriteGameResponse> =
        userFavoriteGameRepository
            .findAllByUserIdOrderByDisplayOrderAsc(userId)
            .map(::UserFavoriteGameResponse)

    @Transactional
    fun updateFavoriteGames(
        userId: Long,
        gameIds: List<Long>,
    ): List<UserFavoriteGameResponse> {
        // 1. 최대 5개 검증
        require(gameIds.size <= 5) { "인생게임은 최대 5개까지 등록할 수 있습니다." }
        // 2. 중복 게임 검증
        require(gameIds.distinct().size == gameIds.size) {
            "같은 게임을 중복해서 등록할 수 없습니다."
        }
        val user: User =
            userRepository
                .findById(userId)
                .orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다.") }
        // 3. 사용자의 UserGame 조회
        val userGames: List<UserGame> =
            userGameRepository.findAllByUser_IdAndGame_IdInAndInLibraryTrue(userId, gameIds)
        // 4. 모든 게임이 사용자의 라이브러리에 있는지 검증
        if (userGames.size != gameIds.size) {
            throw ServiceException(
                "400-11",
                "라이브러리에 없는 게임이 포함되어 있습니다. 선택 목록을 확인해 주세요.",
            )
        }
        // 5. gameId -> Game 매핑
        val gameMap =
            userGames.associate { userGame ->
                checkNotNull(userGame.game.id) to userGame.game
            }
        // 6. 기존 인생게임 목록 삭제
        userFavoriteGameRepository.deleteAllByUserId(userId)
        // 7. 요청 순서대로 새 인생게임 생성
        val favoriteGames =
            gameIds.mapIndexed { index, gameId ->
                UserFavoriteGame(
                    user = user,
                    game = gameMap.getValue(gameId),
                    displayOrder = index + 1,
                )
            }
        userFavoriteGameRepository.saveAll(favoriteGames)
        log.info("인생게임 수정 완료 - userId={}, gameCount={}", userId, gameIds.size)
        return favoriteGames.map(::UserFavoriteGameResponse)
    }

    private fun calculateLongPlay(playedGames: List<UserGame>): TasteMetricDto {
        val playTimes =
            playedGames
                .mapNotNull(UserGame::playTimeHours)
                .filter { it.signum() > 0 }
        if (playTimes.size < MIN_DATA_COUNT) {
            return TasteMetricDto(
                ratio = null,
                message = "아직 플레이 기록이 부족해요.",
                description = "플레이 시간 기록이 3개 이상 쌓이면 확인할 수 있어요.",
            )
        }
        val longPlayCount =
            playTimes
                .count { playTime -> playTime >= LONG_PLAY_HOURS }
                .toLong()
        val longPlayRatio = calculateRatio(longPlayCount, playTimes.size)
        // 70% 이상
        if (longPlayRatio >= HIGH_RATIO) {
            return TasteMetricDto(
                ratio = longPlayRatio,
                message = "장시간 플레이하는 게임이 많아요.",
                description = createPercentageDescription(longPlayRatio, "30시간 이상 플레이한 게임이"),
            )
        }
        // 40% 이상
        if (longPlayRatio >= MEDIUM_RATIO) {
            return TasteMetricDto(
                ratio = longPlayRatio,
                message = "장시간 플레이와 짧은 게임을 골고루 즐겨요.",
                description = createPercentageDescription(longPlayRatio, "30시간 이상 플레이한 게임이"),
            )
        }
        // 40% 미만이면 짧은 게임 비율을 보여준다.
        val shortPlayRatio = BigDecimal.ONE.subtract(longPlayRatio)
        return TasteMetricDto(
            ratio = shortPlayRatio,
            message = "짧게 플레이하는 게임이 많아요.",
            description = createPercentageDescription(shortPlayRatio, "30시간 미만 플레이한 게임이"),
        )
    }

    private fun calculateRating(ratings: List<BigDecimal>): TasteMetricDto {
        if (ratings.size < MIN_DATA_COUNT) {
            return TasteMetricDto(
                ratio = null,
                message = "아직 평가 기록이 부족해요.",
                description = "게임 평가가 3개 이상 쌓이면 확인할 수 있어요.",
            )
        }
        val highRatingCount: Long =
            ratings
                .count { rating -> rating >= HIGH_RATING }
                .toLong()
        val mediumRatingCount =
            ratings
                .count { rating -> rating in LOW_RATING..<HIGH_RATING }
                .toLong()
        val lowRatingCount: Long =
            ratings
                .count { rating -> rating < LOW_RATING }
                .toLong()
        val highRatingRatio = calculateRatio(highRatingCount, ratings.size)
        val mediumRatingRatio = calculateRatio(mediumRatingCount, ratings.size)
        val lowRatingRatio = calculateRatio(lowRatingCount, ratings.size)
        // ① 2.5 ~ 4.0 구간이 가장 크거나 동률인 경우
        if (
            mediumRatingRatio >= highRatingRatio &&
            mediumRatingRatio >= lowRatingRatio
        ) {
            return TasteMetricDto(
                ratio = mediumRatingRatio,
                message = "게임을 비교적 후하게 평가하는 편이에요.",
                description = createPercentageDescription(mediumRatingRatio, "2.5점 이상 4.0점 미만으로 평가한 게임이"),
            )
        }
        // ② 4.0 이상 구간이 가장 큰 경우
        if (highRatingRatio >= lowRatingRatio) {
            return TasteMetricDto(
                ratio = highRatingRatio,
                message = "높은 평점을 주는 게임이 많아요.",
                description = createPercentageDescription(highRatingRatio, "4.0점 이상 평가한 게임이"),
            )
        }
        // ③ 2.5 미만 구간이 가장 큰 경우
        return TasteMetricDto(
            ratio = lowRatingRatio,
            message = "평점을 신중하게 주는 편이에요.",
            description = createPercentageDescription(lowRatingRatio, "2.5점 미만으로 평가한 게임이"),
        )
    }

    private fun calculateCompletion(playedGames: List<UserGame>): TasteMetricDto {
        if (playedGames.size < MIN_DATA_COUNT) {
            return TasteMetricDto(
                ratio = null,
                message = "아직 게임 기록이 부족해요.",
                description = "플레이한 게임이 3개 이상 쌓이면 확인할 수 있어요.",
            )
        }
        val completedCount: Long =
            playedGames
                .count { userGame -> userGame.playStatus == PlayStatus.COMPLETED }
                .toLong()
        val completionRatio = calculateRatio(completedCount, playedGames.size)
        // 70% 이상
        if (completionRatio >= HIGH_RATIO) {
            return TasteMetricDto(
                ratio = completionRatio,
                message = "게임을 끝까지 플레이하는 편이에요.",
                description = createPercentageDescription(completionRatio, "완료한 게임이"),
            )
        }
        // 40% 이상
        if (completionRatio >= MEDIUM_RATIO) {
            return TasteMetricDto(
                ratio = completionRatio,
                message = "플레이한 게임 중 절반 정도를 완료했어요.",
                description = createPercentageDescription(completionRatio, "완료한 게임이"),
            )
        }
        return TasteMetricDto(
            ratio = completionRatio,
            message = "끝까지 완료하기보다 여러 게임을 플레이하는 편이에요.",
            description = createPercentageDescription(completionRatio, "완료한 게임이"),
        )
    }

    private fun calculateRatio(count: Long, total: Int): BigDecimal {
        return BigDecimal.valueOf(count)
            .divide(BigDecimal.valueOf(total.toLong()), 4, RoundingMode.HALF_UP)
    }

    private fun createPercentageDescription(ratio: BigDecimal, subject: String): String {
        val percentage = ratio.multiply(BigDecimal.valueOf(100)).stripTrailingZeros()
        return subject + " " + percentage.toPlainString() + "%예요."
    }

    fun getGenreDistribution(userId: Long): List<UserGameGenreDistributionResponse> {
        val distributions = userGameRepository.findGenreDistribution(userId)
        val totalGenreGameCount = distributions.sumOf(UserGameGenreDTO::gameCount)
        return distributions.map { dto ->
                val ratio =
                    BigDecimal.valueOf(dto.gameCount)
                        .divide(BigDecimal.valueOf(totalGenreGameCount), 4, RoundingMode.HALF_UP)
                UserGameGenreDistributionResponse(
                    genreName = dto.genreName,
                    ratio = ratio,
                )
            }
    }

    fun getRecentPlayedGames(userId: Long): List<UserGameListResponse> {
        val pageable = PageRequest.of(0, 5)
        return userGameRepository
            .findRecentPlayedGames(userId, pageable)
            .map(::UserGameListResponse)
    }

    fun getRecentReviews(userId: Long): List<RecentReviewResponse> {
        val pageable = PageRequest.of(0, 3)
        return reviewRepository
            .findRecentReviews(userId, pageable)
            .map(::RecentReviewResponse)
    }

    companion object {
        private val log: org.slf4j.Logger =
            org.slf4j.LoggerFactory.getLogger(UserGameService::class.java)
        private val LONG_PLAY_HOURS: BigDecimal = BigDecimal.valueOf(30)
        private val HIGH_RATIO: BigDecimal = BigDecimal.valueOf(0.7)
        private val MEDIUM_RATIO: BigDecimal = BigDecimal.valueOf(0.4)
        private val HIGH_RATING: BigDecimal = BigDecimal.valueOf(4.0)
        private val LOW_RATING: BigDecimal = BigDecimal.valueOf(2.5)
        private const val MIN_DATA_COUNT = 3
    }
}
