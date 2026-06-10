# C1 真实 SHOW TABLES 复核结果

> 来源：`Claude审查_C1真实执行验证.md`。Claude 从 GitHub 拉取 `db/db_schema.sql` 后，在其 Linux 环境中安装 MariaDB 10.11 并真实执行。本文件用于记录第三方审查环境中的真实执行结果，区别于此前 Manus 沙箱中因 MySQL 服务不可用而生成的模拟输出。

```text
Tables_in_redface
coefficient_ledger    collect_state       operations_log
photo_assets          player_round        player_round_stats
players               pool_round_stats    popularity_ledger
rounds                suspicion_votes     team_distribution_batches
team_round_stats      teams               tokens
user_photo_collection
```

结论：真实执行结果共创建 **16 张表**，且未出现 `manual_adjustments`、`team_distribution_details`、`popularity_summary` 三张废弃表。
