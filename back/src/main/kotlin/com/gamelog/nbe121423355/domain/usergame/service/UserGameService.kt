package com.gamelog.nbe121423355.domain.usergame.service

import com.gamelog.nbe121423355.domain.game.entity.RelatedGameChangeReason
import com.gamelog.nbe121423355.domain.game.service.RelatedGameChangeRecorder
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.Platform
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.game.repository.PlatformRepository
import com.gamelog.nbe121423355.domain.review.repository.ReviewRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserFavoriteGameRepository
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.domain.usergame.dto.*
import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.domain.usergame.repository.UserGameRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.global.interop.javaIsBlank
import com.gamelog.nbe121423355.global.interop.strip
import java.time.LocalDate
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
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
        val keyword = request.keyword
        val pattern = if (keyword == null || keyword.javaIsBlank()) {
            null
        } else {
            val escapedKeyword = keyword.strip()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_")
            "%$escapedKeyword%"
        }
        val platformIds = request.platformIds.orEmpty()
        val genreIds = request.genreIds.orEmpty()
        return userGameRepository
            .findByLibraryFilters(
                userId,
                requireNotNull(request.status).name,
                pattern,
                platformIds.isNotEmpty(),
                platformIds.ifEmpty { listOf(0L) },
                genreIds.isNotEmpty(),
                genreIds.ifEmpty { listOf(0L) },
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
        val game = findGame(gameId)
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
            userGame.id?.let { userGameId ->
                reviewRepository.findByUserGame_Id(userGameId)
                    .filter { it.hasVisibleContent() }
                    .isPresent
            } == true
        userGame.updateLibraryStatus(hasActiveReview)
        changeRecorder.record(requireNotNull(userGame.game.id), RelatedGameChangeReason.USER_GAME_RECORD)
        if (!userGame.isInLibrary) {
            userFavoriteGameRepository.deleteByUser_IdAndGame_Id(userGame.user.id, userGame.game.id)
        }
    }

    @Transactional(propagation = MANDATORY)
    fun synchronizeLibraryState(userGameId: Long) {
        val userGame =
            userGameRepository.findByIdOrNull(userGameId)
                ?: throw ServiceException("404-2", "게임 기록을 찾을 수 없습니다.")
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

    private fun findUser(userId: Long): User =
        userRepository.findByIdOrNull(userId)
            ?: throw ServiceException("404-1", "사용자를 찾을 수 없습니다.")

    private fun findGame(gameId: Long): Game =
        gameRepository.findByIdOrNull(gameId)
            ?: throw ServiceException("404-2", "게임을 찾을 수 없습니다.")

    private fun findUserGame(userId: Long, gameId: Long): UserGame =
        userGameRepository.findByUser_IdAndGame_Id(userId, gameId)
            ?: throw ServiceException("404-3", "라이브러리에 등록되지 않은 게임입니다.")

    private fun findPlatform(platformId: Long?): Platform? =
        platformId?.let {
            platformRepository.findByIdOrNull(it)
                ?: throw ServiceException("404-4", "플랫폼을 찾을 수 없습니다.")
        }

    private fun createUserGame(userId: Long, gameId: Long): UserGame {
        val user = findUser(userId)
        val game = findGame(gameId)
        return UserGame(
            user = user,
            game = game,
        )
    }

    private fun getOrCreateUserGame(userId: Long, gameId: Long): UserGame =
        userGameRepository.findByUser_IdAndGame_Id(userId, gameId)
            ?: userGameRepository.save(createUserGame(userId, gameId))

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

    fun getRecentPlayedGames(userId: Long): List<UserGameListResponse> {
        val pageable = PageRequest.of(0, 5)
        return userGameRepository
            .findRecentPlayedGames(userId, pageable)
            .map(::UserGameListResponse)
    }

    companion object {
        private val log: org.slf4j.Logger =
            org.slf4j.LoggerFactory.getLogger(UserGameService::class.java)
    }
}
