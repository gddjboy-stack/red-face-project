# =====================================================================
# ⚠⚠⚠ 已废弃，请勿执行。跑一次即回退测试环境。 ⚠⚠⚠
# =====================================================================
#
# 本脚本曾用于从 db/db_schema.sql（生产 MySQL）单向生成
# src/test/resources/schema-h2.sql（H2 测试库）。
# 此后 schema-h2.sql 被多次手工修补，两者已分叉。
# 2026-08-04 实测：现在执行一次，会造成以下三处回退。
#
# 1) 丢掉 schema-h2.sql 开头 27 行 DROP TABLE IF EXISTS
#    H2 测试库会因不同 Spring 测试上下文重复初始化，必须先按外键反向
#    顺序清表。丢掉后跑多个测试类会撞「表已存在」。
#    —— 注意：该报错看起来像环境问题，不会有人联想到是本脚本造成的，
#      排查成本极高。这是本脚本最危险之处。
#
# 2) 把 TIMESTAMP NOT NULL 改回带 DEFAULT CURRENT_TIMESTAMP
#    现役脚本对 start_time / end_time / occurred_at / expires_at
#    刻意去掉了默认值，本脚本会加回来。
#
# 3) 剥离所有注释
#    用 re.sub 清 COMMENT 时会连 `--` 行注释一并影响，会擦掉
#    C20-4B 事故记录与 C20-4B / C20-6 / C20-10 的设计说明。
#
# 现行做法（Claude 裁定 2026-08-04，DEBT-002）：
#   两份 schema **手工同步**，由 SchemaParityC20Test 校验列级一致性。
#   注意该测试只比对列，不比对索引与唯一约束，后者仍须人工核对。
#
# 若 DEBT-002 重启（出现第三份 schema 或再次不同步故障）：
#   优先修复本脚本使其可用，而非继续手工同步。修复须证明生成结果
#   与现役手工版逐字节一致后才可启用。
# =====================================================================

import sys

print(
    '已废弃：本脚本会回退测试环境（丢 DROP 段 / 改 TIMESTAMP 默认值 / 擦注释）。\n'
    '两份 schema 现行为手工同步，详见本文件头部注释与 DEBT-002。\n'
    '若确认要执行，请先删除下方 sys.exit 并对生成结果做逐字节比对。',
    file=sys.stderr,
)
sys.exit(1)


# --------- 以下为原始逻辑，保留以记录当年的自动化尝试 ---------

from pathlib import Path
import re

root = Path('/home/ubuntu/red-face-project')
source_path = root / 'db' / 'db_schema.sql'
target_path = root / 'backend' / 'redface-backend' / 'src' / 'test' / 'resources' / 'schema-h2.sql'

sql = source_path.read_text(encoding='utf-8')

# H2 测试库只用于单元测试逻辑验证，需去除 MySQL 专用表选项与注释语法。
sql = re.sub(r"\s+COMMENT\s+'(?:[^'\\]|\\.)*'", "", sql)
sql = re.sub(r"\)\s*ENGINE=InnoDB(?:\s+COMMENT='(?:[^'\\]|\\.)*')?\s*;", ");", sql)
sql = sql.replace(' JSON NULL', ' CLOB NULL')
sql = sql.replace('JSON NULL', 'CLOB NULL')
sql = sql.replace('TINYINT(1)', 'TINYINT')

# H2 对 ON UPDATE CURRENT_TIMESTAMP 的兼容性存在差异，测试建表阶段去除该子句。
sql = sql.replace(' ON UPDATE CURRENT_TIMESTAMP', '')

# 保持文件可直接执行。
target_path.parent.mkdir(parents=True, exist_ok=True)
target_path.write_text(sql.strip() + '\n', encoding='utf-8')
