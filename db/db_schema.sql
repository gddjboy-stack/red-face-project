-- 选手表(只存固定信息,每轮变化的信息在 player_round)
CREATE TABLE players (
  player_id   INT NOT NULL AUTO_INCREMENT,
  name        VARCHAR(100) NOT NULL COMMENT '选手姓名',
  number      INT NOT NULL COMMENT '选手序号(展示用,如"3号")',
  display_code VARCHAR(20) NULL COMMENT '选手编号(展示与录入)',
  status      VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT 'active/eliminated',
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (player_id),
  UNIQUE KEY uq_number (number),
  UNIQUE KEY uq_display_code (display_code)
) ENGINE=InnoDB COMMENT='选手固定信息表';

CREATE TABLE teams (
  team_id     INT NOT NULL AUTO_INCREMENT,
  name        VARCHAR(100) NOT NULL COMMENT '如 A组',
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (team_id)
) ENGINE=InnoDB COMMENT='团队表';

CREATE TABLE rounds (
  round_id    INT NOT NULL AUTO_INCREMENT,
  name        VARCHAR(100) NOT NULL,
  start_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '轮次开始时间，创建时显式赋值',
  end_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '轮次结束时间，创建时显式赋值',
  status      VARCHAR(20) NOT NULL DEFAULT 'upcoming' COMMENT 'upcoming/active/completed',
  PRIMARY KEY (round_id)
) ENGINE=InnoDB COMMENT='赛事轮次表';

-- 选手每轮关联(团队归属/卧底身份/状态都按轮变化)
CREATE TABLE player_round (
  player_id     INT NOT NULL,
  round_id      INT NOT NULL,
  team_id       INT NULL COMMENT '本轮所属团队',
  is_spy        TINYINT(1) NOT NULL DEFAULT 0 COMMENT '本轮是否卧底',
  spy_status    VARCHAR(20) NULL COMMENT 'hidden/revealed/exposed(被识破)',
  player_status VARCHAR(20) NOT NULL DEFAULT 'normal' COMMENT 'normal/free(自由人)/eliminated',
  PRIMARY KEY (player_id, round_id)
) ENGINE=InnoDB COMMENT='选手每轮的团队/卧底/状态';

-- ★ 人气值流水账(系统唯一真相来源,只增不改)
-- 注意:没有衰减字段!衰减是积分计算时的事,不是流水的事
CREATE TABLE popularity_ledger (
  ledger_id        BIGINT NOT NULL AUTO_INCREMENT,
  target_type      VARCHAR(20) NOT NULL COMMENT 'player/team/spy/pool',
  target_id        INT NULL COMMENT 'pool时可空',
  source           VARCHAR(30) NOT NULL COMMENT 'gift/like/comment/token/manual/refund/team_distribution',
  raw_value        BIGINT NOT NULL COMMENT '原始输入值(抖币数/点赞数等)',
  popularity_value BIGINT NOT NULL COMMENT '换算后的人气值变动(可负)',
  round_id         INT NULL,
  idempotency_key  VARCHAR(128) NOT NULL,
  distribution_batch_id BIGINT NULL COMMENT '团队分配时关联批次',
  operator_id      VARCHAR(64) NULL,
  reason           VARCHAR(500) NULL,
  metadata         JSON NULL,
  occurred_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '流水发生时间',
  created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (ledger_id),
  UNIQUE KEY uq_idem (idempotency_key),
  KEY idx_target (target_type, target_id, round_id)
) ENGINE=InnoDB COMMENT='人气值流水账-唯一真相来源';

-- 选手每轮统计(含加成系数!)
CREATE TABLE player_round_stats (
  player_id             INT NOT NULL,
  round_id              INT NOT NULL,
  individual_popularity BIGINT NOT NULL DEFAULT 0 COMMENT '本轮个人人气值(原始,未衰减)',
  spy_popularity        BIGINT NOT NULL DEFAULT 0 COMMENT '本轮卧底人气值',
  coefficient           INT NOT NULL DEFAULT 100 COMMENT '加成系数×100, 1.0=100',
  updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (player_id, round_id)
) ENGINE=InnoDB COMMENT='选手每轮人气与系数';

