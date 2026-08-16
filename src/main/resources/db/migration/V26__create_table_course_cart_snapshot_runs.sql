CREATE TABLE IF NOT EXISTS course_cart_snapshot_runs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    year        INT          NOT NULL,
    semester    ENUM('SPRING', 'SUMMER', 'FALL', 'WINTER') NOT NULL,
    status      VARCHAR(20)  NOT NULL COMMENT 'PENDING | SUCCESS | FAILED',
    claim_token VARCHAR(36)  NOT NULL,
    claimed_at  DATETIME(6)  NOT NULL,
    captured_at DATETIME(6)  NULL,
    message     VARCHAR(500) NULL,

    CONSTRAINT uk_course_cart_snapshot_runs_term UNIQUE (year, semester)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='학기별 장바구니 자동 스냅샷 실행 이력';
