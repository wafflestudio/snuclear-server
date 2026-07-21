CREATE TABLE IF NOT EXISTS course_cart_snapshots (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id            BIGINT       NOT NULL,
    regular_cart_count   INT          NOT NULL COMMENT '재학생 장바구니 담은 수',
    freshman_cart_count  INT          NOT NULL COMMENT '신입생 장바구니 신청 수',
    captured_at          DATETIME(6)  NOT NULL,

    CONSTRAINT uk_course_cart_snapshots_course UNIQUE (course_id),
    CONSTRAINT fk_course_cart_snapshots_course FOREIGN KEY (course_id)
        REFERENCES courses (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='강의별 장바구니 종료 시점 스냅샷';

CREATE INDEX idx_course_cart_snapshots_captured_at
    ON course_cart_snapshots (captured_at);
