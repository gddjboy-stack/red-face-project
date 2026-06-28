-- H2 测试环境可能因不同 Spring 测试上下文重复初始化，先按外键反向顺序清理旧表。
DROP TABLE IF EXISTS user_session;
DROP TABLE IF EXISTS suspicion_votes;
DROP TABLE IF EXISTS user_membership;
DROP TABLE IF EXISTS user_photo_collection;
DROP TABLE IF EXISTS tokens;
DROP TABLE IF EXISTS photo_assets;
DROP TABLE IF EXISTS coefficient_ledger;
DROP TABLE IF EXISTS popularity_ledger;
DROP TABLE IF EXISTS team_distribution_batches;
DROP TABLE IF EXISTS idempotency_ledger;
DROP TABLE IF EXISTS operations_log;
DROP TABLE IF EXISTS collect_state;
DROP TABLE IF EXISTS pool_round_stats;
DROP TABLE IF EXISTS team_round_stats;
DROP TABLE IF EXISTS player_round_stats;
DROP TABLE IF EXISTS player_round;
DROP TABLE IF EXISTS user_identity;
DROP TABLE IF EXISTS rounds;
DROP TABLE IF EXISTS teams;
DROP TABLE IF EXISTS players;

-- 选手表(只存固定信息,每轮变化的信息在 player_round)
CREATE TABLE players (
  player_id   INT NOT NULL AUTO_INCREMENT,
  name        VARCHAR(100) NOT NULL,
  number      INT NOT NULL,
  status      VARCHAR(20) NOT NULL DEFAULT 'active',
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (player_id),
  UNIQUE KEY uq_number (number)
);

CREATE TABLE teams (
  team_id     INT NOT NULL AUTO_INCREMENT,
  name        VARCHAR(100) NOT NULL,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (team_id)
);

CREATE TABLE rounds (
  round_id    INT NOT NULL AUTO_INCREMENT,
  name        VARCHAR(100) NOT NULL,
  start_time  TIMESTAMP NOT NULL,
  end_time    TIMESTAMP NOT NULL,
  status      VARCHAR(20) NOT NULL DEFAULT 'upcoming',
  PRIMARY KEY (round_id)
);

-- 选手每轮关联(团队归属/卧底身份/状态都按轮变化)
CREATE TABLE player_round (
  player_id     INT NOT NULL,
  round_id      INT NOT NULL,
  team_id       INT NULL,
  is_spy        TINYINT NOT NULL DEFAULT 0,
  spy_status    VARCHAR(20) NULL,
  player_status VARCHAR(20) NOT NULL DEFAULT 'normal',
  PRIMARY KEY (player_id, round_id)
);

-- ★ 人气值流水账(系统唯一真相来源,只增不改)
-- 注意:没有衰减字段!衰减是积分计算时的事,不是流水的事
CREATE TABLE popularity_ledger (
  ledger_id        BIGINT NOT NULL AUTO_INCREMENT,
  target_type      VARCHAR(20) NOT NULL,
  target_id        INT NULL,
  source           VARCHAR(30) NOT NULL,
  raw_value        BIGINT NOT NULL,
  popularity_value BIGINT NOT NULL,
  round_id         INT NULL,
  idempotency_key  VARCHAR(128) NOT NULL,
  distribution_batch_id BIGINT NULL,
  operator_id      VARCHAR(64) NULL,
  reason           VARCHAR(500) NULL,
  metadata         CLOB NULL,
  occurred_at      TIMESTAMP NOT NULL,
  created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (ledger_id),
  UNIQUE KEY uq_idem (idempotency_key),
  KEY idx_target (target_type, target_id, round_id)
);

-- 选手每轮统计(含加成系数!)
CREATE TABLE player_round_stats (
  player_id             INT NOT NULL,
  round_id              INT NOT NULL,
  individual_popularity BIGINT NOT NULL DEFAULT 0,
  spy_popularity        BIGINT NOT NULL DEFAULT 0,
  coefficient           INT NOT NULL DEFAULT 100,
  updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (player_id, round_id)
);

CREATE TABLE team_round_stats (
  team_id                INT NOT NULL,
  round_id               INT NOT NULL,
  team_popularity        BIGINT NOT NULL DEFAULT 0,
  distributed_popularity BIGINT NOT NULL DEFAULT 0,
  updated_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (team_id, round_id)
);

