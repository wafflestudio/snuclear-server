CREATE TABLE IF NOT EXISTS sugang_period_snapshots (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    year        INT          NOT NULL,
    semester    ENUM('SPRING', 'SUMMER', 'FALL', 'WINTER') NOT NULL,
    dumped_data TEXT         NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,

    CONSTRAINT uk_sugang_period_snapshots_term UNIQUE (year, semester)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='학기별 수강신청 일정 정본';
