ALTER TABLE users
    ADD COLUMN tour_completed_at DATETIME(6) NULL DEFAULT NULL;

CREATE TABLE tour_config (
    id TINYINT NOT NULL,
    published_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='서비스 투어 설정';

INSERT INTO tour_config (id, published_at)
VALUES (1, CURRENT_TIMESTAMP(6));