CREATE TABLE pool_round_stats (
  round_id        INT NOT NULL,
  pool_popularity BIGINT NOT NULL DEFAULT 0,
  updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (round_id)
);

-- 加成系数变动流水(防任务重复加减)
CREATE TABLE coefficient_ledger (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  player_id       INT NOT NULL,
  round_id        INT NOT NULL,
  task_id         VARCHAR(64) NOT NULL,
  task_type       VARCHAR(30) NOT NULL,
  delta           INT NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  operator_id     VARCHAR(64) NOT NULL,
  reason          VARCHAR(500) NULL,
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_coef_idem (idempotency_key)
);

-- 卡密表(补全player_id/points/写真)
CREATE TABLE tokens (
  token_id       VARCHAR(32) NOT NULL,
  player_id      INT NOT NULL,
  points         BIGINT NOT NULL,
  photo_asset_id VARCHAR(64) NULL,
  product_sku    VARCHAR(64) NULL,
  aqiso_batch_id VARCHAR(64) NULL,
  status         VARCHAR(20) NOT NULL DEFAULT 'unused',
  order_id       VARCHAR(64) NULL,
  user_id        VARCHAR(64) NULL,
  redeem_source  VARCHAR(20) NULL,
  used_at        TIMESTAMP NULL,
  created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (token_id),
  KEY idx_status (status),
  KEY idx_player (player_id)
);

CREATE TABLE photo_assets (
  asset_id     VARCHAR(64) NOT NULL,
  player_id    INT NOT NULL,
  preview_url  VARCHAR(500) NOT NULL,
  download_url VARCHAR(500) NULL,
  status       VARCHAR(20) NOT NULL DEFAULT 'active',
  is_cover     TINYINT NOT NULL DEFAULT 0,
  sort_order   INT NOT NULL DEFAULT 0,
  file_name    VARCHAR(255) NULL,
  content_type VARCHAR(100) NULL,
  file_size    BIGINT NULL,
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (asset_id),
  KEY idx_photo_player_status (player_id, status, is_cover, sort_order)
);

-- 用户写真收藏(核销后自动收藏)
CREATE TABLE user_photo_collection (
  id         BIGINT NOT NULL AUTO_INCREMENT,
  user_id    VARCHAR(64) NOT NULL,
  asset_id   VARCHAR(64) NOT NULL,
  token_id   VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_user_token (user_id, token_id)
);

-- C9 用户身份映射表：openid 不明文落库，只保存 hash 与脱敏 user_id
CREATE TABLE user_identity (
  user_id       VARCHAR(64) NOT NULL,
  openid_hash   VARCHAR(128) NOT NULL,
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_login_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  UNIQUE KEY uq_openid_hash (openid_hash)
);

-- C9 用户会话表：Bearer token 登录态
CREATE TABLE user_session (
  token        VARCHAR(128) NOT NULL,
  user_id      VARCHAR(64) NOT NULL,
  expires_at   TIMESTAMP NOT NULL,
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (token),
  KEY idx_user_session_user (user_id)
);

-- C16 用户会员有效期聚合表：只保存正向叠加后的当前有效期
CREATE TABLE user_membership (
  user_id          VARCHAR(64) NOT NULL,
  membership_until TIMESTAMP NOT NULL,
  last_token_id    VARCHAR(32) NULL,
  created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  KEY idx_membership_until (membership_until)
);

CREATE TABLE suspicion_votes (
  vote_id           BIGINT NOT NULL AUTO_INCREMENT,
  user_id           VARCHAR(64) NOT NULL,
  round_id          INT NOT NULL,
  team_id           INT NOT NULL,
  suspect_player_id INT NOT NULL,
  voted_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (vote_id),
  UNIQUE KEY uq_user_round_suspect (user_id, round_id, suspect_player_id),
  KEY idx_round_team (round_id, team_id, suspect_player_id)
);

-- 团队分配批次(只存批次元数据,实际人气变更在ledger里!)
CREATE TABLE team_distribution_batches (
  batch_id       BIGINT NOT NULL AUTO_INCREMENT,
  team_id        INT NOT NULL,
  round_id       INT NOT NULL,
  total_value    BIGINT NOT NULL,
  method         VARCHAR(20) NOT NULL,
  custom_weights CLOB NULL,
  operator_id    VARCHAR(64) NOT NULL,
  reason         VARCHAR(500) NULL,
  created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (batch_id)
);

