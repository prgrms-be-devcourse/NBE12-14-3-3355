package com.gamelog.nbe121423355.domain.game.controller

import com.gamelog.nbe121423355.domain.game.sync.*
import com.gamelog.nbe121423355.domain.game.sync.IgdbSyncService
import com.gamelog.nbe121423355.domain.game.sync.IgdbSyncState
import com.gamelog.nbe121423355.domain.game.sync.IgdbSyncStore
import com.gamelog.nbe121423355.global.dto.RsData
import com.gamelog.nbe121423355.global.security.SecurityUser
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.ACCEPTED
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/admin/igdb-sync")
class AdminIgdbSyncController
constructor(private val service: IgdbSyncService, private val store: IgdbSyncStore) {
    @GetMapping
    fun status(): RsData<IgdbSyncState> {
        return RsData<IgdbSyncState>("200-1", "동기화 상태", store.status())
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun start(@AuthenticationPrincipal user: SecurityUser): RsData<IgdbSyncState> {
        return RsData<IgdbSyncState>("202-1", "동기화를 시작했습니다.", service.start(user.id))
    }
}
