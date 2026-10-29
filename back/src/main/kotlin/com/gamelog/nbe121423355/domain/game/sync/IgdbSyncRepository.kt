package com.gamelog.nbe121423355.domain.game.sync

import jakarta.persistence.LockModeType
import jakarta.persistence.LockModeType.PESSIMISTIC_WRITE
import org.springframework.data.jpa.repository.*

interface IgdbSyncRepository : JpaRepository<IgdbSyncState, Long> {
    @org.springframework.data.jpa.repository.Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from IgdbSyncState s where s.id = 1")
    fun lockState(): IgdbSyncState?
}
