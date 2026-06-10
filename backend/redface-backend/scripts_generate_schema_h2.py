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
