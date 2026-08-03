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
  -- C20-10：可空是刻意的。NULL=尚未录入，0=确实无人投票，两者是不同状态；
  -- 若用 0 代替未录入，识破判定的分母就会静默变成 0，导致任何得票都超过 50%。
  voter_count INT NULL COMMENT '本轮参与投票的独立观众人数(去重人头数,非投票次数)',
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
  -- C20-10：与 coefficient 分列而非复用。两者来源与施加时机不同，
  -- 合并会使「卧底被识破减半」连带减半该选手的个人人气，而那是他去留的依据。
  spy_coefficient       INT NOT NULL DEFAULT 100 COMMENT '卧底人气系数×100, 1.0=100',
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

-- C20-10: 卧底人气系数账本。
-- 刻意不镜像 team_coefficient_ledger 的 delta（增量/加法）语义：
-- 卧底系数按规则是「任务加成 × 识破减半」相乘。若沿用 delta 累加，
-- ×1.3 与 ×0.5 会得到 100+30-50=80，而正确结果是 130×50/100=65；
-- 以基础卧底人气 205000 计，两者差 30750 人气，且都不报错，运营无从判断哪个对。
-- factor 存 100 基数的乘数因子（130 表示 ×1.3，50 表示 ×0.5）；
-- factor_type 区分来源（task_bonus/exposed_halve/manual），以满足界面分项回显。
-- revoked 而非物理删除：系数直接影响选手去留，撤销动作本身必须可追溯。
CREATE TABLE spy_coefficient_ledger (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  player_id       INT NOT NULL,
  round_id        INT NOT NULL,
  factor          INT NOT NULL COMMENT '乘数因子×100，130=×1.3，50=×0.5，不是增量',
  factor_type     VARCHAR(30) NOT NULL COMMENT 'task_bonus/exposed_halve/manual',
  idempotency_key VARCHAR(128) NOT NULL,
  operator_id     VARCHAR(64) NOT NULL,
  reason          VARCHAR(500) NULL,
  revoked         TINYINT(1) NOT NULL DEFAULT 0 COMMENT '撤销标记，不物理删除以保留追溯',
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_spy_coef_idem (idempotency_key),
  KEY idx_spy_coef_round_player (round_id, player_id)
) ENGINE=InnoDB COMMENT='卧底人气系数账本-乘数语义非增量语义';

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

-- C20-4A: 直播数据来源水位线
-- 背景：抖音官方直播中控台只提供「本场直播」的点赞/评论/礼物实时累计数，
-- 不提供跨场次历史累计。每场开播时中控台三个数字都从 0 重新开始。
-- 因此运营录入的是「当前累计总数」，系统需减去上次水位线得到本次增量。
-- 水位线按数据来源维护（全场维度，与选手无关），新场次开播时须校准（归零）。
-- 注意：不建「场次」实体，session_seq 仅为分段标识，写入流水 metadata 用于还原每段计数周期。
CREATE TABLE live_metric_watermark (
  metric_type     VARCHAR(30) NOT NULL COMMENT '数据来源:gift/like_delta/comment_delta',
  last_total      BIGINT NOT NULL DEFAULT 0 COMMENT '上次录入的中控台累计总数',
  session_seq     VARCHAR(40) NOT NULL COMMENT '当前计数周期标识,校准时更新',
  prev_total      BIGINT NULL COMMENT '最近一次校准前的水位线原值,供撤销与人工冲销核算',
  prev_session_seq VARCHAR(40) NULL COMMENT '最近一次校准前的周期标识,供撤销恢复',
  calibrated_at   TIMESTAMP NULL COMMENT '最近一次校准时间',
  entry_count     INT NOT NULL DEFAULT 0 COMMENT '当前周期内已录入次数,为0时允许撤销校准',
  operator_id     VARCHAR(64) NULL COMMENT '最近一次操作人',
  updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (metric_type)
) ENGINE=InnoDB COMMENT='直播数据来源水位线-全场维度,新场次开播须校准';
-- C20-4B: 商品单价配置表
-- 背景：人气按「原价 × 件数」计算（John 2026-08-01 决策）。原价不从订单导出表反推，
-- 因为「订单应付金额」已扣除运费/平台优惠/商家优惠/主播优惠，会让包邮与用券的订单人气缩水。
-- 价格由我方自行定义，上架时（8/7）须同步录入本表，漏录则该商品订单无法换算人气。
-- 单价以「分」存储，全链路整数运算，避免浮点误差（1元=1000人气值 → 1分=10人气值）。
CREATE TABLE product_price_config (
  merchant_code   VARCHAR(64) NOT NULL COMMENT '商家编码,即选手编号如P12(与players.display_code对应)',
  product_name    VARCHAR(200) NOT NULL COMMENT '商品名称,仅供人工核对',
  unit_price_cent BIGINT NOT NULL COMMENT '商品原价(分),如19.9元存1990',
  status          VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT 'active/disabled',
  operator_id     VARCHAR(64) NULL COMMENT '最近一次维护人',
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (merchant_code)
) ENGINE=InnoDB COMMENT='商品原价配置-订单人气换算依据,上架时须同步录入';

