```sql
-- 红颜局中局小程序数据库 Schema (V1.0)
-- 日期: 2026-06-09
-- 作者: Manus AI

-- -----------------------------------------------------
-- Table `players` 选手表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `players` (
  `player_id` INT NOT NULL AUTO_INCREMENT COMMENT '选手ID',
  `name` VARCHAR(255) NOT NULL COMMENT '选手姓名',
  `team_id` INT NULL COMMENT '所属团队ID，可为空（未分组）',
  `is_spy` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为卧底 (0:否, 1:是)',
  `status` VARCHAR(50) NOT NULL DEFAULT 'active' COMMENT '选手状态 (active, eliminated, etc.)',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`player_id`)
) ENGINE = InnoDB COMMENT = '选手信息表';

-- -----------------------------------------------------
-- Table `teams` 团队表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `teams` (
  `team_id` INT NOT NULL AUTO_INCREMENT COMMENT '团队ID',
  `name` VARCHAR(255) NOT NULL COMMENT '团队名称',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`team_id`)
) ENGINE = InnoDB COMMENT = '团队信息表';

-- -----------------------------------------------------
-- Table `popularity_ledger` 人气值流水表 (只增不改)
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `popularity_ledger` (
  `ledger_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `target_type` VARCHAR(50) NOT NULL COMMENT '目标类型 (player, team, spy, pool)',
  `target_id` INT NULL COMMENT '目标ID (player_id, team_id)，pool类型可为空',
  `source` VARCHAR(50) NOT NULL COMMENT '来源 (gift, like, comment, token, manual, refund, team_distribution)',
  `raw_value` BIGINT NOT NULL COMMENT '原始人气值变动（正负）',
  `effective_value` BIGINT NOT NULL COMMENT '实际生效人气值（考虑衰减等规则）',
  `occurred_at` TIMESTAMP NOT NULL COMMENT '发生时间',
  `idempotency_key` VARCHAR(255) NOT NULL COMMENT '幂等键，防止重复提交',
  `round_id` INT NULL COMMENT '轮次ID',
  `operator_id` VARCHAR(255) NULL COMMENT '操作人ID (manual, team_distribution)',
  `reason` VARCHAR(500) NULL COMMENT '操作原因/备注',
  `metadata` JSON NULL COMMENT '额外元数据 (如礼物ID, 评论内容等)',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`ledger_id`),
  UNIQUE INDEX `uq_idempotency_key` (`idempotency_key` ASC)
) ENGINE = InnoDB COMMENT = '人气值变动流水表';

-- -----------------------------------------------------
-- Table `popularity_summary` 人气值汇总表 (实时更新)
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `popularity_summary` (
  `summary_id` INT NOT NULL AUTO_INCREMENT COMMENT '汇总ID',
  `target_type` VARCHAR(50) NOT NULL COMMENT '目标类型 (player, team, spy, pool)',
  `target_id` INT NULL COMMENT '目标ID (player_id, team_id)，pool类型可为空',
  `current_popularity` BIGINT NOT NULL DEFAULT 0 COMMENT '当前总人气值',
  `last_round_popularity` BIGINT NOT NULL DEFAULT 0 COMMENT '上一轮次总人气值（用于衰减计算）',
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`summary_id`),
  UNIQUE INDEX `uq_target_type_id` (`target_type` ASC, `target_id` ASC)
) ENGINE = InnoDB COMMENT = '人气值汇总表';

-- -----------------------------------------------------
-- Table `tokens` 卡密表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `tokens` (
  `token_id` VARCHAR(255) NOT NULL COMMENT '卡密ID (RFZJ-XXXX-XXXX-XXXX)',
  `product_sku` VARCHAR(255) NOT NULL COMMENT '关联商品SKU',
  `status` VARCHAR(50) NOT NULL DEFAULT 'unused' COMMENT '卡密状态 (unused, used, expired)',
  `order_id` VARCHAR(255) NULL COMMENT '关联订单ID',
  `user_id` VARCHAR(255) NULL COMMENT '核销用户ID',
  `used_at` TIMESTAMP NULL COMMENT '核销时间',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`token_id`),
  INDEX `idx_status` (`status` ASC)
) ENGINE = InnoDB COMMENT = '卡密信息表';

-- -----------------------------------------------------
-- Table `rounds` 轮次表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `rounds` (
  `round_id` INT NOT NULL AUTO_INCREMENT COMMENT '轮次ID',
  `name` VARCHAR(255) NOT NULL COMMENT '轮次名称',
  `start_time` TIMESTAMP NOT NULL COMMENT '开始时间',
  `end_time` TIMESTAMP NOT NULL COMMENT '结束时间',
  `status` VARCHAR(50) NOT NULL DEFAULT 'upcoming' COMMENT '轮次状态 (upcoming, active, completed)',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`round_id`)
) ENGINE = InnoDB COMMENT = '赛事轮次表';