-- 场控状态(当前集赞归属,单行表)
CREATE TABLE collect_state (
  id          INT NOT NULL DEFAULT 1,
  mode        VARCHAR(20) NOT NULL DEFAULT 'pool',
  target_id   INT NULL,
  round_id    INT NULL,
  updated_by  VARCHAR(64) NULL,
  updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

-- 操作审计日志(只增)
CREATE TABLE operations_log (
  id          BIGINT NOT NULL AUTO_INCREMENT,
  operator_id VARCHAR(64) NOT NULL,
  action_type VARCHAR(50) NOT NULL,
  target      VARCHAR(100) NULL,
  detail      CLOB NULL,
  reason      VARCHAR(500) NULL,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

-- Foreign Key Constraints
ALTER TABLE player_round ADD CONSTRAINT fk_player_round_player_id FOREIGN KEY (player_id) REFERENCES players (player_id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE player_round ADD CONSTRAINT fk_player_round_round_id FOREIGN KEY (round_id) REFERENCES rounds (round_id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE player_round ADD CONSTRAINT fk_player_round_team_id FOREIGN KEY (team_id) REFERENCES teams (team_id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE popularity_ledger ADD CONSTRAINT fk_popularity_ledger_round_id FOREIGN KEY (round_id) REFERENCES rounds (round_id) ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE popularity_ledger ADD CONSTRAINT fk_popularity_ledger_distribution_batch_id FOREIGN KEY (distribution_batch_id) REFERENCES team_distribution_batches (batch_id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE player_round_stats ADD CONSTRAINT fk_player_round_stats_player_id FOREIGN KEY (player_id) REFERENCES players (player_id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE player_round_stats ADD CONSTRAINT fk_player_round_stats_round_id FOREIGN KEY (round_id) REFERENCES rounds (round_id) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE team_round_stats ADD CONSTRAINT fk_team_round_stats_team_id FOREIGN KEY (team_id) REFERENCES teams (team_id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE team_round_stats ADD CONSTRAINT fk_team_round_stats_round_id FOREIGN KEY (round_id) REFERENCES rounds (round_id) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE pool_round_stats ADD CONSTRAINT fk_pool_round_stats_round_id FOREIGN KEY (round_id) REFERENCES rounds (round_id) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE coefficient_ledger ADD CONSTRAINT fk_coefficient_ledger_player_id FOREIGN KEY (player_id) REFERENCES players (player_id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE coefficient_ledger ADD CONSTRAINT fk_coefficient_ledger_round_id FOREIGN KEY (round_id) REFERENCES rounds (round_id) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE tokens ADD CONSTRAINT fk_tokens_player_id FOREIGN KEY (player_id) REFERENCES players (player_id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE tokens ADD CONSTRAINT fk_tokens_photo_asset_id FOREIGN KEY (photo_asset_id) REFERENCES photo_assets (asset_id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE photo_assets ADD CONSTRAINT fk_photo_assets_player_id FOREIGN KEY (player_id) REFERENCES players (player_id) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE user_photo_collection ADD CONSTRAINT fk_user_photo_collection_asset_id FOREIGN KEY (asset_id) REFERENCES photo_assets (asset_id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE user_photo_collection ADD CONSTRAINT fk_user_photo_collection_token_id FOREIGN KEY (token_id) REFERENCES tokens (token_id) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE user_session ADD CONSTRAINT fk_user_session_user_id FOREIGN KEY (user_id) REFERENCES user_identity (user_id) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE suspicion_votes ADD CONSTRAINT fk_suspicion_votes_round_id FOREIGN KEY (round_id) REFERENCES rounds (round_id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE suspicion_votes ADD CONSTRAINT fk_suspicion_votes_team_id FOREIGN KEY (team_id) REFERENCES teams (team_id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE suspicion_votes ADD CONSTRAINT fk_suspicion_votes_suspect_player_id FOREIGN KEY (suspect_player_id) REFERENCES players (player_id) ON DELETE CASCADE ON UPDATE CASCADE;

CREATE TABLE idempotency_ledger (
  idempotency_key VARCHAR(128) NOT NULL,
  action_type     VARCHAR(50) NOT NULL,
  result_data     VARCHAR(255) NULL,
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (idempotency_key)
);