-- C20-4B: 订单销量流水账
-- 独立于人气账本(沿用 C20-3-FIX「不同业务来源分表存储」原则)，存已处理订单明细。
-- 幂等键取「子订单编号」而非主订单号：一个主订单含多个子订单(商品维度导出每子订单一行)。
-- 有效性判定为「订单状态 + 售后状态」复合条件，不可用单列筛选——
-- 官方明确「售后关闭不等于售后成功」，售后关闭的订单同样是有效订单。
-- 入账门槛设在「支付完成」(John 2026-08-01 决策)：未支付完成不计人气，防下单不付款刷分。
CREATE TABLE order_sales_ledger (
  entry_id          BIGINT NOT NULL AUTO_INCREMENT,
  sub_order_no      VARCHAR(64) NOT NULL COMMENT '子订单编号,幂等键',
  main_order_no     VARCHAR(64) NULL COMMENT '主订单号,仅供人工追溯,会重复',
  merchant_code     VARCHAR(64) NULL COMMENT '商家编码,选手归属键;为空则无法归属',
  player_id         INT NULL COMMENT '解析出的选手,归属失败时为空',
  quantity          INT NOT NULL DEFAULT 0 COMMENT '商品数量',
  unit_price_cent   BIGINT NULL COMMENT '换算时采用的原价(分),快照留存',
  popularity_value  BIGINT NOT NULL DEFAULT 0 COMMENT '本行折算人气值,无效订单为0',
  order_status      VARCHAR(30) NULL COMMENT '订单状态原文',
  aftersale_status  VARCHAR(30) NULL COMMENT '售后状态原文,空值已归一化',
  validity          VARCHAR(20) NOT NULL COMMENT 'valid/invalid/unattributed',
  invalid_reason    VARCHAR(200) NULL COMMENT '无效或未归属原因',
  in_aftersale      TINYINT NOT NULL DEFAULT 0 COMMENT '1=售后中(计入有效但需单列显示风险敞口)',
  paid_at           TIMESTAMP NULL COMMENT '支付完成时间,场次归属依据',
  payable_amount_cent BIGINT NULL COMMENT '订单应付金额(分),仅作核对,不用于换算',
  round_id          INT NULL COMMENT '入账轮次',
  import_batch_id   VARCHAR(64) NOT NULL COMMENT '导入批次,同一文件一批',
  operator_id       VARCHAR(64) NULL,
  raw_row           JSON NULL COMMENT '原始行快照,供争议追溯',
  created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (entry_id),
  UNIQUE KEY uq_sub_order (sub_order_no),
  KEY idx_osl_batch (import_batch_id),
  KEY idx_osl_player (player_id, round_id)
) ENGINE=InnoDB COMMENT='订单销量流水账-幂等键为子订单编号,人气效果另写popularity_ledger';

-- C20-6: 后台手工销量录入流水账
-- 背景：8/9 首场不使用订单表批量导入（C20-4C 已完成但暂不启用，因 players.display_code
-- 无写入入口，见 collaboration/已知缺陷_display_code无写入入口_V1.0.md）。
-- 改由运营在后台按「选手 + 商品 + 件数」手工录入，选手从下拉框选取，不经过 display_code。
-- 沿用 C20-3-FIX「不同业务来源分表存储」原则，与 order_sales_ledger 物理隔离：
-- 两表口径不同（本表无子订单号、无订单状态、无售后判定），混表会让日后对账无法区分数据来源。
-- quantity 允许负数：负数为冲销纠错，语义与 group_vote_ledger.votes 一致，不做覆盖式修改。
-- unit_price_cent 是录入时刻的价格快照，不是外键引用：product_price_config 的价格可被改动，
-- 若不快照，事后核对会用新价重算出与当时实际入账不符的数字，且无人能解释差异来源。
CREATE TABLE manual_sales_ledger (
  entry_id         BIGINT NOT NULL AUTO_INCREMENT,
  round_id         INT NOT NULL,
  player_id        INT NOT NULL,
  merchant_code    VARCHAR(64) NOT NULL COMMENT '商品编码,对应product_price_config.merchant_code',
  product_name     VARCHAR(100) NULL COMMENT '商品名称快照,仅供核对展示',
  quantity         INT NOT NULL COMMENT '件数增量(正数累加/负数冲销),不可为0',
  unit_price_cent  BIGINT NOT NULL COMMENT '录入时单价快照(分),防事后改价导致追溯不一致',
  popularity_value BIGINT NOT NULL COMMENT '本次折算人气值=单价分×件数×10,负数冲销时为负',
  idempotency_key  VARCHAR(128) NOT NULL,
  operator_id      VARCHAR(64) NOT NULL,
  reason           VARCHAR(500) NOT NULL,
  created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (entry_id),
  UNIQUE KEY uq_msl_idem (idempotency_key),
  KEY idx_msl_round_player (round_id, player_id),
  KEY idx_msl_round_code (round_id, merchant_code)
) ENGINE=InnoDB COMMENT='后台手工销量流水账-人气效果另写popularity_ledger,与订单导入物理隔离';
