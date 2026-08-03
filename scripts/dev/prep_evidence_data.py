#!/usr/bin/env python3
"""C20-9 取证环境准备：补齐 12 名选手、全部入轮、灌入人气。

之所以要 12 人：Claude 指定的取证项之一是「轮播必须到达末屏」，
6 人一屏放得下不会触发滚动，必须 12 人才能验证第 11、12 名会否出现。
"""
import json
import urllib.request
import random

BASE = "http://localhost:8080"
HDR = {"X-Admin-Token": "train2026", "Content-Type": "application/json"}
ROUND_ID = 1


def call(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, headers=HDR, method=method)
    try:
        with urllib.request.urlopen(req) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        return json.load(e)


def main():
    players = call("GET", "/api/admin/players")["data"]
    existing = {p["number"] for p in players}
    names = {7: "孙七", 8: "周八", 9: "吴九", 10: "郑十", 11: "钱十一", 12: "刘十二"}
    for num, name in names.items():
        if num in existing:
            continue
        r = call("POST", "/api/admin/players",
                 {"name": name, "number": num, "operatorId": "manus"})
        print("create", num, name, r.get("code"), r.get("message"))

    players = call("GET", "/api/admin/players")["data"]
    teams = call("GET", "/api/admin/teams")["data"]
    print("teams:", [(t["teamId"], t["name"]) for t in teams])
    print("players:", len(players))

    # 全部入轮：漏这一步大屏会静默漏人（此前已验证过的坑）
    for p in players:
        team = teams[(p["number"] - 1) % len(teams)]
        r = call("POST", "/api/admin/player-round",
                 {"playerId": p["playerId"], "roundId": ROUND_ID,
                  "teamId": team["teamId"], "operatorId": "manus"})
        print("bind", p["number"], p["name"], "->", team["name"], r.get("code"))

    # 灌人气：用手动调分，金额彼此拉开以便肉眼核对排名与柱长
    random.seed(20260803)
    for p in players:
        val = random.randint(30000, 900000)
        r = call("POST", "/api/admin/popularity/manual-adjust",
                 {"targetType": "player", "targetId": p["playerId"],
                  "roundId": ROUND_ID, "rawValue": val,
                  "reason": "C20-9取证铺底", "operatorId": "manus"})
        print("adjust", p["number"], p["name"], val, r.get("code"), r.get("message", "")[:40])

    board = call("GET", f"/api/display/board?tab=player&roundId={ROUND_ID}")
    rows = (board.get("data") or {}).get("rows", [])
    print("\nboard rows:", len(rows))
    for row in rows:
        print(" ", row.get("rank"), row.get("displayName"), row.get("popularity"))


if __name__ == "__main__":
    main()
