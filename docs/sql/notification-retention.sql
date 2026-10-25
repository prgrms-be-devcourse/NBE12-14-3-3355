-- 이미 notifications 테이블을 생성한 운영 DB에만 한 번 적용.
-- 새 DB에 최신 notifications.sql로 생성했다면 실행하지 않는다.
CREATE INDEX idx_notification_created ON notifications (created_at);
CREATE INDEX idx_notification_read ON notifications (read_at);
