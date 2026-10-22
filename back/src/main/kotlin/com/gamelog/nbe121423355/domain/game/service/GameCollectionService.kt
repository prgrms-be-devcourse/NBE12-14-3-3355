package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.sync.IgdbSyncService
import org.springframework.stereotype.Service

@Service
class GameCollectionService constructor(private val syncService: IgdbSyncService) {
    fun importGames() {
        // 기존 local 시작 옵션도 동일한 동기화 정책과 중복 실행 방지를 사용합니다.
        syncService.start(0L)
    }
}
