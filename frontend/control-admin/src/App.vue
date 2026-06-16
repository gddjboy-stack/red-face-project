<template>
  <main class="admin-shell">
    <el-card class="header-card">
      <div class="header-content">
        <div>
          <h1 class="header-title">红颜局中局 · 彩排运营后台</h1>
          <p class="header-desc">C10 场控台 + C19 基础数据管理。所有写操作必须带 operatorId 并进入操作日志。</p>
        </div>
        <div class="operator-box">
          <el-input v-model="operatorId" placeholder="输入操作员 ID，例如 john/director" @change="saveOperator">
            <template #prepend>operatorId</template>
          </el-input>
          <p class="tip">彩排版临时鉴权：顶部输入一次，后续请求自动携带。上线前由 C18 替换为正式权限。</p>
        </div>
      </div>
    </el-card>

    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="场控监控" name="monitor">
        <div class="grid-two">
          <el-card class="panel-card">
            <div class="panel-title">直播状态</div>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="状态">{{ home.liveStatus || '-' }}</el-descriptions-item>
              <el-descriptions-item label="轮次">{{ home.roundName || home.roundId || '-' }}</el-descriptions-item>
              <el-descriptions-item label="当前模式">{{ home.currentMode || '-' }}</el-descriptions-item>
              <el-descriptions-item label="目标">{{ home.targetDisplayName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="目标人气"><span class="metric-number">{{ home.targetPopularity ?? 0 }}</span></el-descriptions-item>
              <el-descriptions-item label="团队">{{ home.teamDisplayName || '-' }} / {{ home.teamPopularity ?? 0 }}</el-descriptions-item>
            </el-descriptions>
            <template #footer>
              <el-button type="primary" @click="refreshMonitor">刷新监控</el-button>
            </template>
          </el-card>

          <el-card class="panel-card">
            <div class="panel-title">人气看板</div>
            <el-form inline>
              <el-form-item label="Tab">
                <el-select v-model="boardTab" style="width: 140px" @change="refreshBoard">
                  <el-option label="个人" value="player" />
                  <el-option label="团队" value="team" />
                  <el-option label="卧底" value="spy" />
                </el-select>
              </el-form-item>
              <el-form-item label="roundId">
                <el-input-number v-model="boardRoundId" :min="1" />
              </el-form-item>
              <el-form-item>
                <el-button @click="refreshBoard">查询</el-button>
              </el-form-item>
            </el-form>
            <p class="tip">合规要求：后台只展示后端返回顺序，不按人气值重排。</p>
            <el-table :data="board.items || []" size="small" height="300">
              <el-table-column prop="number" label="序号/团队ID" width="120" />
              <el-table-column prop="name" label="名称" />
              <el-table-column prop="teamName" label="队伍" />
              <el-table-column prop="value" label="人气值" />
            </el-table>
          </el-card>
        </div>
      </el-tab-pane>

      <el-tab-pane label="场控操作" name="control">
        <div class="grid-two">
          <el-card class="panel-card">
            <div class="panel-title">集赞目标切换</div>
            <el-form label-width="100px">
              <el-form-item label="模式">
                <el-select v-model="collectForm.mode">
                  <el-option label="选手 player" value="player" />
                  <el-option label="团队 team" value="team" />
                  <el-option label="卧底 spy" value="spy" />
                  <el-option label="总池 pool" value="pool" />
                </el-select>
              </el-form-item>
              <el-form-item label="目标 ID">
                <el-input-number v-model="collectForm.targetId" :min="1" :disabled="collectForm.mode === 'pool'" />
              </el-form-item>
              <el-form-item label="轮次 ID">
                <el-input-number v-model="collectForm.roundId" :min="1" />
              </el-form-item>
              <div class="form-actions">
                <el-button type="primary" @click="submitCollectState">确认切换</el-button>
              </div>
            </el-form>
          </el-card>

          <el-card class="panel-card">
            <div class="panel-title">模拟注入</div>
            <el-form label-width="100px">
              <el-form-item label="事件类型">
                <el-select v-model="simulateForm.eventType">
                  <el-option label="礼物 gift" value="gift" />
                  <el-option label="点赞 like_delta" value="like_delta" />
                  <el-option label="留言 comment_delta" value="comment_delta" />
                </el-select>
              </el-form-item>
              <el-form-item label="数值">
                <el-input-number v-model="simulateForm.value" :min="1" />
              </el-form-item>
              <el-form-item label="目标选手">
                <el-input-number v-model="simulateForm.targetId" :min="1" />
              </el-form-item>
              <p class="tip">gift 建议填写目标选手；like/comment 会按当前场控目标自动归属。</p>
              <div class="form-actions">
                <el-button type="primary" @click="submitSimulate">注入并刷新</el-button>
              </div>
            </el-form>
          </el-card>

          <el-card class="panel-card">
            <div class="panel-title">手动调分</div>
            <el-form label-width="100px">
              <el-form-item label="目标类型">
                <el-select v-model="manualForm.targetType">
                  <el-option label="选手 player" value="player" />
                  <el-option label="团队 team" value="team" />
                  <el-option label="卧底 spy" value="spy" />
                  <el-option label="总池 pool" value="pool" />
                </el-select>
              </el-form-item>
              <el-form-item label="目标 ID">
                <el-input-number v-model="manualForm.targetId" :min="1" :disabled="manualForm.targetType === 'pool'" />
              </el-form-item>
              <el-form-item label="轮次 ID"><el-input-number v-model="manualForm.roundId" :min="1" /></el-form-item>
              <el-form-item label="人气变动"><el-input-number v-model="manualForm.rawValue" /></el-form-item>
              <el-form-item label="原因"><el-input v-model="manualForm.reason" /></el-form-item>
              <div class="form-actions">
                <el-button type="warning" @click="submitManualAdjust">确认调分</el-button>
              </div>
            </el-form>
          </el-card>

          <el-card class="panel-card">
            <div class="panel-title">团队人气均分</div>
            <el-form label-width="100px">
              <el-form-item label="团队 ID"><el-input-number v-model="distributionForm.teamId" :min="1" /></el-form-item>
              <el-form-item label="轮次 ID"><el-input-number v-model="distributionForm.roundId" :min="1" /></el-form-item>
              <el-form-item label="方式"><el-tag type="success">equal 均分</el-tag></el-form-item>
              <el-form-item label="原因"><el-input v-model="distributionForm.reason" /></el-form-item>
              <p class="tip warning-text">P0 只做 equal。点击后会把团队池当前余额分配给成员，请确认直播流程。</p>
              <div class="form-actions">
                <el-button type="danger" @click="submitDistribution">确认均分</el-button>
              </div>
            </el-form>
          </el-card>
        </div>
      </el-tab-pane>

      <el-tab-pane label="基础数据" name="basic">
        <div class="grid-two">
          <el-card class="panel-card">
            <div class="panel-title">选手管理</div>
            <el-form inline>
              <el-form-item label="姓名"><el-input v-model="playerForm.name" /></el-form-item>
              <el-form-item label="序号"><el-input-number v-model="playerForm.number" :min="1" /></el-form-item>
              <el-form-item><el-button type="primary" @click="submitPlayer">新增选手</el-button></el-form-item>
            </el-form>
            <el-table :data="players" size="small" height="260">
              <el-table-column prop="number" label="序号" width="80" />
              <el-table-column prop="name" label="姓名" />
              <el-table-column prop="status" label="状态" />
              <el-table-column prop="playerId" label="ID" width="80" />
            </el-table>
          </el-card>

          <el-card class="panel-card">
            <div class="panel-title">队伍管理</div>
            <el-form inline>
              <el-form-item label="队名"><el-input v-model="teamForm.name" /></el-form-item>
              <el-form-item><el-button type="primary" @click="submitTeam">新增队伍</el-button></el-form-item>
            </el-form>
            <el-table :data="teams" size="small" height="260">
              <el-table-column prop="teamId" label="ID" width="80" />
              <el-table-column prop="name" label="队名" />
            </el-table>
          </el-card>

          <el-card class="panel-card">
            <div class="panel-title">轮次管理</div>
            <el-form label-width="90px">
              <el-form-item label="名称"><el-input v-model="roundForm.name" /></el-form-item>
              <el-form-item label="开始时间"><el-date-picker v-model="roundForm.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
              <el-form-item label="结束时间"><el-date-picker v-model="roundForm.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
              <el-form-item label="状态">
                <el-select v-model="roundForm.status">
                  <el-option label="upcoming" value="upcoming" />
                  <el-option label="active" value="active" />
                  <el-option label="completed" value="completed" />
                </el-select>
              </el-form-item>
              <div class="form-actions"><el-button type="primary" @click="submitRound">新增轮次</el-button></div>
            </el-form>
            <el-table :data="rounds" size="small" height="260">
              <el-table-column prop="roundId" label="ID" width="70" />
              <el-table-column prop="name" label="名称" />
              <el-table-column prop="status" label="状态" width="110" />
              <el-table-column label="操作" width="130">
                <template #default="scope">
                  <el-button size="small" type="warning" @click="activateRound(scope.row)">设为 active</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <el-card class="panel-card">
            <div class="panel-title">分队与卧底设置</div>
            <el-form inline>
              <el-form-item label="轮次 ID"><el-input-number v-model="playerRoundFilterRoundId" :min="1" /></el-form-item>
              <el-form-item><el-button @click="refreshPlayerRounds">查询</el-button></el-form-item>
            </el-form>
            <el-form label-width="90px">
              <el-form-item label="选手 ID"><el-input-number v-model="playerRoundForm.playerId" :min="1" /></el-form-item>
              <el-form-item label="队伍 ID"><el-input-number v-model="playerRoundForm.teamId" :min="1" /></el-form-item>
              <el-form-item label="是否卧底"><el-switch v-model="playerRoundForm.isSpy" /></el-form-item>
              <el-form-item label="状态">
                <el-select v-model="playerRoundForm.playerStatus">
                  <el-option label="normal" value="normal" />
                  <el-option label="free" value="free" />
                  <el-option label="eliminated" value="eliminated" />
                </el-select>
              </el-form-item>
              <div class="form-actions"><el-button type="primary" @click="submitPlayerRound">保存分队</el-button></div>
            </el-form>
            <el-table :data="playerRounds" size="small" height="240">
              <el-table-column prop="number" label="序号" width="70" />
              <el-table-column prop="playerName" label="选手" />
              <el-table-column prop="teamName" label="队伍" />
              <el-table-column prop="isSpy" label="卧底" />
              <el-table-column prop="playerStatus" label="状态" />
            </el-table>
          </el-card>
        </div>
      </el-tab-pane>
    </el-tabs>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { distributeTeam, getAdminBoard, getAdminHome, manualAdjust, setCollectState, simulateInject } from './api/admin'
import { createPlayer, createRound, createTeam, listPlayerRounds, listPlayers, listRounds, listTeams, savePlayerRound, updateRoundStatus } from './api/basicData'

const activeTab = ref('monitor')
const operatorId = ref(localStorage.getItem('operatorId') || 'director')
const home = ref<any>({})
const board = ref<any>({ items: [] })
const boardTab = ref('player')
const boardRoundId = ref(1)
const players = ref<any[]>([])
const teams = ref<any[]>([])
const rounds = ref<any[]>([])
const playerRounds = ref<any[]>([])
const playerRoundFilterRoundId = ref(1)

const collectForm = reactive({ mode: 'player', targetId: 1, roundId: 1 })
const simulateForm = reactive({ eventType: 'like_delta', value: 10, targetId: 1 })
const manualForm = reactive({ targetType: 'player', targetId: 1, roundId: 1, rawValue: 100, reason: '彩排手动调分' })
const distributionForm = reactive({ teamId: 1, roundId: 1, method: 'equal', reason: '彩排团队均分' })
const playerForm = reactive({ name: '', number: 1 })
const teamForm = reactive({ name: '' })
const roundForm = reactive({ name: '', startTime: '', endTime: '', status: 'upcoming' })
const playerRoundForm = reactive({ playerId: 1, teamId: 1, isSpy: false, playerStatus: 'normal' })

function saveOperator() {
  localStorage.setItem('operatorId', operatorId.value)
}

function withOperator<T extends Record<string, any>>(data: T): T & { operatorId: string } {
  return { ...data, operatorId: operatorId.value }
}

async function runAction(message: string, action: () => Promise<any>, after?: () => Promise<void>) {
  try {
    await action()
    ElMessage.success(message)
    if (after) await after()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

async function refreshMonitor() {
  home.value = await getAdminHome()
  await refreshBoard()
}

async function refreshBoard() {
  board.value = await getAdminBoard(boardTab.value, boardRoundId.value)
}

async function refreshBasicData() {
  players.value = await listPlayers()
  teams.value = await listTeams()
  rounds.value = await listRounds()
}

async function refreshPlayerRounds() {
  playerRounds.value = await listPlayerRounds(playerRoundFilterRoundId.value)
}

async function submitCollectState() {
  await ElMessageBox.confirm('确认切换当前集赞目标？该操作会影响点赞/留言归属。', '二次确认', { type: 'warning' })
  const payload = { ...collectForm, targetId: collectForm.mode === 'pool' ? null : collectForm.targetId }
  await runAction('集赞目标已切换', () => setCollectState(withOperator(payload)), refreshMonitor)
}

async function submitSimulate() {
  await runAction('模拟注入成功', () => simulateInject(withOperator(simulateForm)), refreshMonitor)
}

async function submitManualAdjust() {
  await ElMessageBox.confirm('确认执行手动调分？该操作会写入人气流水与审计日志。', '二次确认', { type: 'warning' })
  const payload = { ...manualForm, targetId: manualForm.targetType === 'pool' ? null : manualForm.targetId }
  await runAction('手动调分成功', () => manualAdjust(withOperator(payload)), refreshMonitor)
}

async function submitDistribution() {
  await ElMessageBox.confirm('确认将团队池当前余额均分给团队成员？', '二次确认', { type: 'warning' })
  await runAction('团队人气均分成功', () => distributeTeam(withOperator(distributionForm)), refreshMonitor)
}

async function submitPlayer() {
  await runAction('选手已新增', () => createPlayer(withOperator(playerForm)), refreshBasicData)
}

async function submitTeam() {
  await runAction('队伍已新增', () => createTeam(withOperator(teamForm)), refreshBasicData)
}

async function submitRound() {
  await runAction('轮次已新增', () => createRound(withOperator(roundForm)), refreshBasicData)
}

async function activateRound(row: any) {
  await ElMessageBox.confirm(`这会把当前进行中的轮次标记为已结束，并将【${row.name}】设为 active。确认继续？`, '切换 active 轮次', { type: 'warning' })
  await runAction('轮次已切换为 active', () => updateRoundStatus(row.roundId, withOperator({ status: 'active' })), refreshBasicData)
}

async function submitPlayerRound() {
  const payload = { ...playerRoundForm, roundId: playerRoundFilterRoundId.value }
  await runAction('分队信息已保存', () => savePlayerRound(withOperator(payload)), refreshPlayerRounds)
}

onMounted(async () => {
  saveOperator()
  await refreshBasicData()
  await refreshMonitor()
})
</script>