CREATE TABLE team_round_stats (
  team_id                INT NOT NULL,
  round_id               INT NOT NULL,
  team_popularity        BIGINT NOT NULL DEFAULT 0 COMMENT '团队池当前人气值',
  coefficient            INT NOT NULL DEFAULT 100 COMMENT '加成系数×100, 1.0=100',
  distributed_popularity BIGINT NOT NULL DEFAULT 0 COMMENT '已分配给成员的累计',
  updated_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (team_id, round_id)
) ENGINE=InnoDB COMMENT='团队每轮人气池';

CREATE TABLE pool_round_stats (
  round_id        INT NOT NULL,
  pool_popularity BIGINT NOT NULL DEFAULT 0,
  updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (round_id)
) ENGINE=InnoDB COMMENT='赛事总人气池';

-- 加成系数变动流水(防任务重复加减)
CREATE TABLE coefficient_ledger (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  player_id       INT NOT NULL,
  round_id        INT NOT NULL,
  task_id         VARCHAR(64) NOT NULL COMMENT '任务唯一标识',
  task_type       VARCHAR(30) NOT NULL COMMENT 'team_task/personal_task/spy_task/pk_win/popularity_king',
  delta           INT NOT NULL COMMENT '±10代表±0.1, pk_win为+5',
  idempotency_key VARCHAR(128) NOT NULL,
  operator_id     VARCHAR(64) NOT NULL,
  reason          VARCHAR(500) NULL,
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_coef_idem (idempotency_key)
) ENGINE=InnoDB COMMENT='加成系数变动流水';

-- 卡密表(补全player_id/points/写真)
CREATE TABLE tokens (
  token_id       VARCHAR(32) NOT NULL COMMENT 'RFZJ-XXXX-XXXX-XXXX',
  player_id      INT NOT NULL COMMENT '绑定选手',
  points         BIGINT NOT NULL COMMENT '核销后增加的人气值,如19900',
  photo_asset_id VARCHAR(64) NULL COMMENT '绑定的数字写真资产',
  product_sku    VARCHAR(64) NULL,
  aqiso_batch_id VARCHAR(64) NULL COMMENT '导出给阿奇索的批次',
  status         VARCHAR(20) NOT NULL DEFAULT 'unused' COMMENT 'unused/used/refunded(C14退款)',
  order_id       VARCHAR(64) NULL,
  user_id        VARCHAR(64) NULL,
  redeem_source  VARCHAR(20) NULL COMMENT 'h5/manual/backend',
  used_at        TIMESTAMP NULL,
  created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (token_id),
  KEY idx_status (status),
  KEY idx_player (player_id)
) ENGINE=InnoDB COMMENT='卡密表';

CREATE TABLE photo_assets (
  asset_id     VARCHAR(64) NOT NULL,
  player_id    INT NOT NULL,
  preview_url  VARCHAR(500) NOT NULL,
  download_url VARCHAR(500) NULL,
  status       VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT 'active/inactive',
  is_cover     TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否选手封面图',
  sort_order   INT NOT NULL DEFAULT 0 COMMENT '同一选手下展示排序',
  file_name    VARCHAR(255) NULL COMMENT '原始文件名，仅记录展示，不用于落盘',
  content_type VARCHAR(100) NULL COMMENT '真实图片 MIME 类型',
  file_size    BIGINT NULL COMMENT '文件大小',
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (asset_id),
  KEY idx_photo_player_status (player_id, status, is_cover, sort_order)
) ENGINE=InnoDB COMMENT='数字写真资产';

-- 用户写真收藏(核销后自动收藏)
CREATE TABLE user_photo_collection (
  id         BIGINT NOT NULL AUTO_INCREMENT,
  user_id    VARCHAR(64) NOT NULL,
  asset_id   VARCHAR(64) NOT NULL,
  token_id   VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_user_token (user_id, token_id)
) ENGINE=InnoDB COMMENT='用户写真收藏';

-- C9 用户身份映射表：openid 不明文落库，只保存 hash 与脱敏 user_id
CREATE TABLE user_identity (
  user_id       VARCHAR(64) NOT NULL COMMENT '脱敏用户标识',
  openid_hash   VARCHAR(128) NOT NULL COMMENT 'openid SHA-256 hash',
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_login_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  UNIQUE KEY uq_openid_hash (openid_hash)
) ENGINE=InnoDB COMMENT='C9用户身份映射';

-- C9 用户会话表：Bearer token 登录态
CREATE TABLE user_session (
  token        VARCHAR(128) NOT NULL,
  user_id      VARCHAR(64) NOT NULL,
  expires_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '会话过期时间，登录时由代码显式写入',
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (token),
  KEY idx_user_session_user (user_id)
) ENGINE=InnoDB COMMENT='C9用户会话';

