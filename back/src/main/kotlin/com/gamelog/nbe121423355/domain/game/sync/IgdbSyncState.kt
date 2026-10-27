package com.gamelog.nbe121423355.domain.game.sync

import jakarta.persistence.*
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "igdb_sync_state")
class IgdbSyncState {
    @Id var id: Long? = 1L

    var status: String = "IDLE"

    var mode: String? = null

    var runId: String? = null

    var requestedBy: Long? = null

    var watermark: Long? = null

    var sinceEpoch: Long? = null

    var cutoff: Long = 0L

    var cursorId: Long = 0L

    var processed: Long = 0L

    var inserted: Long = 0L

    var updated: Long = 0L

    var deleted: Long = 0L

    var skipped: Long = 0L

    var leaseUntil: Long = 0L

    var finishedAt: Long? = null

    @Column(length = 500) var error: String? = null
}