-- -----------------------------------------------------
-- Table `suspicion_votes` 卧底投票表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `suspicion_votes` (
  `vote_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '投票ID',
  `user_id` VARCHAR(255) NOT NULL COMMENT '投票用户ID',
  `round_id` INT NOT NULL COMMENT '投票轮次ID',
  `team_id` INT NOT NULL COMMENT '被投票选手所属团队ID',
  `suspect_player_id` INT NOT NULL COMMENT '被投票的嫌疑卧底选手ID',
  `voted_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投票时间',
  PRIMARY KEY (`vote_id`),
  UNIQUE INDEX `uq_user_round_suspect` (`user_id` ASC, `round_id` ASC, `suspect_player_id` ASC) COMMENT '确保同一用户同一轮次不能重复投同一选手',
  INDEX `idx_round_team_suspect` (`round_id` ASC, `team_id` ASC, `suspect_player_id` ASC)
) ENGINE = InnoDB COMMENT = '卧底投票记录表';

-- -----------------------------------------------------
-- Table `manual_adjustments` 手动调整记录表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `manual_adjustments` (
  `adjustment_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '调整ID',
  `target_type` VARCHAR(50) NOT NULL COMMENT '目标类型 (player, team, spy, pool)',
  `target_id` INT NULL COMMENT '目标ID',
  `adjustment_value` BIGINT NOT NULL COMMENT '调整值（正负）',
  `operator_id` VARCHAR(255) NOT NULL COMMENT '操作人ID',
  `reason` VARCHAR(500) NOT NULL COMMENT '调整原因/备注',
  `occurred_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调整时间',
  PRIMARY KEY (`adjustment_id`)
) ENGINE = InnoDB COMMENT = '人气值手动调整记录表';

-- -----------------------------------------------------
-- Table `team_distribution_batches` 团队人气分配批次表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `team_distribution_batches` (
  `batch_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '批次ID',
  `team_id` INT NOT NULL COMMENT '团队ID',
  `round_id` INT NOT NULL COMMENT '轮次ID',
  `total_distributed_popularity` BIGINT NOT NULL COMMENT '本次分配的总人气值',
  `method` VARCHAR(50) NOT NULL COMMENT '分配方式 (equal, custom)',
  `operator_id` VARCHAR(255) NOT NULL COMMENT '操作人ID',
  `reason` VARCHAR(500) NULL COMMENT '分配原因/备注',
  `custom_weights` JSON NULL COMMENT '自定义权重 (JSON格式: {player_id: weight})',
  `distributed_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '分配时间',
  PRIMARY KEY (`batch_id`)
) ENGINE = InnoDB COMMENT = '团队人气分配批次表';

-- -----------------------------------------------------
-- Table `team_distribution_details` 团队人气分配明细表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `team_distribution_details` (
  `detail_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `batch_id` BIGINT NOT NULL COMMENT '批次ID',
  `player_id` INT NOT NULL COMMENT '选手ID',
  `distributed_value` BIGINT NOT NULL COMMENT '分配到该选手的人气值',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`detail_id`),
  INDEX `idx_batch_player` (`batch_id` ASC, `player_id` ASC)
) ENGINE = InnoDB COMMENT = '团队人气分配明细表';

-- -----------------------------------------------------
-- Foreign Key Constraints
-- -----------------------------------------------------
ALTER TABLE `players`
  ADD CONSTRAINT `fk_players_teams`
  FOREIGN KEY (`team_id`)
  REFERENCES `teams` (`team_id`)
  ON DELETE SET NULL
  ON UPDATE CASCADE;

ALTER TABLE `popularity_ledger`
  ADD CONSTRAINT `fk_popularity_ledger_rounds`
  FOREIGN KEY (`round_id`)
  REFERENCES `rounds` (`round_id`)
  ON DELETE SET NULL
  ON UPDATE CASCADE;

ALTER TABLE `suspicion_votes`
  ADD CONSTRAINT `fk_suspicion_votes_players`
  FOREIGN KEY (`suspect_player_id`)
  REFERENCES `players` (`player_id`)
  ON DELETE CASCADE
  ON UPDATE CASCADE;

ALTER TABLE `suspicion_votes`
  ADD CONSTRAINT `fk_suspicion_votes_rounds`
  FOREIGN KEY (`round_id`)
  REFERENCES `rounds` (`round_id`)
  ON DELETE CASCADE
  ON UPDATE CASCADE;

ALTER TABLE `suspicion_votes`
  ADD CONSTRAINT `fk_suspicion_votes_teams`
  FOREIGN KEY (`team_id`)
  REFERENCES `teams` (`team_id`)
  ON DELETE CASCADE
  ON UPDATE CASCADE;

ALTER TABLE `team_distribution_batches`
  ADD CONSTRAINT `fk_team_distribution_batches_teams`
  FOREIGN KEY (`team_id`)
  REFERENCES `teams` (`team_id`)
  ON DELETE CASCADE
  ON UPDATE CASCADE;

ALTER TABLE `team_distribution_batches`
  ADD CONSTRAINT `fk_team_distribution_batches_rounds`
  FOREIGN KEY (`round_id`)
  REFERENCES `rounds` (`round_id`)
  ON DELETE CASCADE
  ON UPDATE CASCADE;

ALTER TABLE `team_distribution_details`
  ADD CONSTRAINT `fk_team_distribution_details_batches`
  FOREIGN KEY (`batch_id`)
  REFERENCES `team_distribution_batches` (`batch_id`)
  ON DELETE CASCADE
  ON UPDATE CASCADE;

ALTER TABLE `team_distribution_details`
  ADD CONSTRAINT `fk_team_distribution_details_players`
  FOREIGN KEY (`player_id`)
  REFERENCES `players` (`player_id`)
  ON DELETE CASCADE
  ON UPDATE CASCADE;

```