-- C16 用户会员有效期聚合表：只保存正向叠加后的当前有效期
CREATE TABLE user_membership (
  user_id          VARCHAR(64) NOT NULL COMMENT '脱敏用户标识',
  membership_until TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '当前会员有效期截止时间，核销时显式写入',
  last_token_id    VARCHAR(32) NULL COMMENT '最近一次增加会员的卡密',
  created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  KEY idx_membership_until (membership_until)
) ENGINE=InnoDB COMMENT='C16用户会员有效期聚合表';

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
) ENGINE=InnoDB COMMENT='卧底识破投票';

-- 团队分配批次(只存批次元数据,实际人气变更在ledger里!)
CREATE TABLE team_distribution_batches (
  batch_id       BIGINT NOT NULL AUTO_INCREMENT,
  team_id        INT NOT NULL,
  round_id       INT NOT NULL,
  total_value    BIGINT NOT NULL,
  method         VARCHAR(20) NOT NULL COMMENT 'equal/custom',
  custom_weights JSON NULL,
  operator_id    VARCHAR(64) NOT NULL,
  reason         VARCHAR(500) NULL,
  created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (batch_id)
) ENGINE=InnoDB COMMENT='团队分配批次(明细=ledger中带此batch_id的记录)';

-- 场控状态(当前集赞归属,单行表)
CREATE TABLE collect_state (
  id          INT NOT NULL DEFAULT 1,
  mode        VARCHAR(20) NOT NULL DEFAULT 'pool' COMMENT 'player/team/spy/pool',
  target_id   INT NULL,
  round_id    INT NULL,
  updated_by  VARCHAR(64) NULL,
  updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB COMMENT='当前场控集赞状态(单行)';

-- 操作审计日志(只增)
CREATE TABLE operations_log (
  id          BIGINT NOT NULL AUTO_INCREMENT,
  operator_id VARCHAR(64) NOT NULL,
  action_type VARCHAR(50) NOT NULL,
  target      VARCHAR(100) NULL,
  detail      JSON NULL,
  reason      VARCHAR(500) NULL,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB COMMENT='操作审计日志';

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


-- 幂等控制表(防发码连点等)
CREATE TABLE idempotency_ledger (
  idempotency_key VARCHAR(128) NOT NULL,
  action_type     VARCHAR(50) NOT NULL,
  result_data     VARCHAR(255) NULL,
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (idempotency_key)
) ENGINE=InnoDB COMMENT='幂等控制表';

-- 团队加成系数变动流水
CREATE TABLE team_coefficient_ledger (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  team_id         INT NOT NULL,
  round_id        INT NOT NULL,
  task_id         VARCHAR(64) NOT NULL COMMENT '任务唯一标识',
  task_type       VARCHAR(30) NOT NULL COMMENT 'manual_bonus',
  delta           INT NOT NULL COMMENT '±10代表±0.1',
  idempotency_key VARCHAR(128) NOT NULL,
  operator_id     VARCHAR(64) NOT NULL,
  reason          VARCHAR(500) NULL,
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_team_coef_idem (idempotency_key),
  CONSTRAINT fk_team_coef_ledger_team FOREIGN KEY (team_id) REFERENCES teams (team_id) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_team_coef_ledger_round FOREIGN KEY (round_id) REFERENCES rounds (round_id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB COMMENT='团队加成系数变动流水';

-- C20-3-FIX: 群投票独立账本（票数只判卧底胜负，不折算人气，与popularity_ledger物理隔离）
CREATE TABLE group_vote_ledger (
  entry_id        BIGINT NOT NULL AUTO_INCREMENT,
  round_id        INT NOT NULL,
  player_id       INT NOT NULL,
  votes           BIGINT NOT NULL COMMENT '票数增量(正数累加/负数冲销)',
  idempotency_key VARCHAR(128) NOT NULL,
  operator_id     VARCHAR(64) NOT NULL,
  reason          VARCHAR(500) NOT NULL,
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (entry_id),
  UNIQUE KEY uq_gv_idem (idempotency_key),
  KEY idx_gv_round_player (round_id, player_id)
) ENGINE=InnoDB COMMENT='群投票流水账-独立于人气账本,只用于卧底胜负判定';
