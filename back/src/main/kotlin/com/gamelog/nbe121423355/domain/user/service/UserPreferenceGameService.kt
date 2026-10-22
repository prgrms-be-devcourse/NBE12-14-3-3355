package com.gamelog.nbe121423355.domain.user.service

import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.user.dto.PreferredGameResponseDto
import com.gamelog.nbe121423355.domain.user.entity.UserPreferenceGame
import com.gamelog.nbe121423355.domain.user.repository.UserPreferenceGameRepository
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class UserPreferenceGameService
constructor(
    private val userPreferenceGameRepository: UserPreferenceGameRepository,
    private val userRepository: UserRepository,
    private val gameRepository: GameRepository,
) {
    // 온보딩 처음 선택 + 나중에 다시 바꾸기 구분하지 않는 로직
    @Transactional
    fun updatePreferredGames(
        userId: Long,
        genreIds: MutableList<Long>,
    ): MutableList<PreferredGameResponseDto> {
        if (genreIds.size > 3) {
            throw ServiceException("400-12", "선호 게임은 최대 3개까지 선택 가능합니다.") // 온보딩 장르 최대 3개 선택
        }
        val user =
            userRepository
                .findById(userId)
                .orElseThrow({ ServiceException("404-1", "존재하지 않는 유저 입니다.") })
        val games = gameRepository.findAllById(genreIds)
        if (games.size != genreIds.size) {
            throw ServiceException("400-11", "존재하지 않는 게임이 포함되어 있습니다.")
        }
        userPreferenceGameRepository.deleteByUser_Id(userId)
        val newPreferences: MutableList<UserPreferenceGame> =
            games.stream().map({ game -> UserPreferenceGame(user, game) }).toList()
        userPreferenceGameRepository.saveAll(newPreferences)
        return newPreferences.stream().map({ value -> PreferredGameResponseDto(value) }).toList()
    }

    // 온보딩 게임정보 가져오기
    fun getPreferredGames(userId: Long?): MutableList<PreferredGameResponseDto> {
        return userPreferenceGameRepository
            .findByUser_Id(userId)
            .stream()
            .map({ value -> PreferredGameResponseDto(value) })
            .toList()
    }
}
