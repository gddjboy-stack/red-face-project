#!/bin/bash
# C20-10 取证脚本（参与人数录入 + 卧底人气系数）
# 前提：后端已以 test profile 启动，ADMIN_TOKEN=train2026 DISPLAY_TOKEN=display2026
# 且已执行 scripts/dev/seed_training_data.sh 建好 6 选手 + 轮次1
set -u
API="http://localhost:8080/api/admin"
TK="X-Admin-Token: train2026"
OP="manus-c20-10"
EV="$(cd "$(dirname "$0")/../.." && pwd)/evidence/C20-10"
mkdir -p "$EV"

post() { curl -s -X POST "$API$1" -H "$TK" -H "Content-Type: application/json" -d "$2"; echo; }
get()  { curl -s "$API$1" -H "$TK"; echo; }

echo "=== 前置：1-3 号设为卧底、录票、灌卧底人气 ==="
for i in 1 2 3; do
  post "/player-round" "{\"playerId\":$i,\"roundId\":1,\"teamId\":1,\"isSpy\":true,\"playerStatus\":\"normal\",\"operatorId\":\"$OP\"}" >/dev/null
done
post "/group-vote/entry" "{\"roundId\":1,\"playerId\":1,\"votes\":45,\"reason\":\"C20-10取证\",\"operatorId\":\"$OP\",\"idempotencyKey\":\"gv_c20_10_1\"}" >/dev/null
post "/group-vote/entry" "{\"roundId\":1,\"playerId\":2,\"votes\":30,\"reason\":\"C20-10取证\",\"operatorId\":\"$OP\",\"idempotencyKey\":\"gv_c20_10_2\"}" >/dev/null
post "/group-vote/entry" "{\"roundId\":1,\"playerId\":3,\"votes\":12,\"reason\":\"C20-10取证\",\"operatorId\":\"$OP\",\"idempotencyKey\":\"gv_c20_10_3\"}" >/dev/null
# 注意 targetType 必须是 spy，写 player 会进 individual_popularity，卧底折算读不到
post "/popularity/manual-adjust" "{\"targetType\":\"spy\",\"targetId\":1,\"roundId\":1,\"rawValue\":205000,\"reason\":\"C20-10取证灌卧底人气\",\"operatorId\":\"$OP\",\"idempotencyKey\":\"spypop_c20_10_1\"}" >/dev/null
echo "前置完成"

{
echo "########## 1. 参与人数未录入：votePercent=null（不是 0） ##########"
get "/group-vote/summary?roundId=1"
echo; echo "########## 2. 查询参与人数：voterCount=null, recorded=false ##########"
get "/voter-count?roundId=1"
echo; echo "########## 3. 首次录入 80 人：直接生效，无需二次确认 ##########"
post "/voter-count/entry" "{\"roundId\":1,\"voterCount\":80,\"reason\":\"群内清点80人参与\",\"operatorId\":\"$OP\",\"confirmed\":false}"
echo; echo "########## 4. 占比以参与人数为分母：45/80=56.3%，三人合计>100%（可多投） ##########"
get "/group-vote/summary?roundId=1"
} 2>&1 | tee "$EV/api_01_voter_count.txt"

{
echo "########## 5. 覆盖已有值 80→95：needs_confirm，此时尚未写入 ##########"
post "/voter-count/entry" "{\"roundId\":1,\"voterCount\":95,\"reason\":\"复核后为95人\",\"operatorId\":\"$OP\",\"confirmed\":false}"
echo; echo "########## 6. 验证上一步确实未落库（仍为 80） ##########"
get "/voter-count?roundId=1"
echo; echo "########## 7. confirmed=true 再提交：写入生效 ##########"
post "/voter-count/entry" "{\"roundId\":1,\"voterCount\":95,\"reason\":\"复核后为95人\",\"operatorId\":\"$OP\",\"confirmed\":true}"
echo; echo "########## 8. 冲突：填 30 < 最高得票 45 → needs_confirm，理由点名冲突对象 ##########"
post "/voter-count/entry" "{\"roundId\":1,\"voterCount\":30,\"reason\":\"误填测试\",\"operatorId\":\"$OP\",\"confirmed\":false}"
echo; echo "########## 9. 强制覆盖冲突值：写入但 forcedOverConflict=true（供审计定位） ##########"
post "/voter-count/entry" "{\"roundId\":1,\"voterCount\":30,\"reason\":\"误填测试-强制\",\"operatorId\":\"$OP\",\"confirmed\":true}"
echo; echo "########## 10. 冲突态下占比 150%，界面须标红而非静默截断 ##########"
get "/group-vote/summary?roundId=1"
echo; echo "########## 11. 修回 95 ##########"
post "/voter-count/entry" "{\"roundId\":1,\"voterCount\":95,\"reason\":\"修正回95人\",\"operatorId\":\"$OP\",\"confirmed\":true}"
} 2>&1 | tee "$EV/api_02_voter_count_confirm.txt"

