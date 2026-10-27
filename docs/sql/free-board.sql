-- MySQL 운영 DB: 자유게시판 배포 전 한 번 적용. 개발의 ddl-auto=update로 이미 생성된 DB에는 재실행하지 않는다.
-- 상태 컬럼은 notifications와 같이 VARCHAR로 두어 값 추가 시 ALTER가 필요 없게 한다.
CREATE TABLE board_posts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    game_id BIGINT NULL,
    title VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    spoiler BIT NOT NULL,
    edited BIT NOT NULL,
    status VARCHAR(20) NOT NULL,
    deleted_at DATETIME(6) NULL,
    created_date DATETIME(6) NULL,
    last_modified_date DATETIME(6) NULL,
    CONSTRAINT fk_board_post_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_board_post_game FOREIGN KEY (game_id) REFERENCES games(id),
    INDEX idx_board_post_status_id (status, id)
);

CREATE TABLE board_comments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    content TEXT NOT NULL,
    spoiler BIT NOT NULL,
    edited BIT NOT NULL,
    deleted BIT NOT NULL,
    hidden BIT NOT NULL,
    created_date DATETIME(6) NULL,
    last_modified_date DATETIME(6) NULL,
    CONSTRAINT fk_board_comment_post FOREIGN KEY (post_id) REFERENCES board_posts(id),
    CONSTRAINT fk_board_comment_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_board_comment_parent FOREIGN KEY (parent_id) REFERENCES board_comments(id),
    INDEX idx_board_comment_post_parent_id (post_id, parent_id, id)
);

CREATE TABLE board_post_likes (
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_date DATETIME(6) NULL,
    last_modified_date DATETIME(6) NULL,
    PRIMARY KEY (post_id, user_id)
);

-- 대상(글·댓글)은 target_type으로 구분하므로 target_id에는 외래 키를 두지 않는다.
CREATE TABLE board_reports (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    reporter_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    title_snapshot VARCHAR(100) NULL,
    content_snapshot TEXT NOT NULL,
    spoiler_snapshot BIT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_date DATETIME(6) NULL,
    last_modified_date DATETIME(6) NULL,
    CONSTRAINT fk_board_report_reporter FOREIGN KEY (reporter_id) REFERENCES users(id),
    CONSTRAINT uk_board_report_target_reporter UNIQUE (target_type, target_id, reporter_id),
    INDEX idx_board_report_status_id (status, id)
);
