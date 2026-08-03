#!/bin/bash
# 培训演示数据准备脚本（2026-08-02，临时用途）
# 说明：所有请求都带 X-Admin-Token 与 operatorId，模拟真实运营操作。
set -u
API="http://localhost:8080/api/admin"
TK="X-Admin-Token: train2026"
OP="manus-seed"

post() {
  local path="$1"; local body="$2"
  echo "--- POST $path"
  curl -s -X POST "$API$path" -H "$TK" -H "Content-Type: application/json" -d "$body"
  echo
}

put() {
  local path="$1"; local body="$2"
  echo "--- PUT $path"
  curl -s -X PUT "$API$path" -H "$TK" -H "Content-Type: application/json" -d "$body"
  echo
}

echo "===== 1. 创建两支队伍 ====="
post "/teams" "{\"teamId\":1,\"name\":\"红队\",\"operatorId\":\"$OP\"}"
post "/teams" "{\"teamId\":2,\"name\":\"蓝队\",\"operatorId\":\"$OP\"}"

echo "===== 2. 创建六位选手 ====="
post "/players" "{\"playerId\":1,\"name\":\"林一\",\"number\":1,\"displayCode\":\"P01\",\"status\":\"active\",\"operatorId\":\"$OP\"}"
post "/players" "{\"playerId\":2,\"name\":\"周二\",\"number\":2,\"displayCode\":\"P02\",\"status\":\"active\",\"operatorId\":\"$OP\"}"
post "/players" "{\"playerId\":3,\"name\":\"张三\",\"number\":3,\"displayCode\":\"P03\",\"status\":\"active\",\"operatorId\":\"$OP\"}"
post "/players" "{\"playerId\":4,\"name\":\"李四\",\"number\":4,\"displayCode\":\"P04\",\"status\":\"active\",\"operatorId\":\"$OP\"}"
post "/players" "{\"playerId\":5,\"name\":\"王五\",\"number\":5,\"displayCode\":\"P05\",\"status\":\"active\",\"operatorId\":\"$OP\"}"
post "/players" "{\"playerId\":6,\"name\":\"赵六\",\"number\":6,\"displayCode\":\"P06\",\"status\":\"active\",\"operatorId\":\"$OP\"}"

echo "===== 3. 创建一个轮次 ====="
post "/rounds" "{\"roundId\":1,\"name\":\"培训彩排场\",\"startTime\":\"2026-08-02T09:00:00\",\"endTime\":\"2026-08-02T23:00:00\",\"status\":\"upcoming\",\"operatorId\":\"$OP\"}"

echo "===== 4. 选手编入轮次与队伍 ====="
for i in 1 2 3; do
  post "/player-round" "{\"playerId\":$i,\"roundId\":1,\"teamId\":1,\"isSpy\":false,\"playerStatus\":\"normal\",\"operatorId\":\"$OP\"}"
done
for i in 4 5 6; do
  post "/player-round" "{\"playerId\":$i,\"roundId\":1,\"teamId\":2,\"isSpy\":false,\"playerStatus\":\"normal\",\"operatorId\":\"$OP\"}"
done

echo "===== 5. 轮次置为进行中 ====="
put "/rounds/1/status" "{\"status\":\"active\",\"operatorId\":\"$OP\"}"

echo "===== 6. 核对结果 ====="
echo "--- 选手列表"
curl -s -H "$TK" "$API/players"; echo
echo "--- 轮次列表"
curl -s -H "$TK" "$API/rounds"; echo