{
echo "########## 12. 任务加成 ×1.3 ##########"
post "/spy-coefficient/apply" "{\"playerId\":1,\"roundId\":1,\"factor\":130,\"factorType\":\"task_bonus\",\"reason\":\"完成潜伏任务\",\"operatorId\":\"vincent\",\"idempotencyKey\":\"spy_ev_01\"}"
echo; echo "########## 13. 识破减半：×1.3 → ×0.65（乘法叠加） ##########"
echo "###      乘法：205000×0.65=133250"
echo "###      若误用加法(+30%-50%=-20%)：205000×0.8=164000，差 30750"
post "/spy-coefficient/apply" "{\"playerId\":1,\"roundId\":1,\"factor\":50,\"factorType\":\"exposed_halve\",\"reason\":\"被现场识破\",\"operatorId\":\"vincent\",\"idempotencyKey\":\"spy_ev_02\"}"
echo; echo "########## 14. 重复识破（新幂等键）→ rejected，理由含首次时间与操作人 ##########"
post "/spy-coefficient/apply" "{\"playerId\":1,\"roundId\":1,\"factor\":50,\"factorType\":\"exposed_halve\",\"reason\":\"再次识破尝试\",\"operatorId\":\"john\",\"idempotencyKey\":\"spy_ev_03\"}"
echo; echo "########## 15. 同幂等键重提 → duplicated（语义与 rejected 相反：那一笔已生效） ##########"
post "/spy-coefficient/apply" "{\"playerId\":1,\"roundId\":1,\"factor\":50,\"factorType\":\"exposed_halve\",\"reason\":\"被现场识破\",\"operatorId\":\"vincent\",\"idempotencyKey\":\"spy_ev_02\"}"
echo; echo "########## 16. 后台汇总：1号 exposed=true（后台可见） ##########"
get "/group-vote/summary?roundId=1"
} 2>&1 | tee "$EV/api_03_spy_coefficient.txt"

{
echo "########## 17. 回显：裸值 205000 / 折算 133250 / 账本 2 条 ##########"
curl -s "$API/spy-coefficient?playerId=1&roundId=1" -H "$TK" | python3 -m json.tool
echo; echo "########## 18. 撤销识破条目 #2：×0.65 → ×1.3 ##########"
echo "###      实现为「从 ×1 起按剩余未撤销条目重乘」，而非除法回退"
echo "###      因为整数百分比域内 65÷50 不可逆（65/0.5=130 恰好整除是巧合，×1.15 之类会失真）"
post "/spy-coefficient/revoke" "{\"ledgerId\":2,\"playerId\":1,\"roundId\":1,\"reason\":\"识破标记错人，已与现场核对\",\"operatorId\":\"john\"}"
echo; echo "########## 19. 撤销后 exposed=false，折算值回 266500，账本留痕 revoked=true ##########"
curl -s "$API/spy-coefficient?playerId=1&roundId=1" -H "$TK" | python3 -m json.tool
echo; echo "########## 20. 撤销后允许重新施加识破 ##########"
post "/spy-coefficient/apply" "{\"playerId\":1,\"roundId\":1,\"factor\":50,\"factorType\":\"exposed_halve\",\"reason\":\"核对后确认1号确实被识破\",\"operatorId\":\"vincent\",\"idempotencyKey\":\"spy_ev_04\"}"
} 2>&1 | tee "$EV/api_04_spy_revoke.txt"

{
echo "########## 21. 大屏换取展示 Cookie ##########"
curl -s -c /tmp/display_cookie.txt -X POST "http://localhost:8080/api/display-auth/session" \
  -H "Content-Type: application/json" -d '{"token":"display2026"}'; echo
echo; echo "########## 22. 大屏群投票汇总：带 voterCount 与 votePercent ##########"
echo "###      【关键】响应中物理上不含 exposed 字段。"
echo "###      修正前的实现是复用后台 DTO 但不赋值，Jackson 仍会输出 \"exposed\": false，"
echo "###      观众开控制台即可确认系统在跟踪识破状态，属赛制机密泄露。"
curl -s -b /tmp/display_cookie.txt "http://localhost:8080/api/display/group-vote?roundId=1" | python3 -m json.tool
echo; echo "--- 字段级校验：大屏响应中 exposed 出现次数（期望 0） ---"
CNT=$(curl -s -b /tmp/display_cookie.txt "http://localhost:8080/api/display/group-vote?roundId=1" | grep -o exposed | wc -l)
echo "exposed 出现次数 = $CNT"
if [ "$CNT" -eq 0 ]; then echo "PASS：大屏未泄露识破标记"; else echo "FAIL：大屏仍在下发 exposed"; fi
echo; echo "--- 对照：后台同轮次汇总仍含 exposed（后台需要它做标记） ---"
CNT2=$(curl -s "$API/group-vote/summary?roundId=1" -H "$TK" | grep -o '"exposed":true' | wc -l)
echo "后台响应中 exposed=true 出现次数 = $CNT2（期望 1，即 1 号林一）"
} 2>&1 | tee "$EV/api_05_display_no_exposed.txt"

echo
echo "取证文件已写入 $EV"
ls -la "$EV"
