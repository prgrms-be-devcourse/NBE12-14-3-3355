package com.gamelog.nbe121423355.domain.user.service

import com.gamelog.nbe121423355.domain.game.repository.GenreRepository
import com.gamelog.nbe121423355.domain.user.dto.PreferredGenreResponseDto
import com.gamelog.nbe121423355.domain.user.entity.UserPreferenceGenre
import com.gamelog.nbe121423355.domain.user.repository.UserPreferenceGenreRepository
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class UserPreferenceGenreService
constructor(
    private val userPreferenceGenreRepository: UserPreferenceGenreRepository,
    private val userRepository: UserRepository,
    private val genreRepository: GenreRepository,
) {
    // 온보딩 처음 선택 + 나중에 다시 바꾸기 구분하지 않는 로직
    @Transactional
    fun updatePreferredGenres(
        userId: Long,
        genreIds: MutableList<Long>,
    ): MutableList<PreferredGenreResponseDto> {
        if (genreIds.size > 3) {
            throw ServiceException("400-12", "선호 장르는 최대 3개까지 선택 가능합니다.") // 온보딩 장르 최대 3개 선택
        }
        val user =
            userRepository
                .findById(userId)
                .orElseThrow({ ServiceException("404-1", "존재하지 않는 유저 입니다.") })
        val genres = genreRepository.findAllById(genreIds)
        if (genres.size != genreIds.size) {
            throw ServiceException("400-11", "존재하지 않는 장르가 포함되어 있습니다.")
        }
        userPreferenceGenreRepository.deleteByUser_Id(userId)
        val newPreferences: MutableList<UserPreferenceGenre> =
            genres.stream().map({ genre -> UserPreferenceGenre(user, genre) }).toList()
        userPreferenceGenreRepository.saveAll(newPreferences)
        return newPreferences.stream().map({ value -> PreferredGenreResponseDto(value) }).toList()
    }

    // 온보딩 장르정보 가져오기
    fun getPreferredGenres(userId: Long?): MutableList<PreferredGenreResponseDto> {
        return userPreferenceGenreRepository
            .findByUser_Id(userId)
            .stream()
            .map({ value -> PreferredGenreResponseDto(value) })
            .toList()
    }
}
