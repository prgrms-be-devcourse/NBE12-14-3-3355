-- MySQL 운영 DB: 알림 기능 배포 전에 적용. 기존 활동에 대한 알림은 소급 생성하지 않는다.
CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    recipient_id BIGINT NOT NULL,
    actor_id BIGINT NULL,
    kind VARCHAR(40) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id BIGINT NOT NULL,
    review_id BIGINT NULL,
    game_title VARCHAR(255) NULL,
    source_key VARCHAR(160) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    read_at DATETIME(6) NULL,
    CONSTRAINT uk_notification_source_recipient UNIQUE (source_key, recipient_id),
    INDEX idx_notification_recipient_id (recipient_id, id),
    INDEX idx_notification_unread (recipient_id, read_at),
    INDEX idx_notification_created (created_at),
    INDEX idx_notification_read (read_at)
);
-- 대상 삭제/회원 탈퇴 후에도 알림을 남기므로 의도적으로 외래 키를 두지 않는다.
