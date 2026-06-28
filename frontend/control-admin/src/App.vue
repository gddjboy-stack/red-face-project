<template>
  <main class="admin-shell" @submit.prevent>
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
          <el-input
            v-model="adminToken"
            type="password"
            show-password
            placeholder="输入管理口令 ADMIN_TOKEN"
            @change="saveAdminTokenValue"
          >
            <template #prepend>管理口令</template>
          </el-input>
          <p class="tip">管理口令：由运维私密下发，顶部输入一次即可，仅存本地、不进代码。口令无效会提示重输。</p>
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
              <el-button native-type="button" type="primary" @click="refreshMonitor">刷新监控</el-button>
            </template>
          </el-card>

          <el-card class="panel-card">
            <div class="panel-title">人气看板</div>
            <el-form @submit.prevent inline>
              <el-form-item label="Tab">
                <el-select v-model="boardTab" style="width: 140px" @change="refreshBoard">
                  <el-option label="个人" value="player" />
                  <el-option label="团队" value="team" />
                  <el-option label="卧底" value="spy" />
                </el-select>
              </el-form-item>
              <el-form-item label="roundId">
                <el-select v-model="boardRoundId" filterable placeholder="请选择轮次" style="width: 180px" @change="refreshBoard">
                  <el-option v-for="r in rounds" :key="r.roundId" :label="`[${r.roundId}] ${r.name}`" :value="r.roundId" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button native-type="button" @click="refreshBoard">查询</el-button>
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

          <el-card class="panel-card">
            <div class="panel-title">真相识破监控</div>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="状态">{{ suspicionStatus.open ? '进行中' : '暂未开启' }}</el-descriptions-item>
              <el-descriptions-item label="轮次">{{ suspicionStatus.roundName || suspicionStatus.roundId || '-' }}</el-descriptions-item>
            </el-descriptions>
            <p class="tip">只展示判断分布，按选手序号排列，不展示真实卧底身份。</p>
            <el-table :data="suspicionStatus.candidates || []" size="small" height="260">
              <el-table-column prop="number" label="序号" width="80" />
              <el-table-column prop="playerName" label="选手" />
              <el-table-column prop="teamName" label="队伍" />
              <el-table-column prop="count" label="判断次数" />
            </el-table>
          </el-card>
        </div>
      </el-tab-pane>

      <el-tab-pane label="场控操作" name="control">
        <div class="grid-two">
          <el-card class="panel-card">
            <div class="panel-title">集赞目标切换</div>
            <el-form @submit.prevent label-width="100px">
              <el-form-item label="模式">
                <el-select v-model="collectForm.mode" @change="onCollectModeChange">
                  <el-option label="选手 player" value="player" />
                  <el-option label="团队 team" value="team" />
                  <el-option label="卧底 spy" value="spy" />
                  <el-option label="总池 pool" value="pool" />
                </el-select>
              </el-form-item>
              <el-form-item label="目标">
                <el-select v-model="collectForm.targetId" :disabled="collectForm.mode === 'pool'" filterable clearable placeholder="请选择目标" style="width: 220px">
                  <template v-if="collectForm.mode === 'player' || collectForm.mode === 'spy'">
                    <el-option v-for="p in players" :key="p.playerId" :label="`${p.number}号 ${p.name}`" :value="p.playerId" />
                  </template>
                  <template v-else-if="collectForm.mode === 'team'">
                    <el-option v-for="t in teams" :key="t.teamId" :label="t.name" :value="t.teamId" />
                  </template>
                </el-select>
              </el-form-item>
              <el-form-item label="轮次">
                <el-select v-model="collectForm.roundId" filterable placeholder="请选择轮次" style="width: 220px">
                  <el-option v-for="r in rounds" :key="r.roundId" :label="`[${r.roundId}] ${r.name}`" :value="r.roundId" />
                </el-select>
              </el-form-item>
              <div class="form-actions">
                <el-button native-type="button" type="primary" @click="submitCollectState">确认切换</el-button>
              </div>
            </el-form>
          </el-card>

          <el-card class="panel-card">
            <div class="panel-title">模拟注入</div>
            <el-form @submit.prevent label-width="100px">
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
                <el-select v-model="simulateForm.targetId" filterable clearable placeholder="请选择选手" style="width: 220px">
                  <el-option v-for="p in players" :key="p.playerId" :label="`${p.number}号 ${p.name}`" :value="p.playerId" />
                </el-select>
              </el-form-item>
              <p class="tip">gift 建议填写目标选手；like/comment 会按当前场控目标自动归属。</p>
              <div class="form-actions">
                <el-button native-type="button" type="primary" @click="submitSimulate">注入并刷新</el-button>
              </div>
            </el-form>
          </el-card>

          <el-card class="panel-card">
            <div class="panel-title">手动调分</div>
            <el-form @submit.prevent label-width="100px">
              <el-form-item label="目标类型">
                <el-select v-model="manualForm.targetType" @change="onManualTypeChange">
                  <el-option label="选手 player" value="player" />
                  <el-option label="团队 team" value="team" />
                  <el-option label="卧底 spy" value="spy" />
                  <el-option label="总池 pool" value="pool" />
                </el-select>
              </el-form-item>
              <el-form-item label="目标">
                <el-select v-model="manualForm.targetId" :disabled="manualForm.targetType === 'pool'" filterable clearable placeholder="请选择目标" style="width: 220px">
                  <template v-if="manualForm.targetType === 'player' || manualForm.targetType === 'spy'">
                    <el-option v-for="p in players" :key="p.playerId" :label="`${p.number}号 ${p.name}`" :value="p.playerId" />
                  </template>
                  <template v-else-if="manualForm.targetType === 'team'">
                    <el-option v-for="t in teams" :key="t.teamId" :label="t.name" :value="t.teamId" />
                  </template>
                </el-select>
              </el-form-item>
              <el-form-item label="轮次"><el-select v-model="manualForm.roundId" filterable placeholder="请选择轮次" style="width: 220px"><el-option v-for="r in rounds" :key="r.roundId" :label="`[${r.roundId}] ${r.name}`" :value="r.roundId" /></el-select></el-form-item>
              <el-form-item label="人气变动"><el-input-number v-model="manualForm.rawValue" /></el-form-item>
              <el-form-item label="原因"><el-input v-model="manualForm.reason" /></el-form-item>
              <div class="form-actions">
                <el-button native-type="button" type="warning" @click="submitManualAdjust">确认调分</el-button>
              </div>
            </el-form>
          </el-card>

          <el-card class="panel-card">
            <div class="panel-title">团队人气均分</div>
            <el-form @submit.prevent label-width="100px">
              <el-form-item label="团队"><el-select v-model="distributionForm.teamId" filterable clearable placeholder="请选择团队" style="width: 220px"><el-option v-for="t in teams" :key="t.teamId" :label="t.name" :value="t.teamId" /></el-select></el-form-item>
              <el-form-item label="轮次"><el-select v-model="distributionForm.roundId" filterable placeholder="请选择轮次" style="width: 220px"><el-option v-for="r in rounds" :key="r.roundId" :label="`[${r.roundId}] ${r.name}`" :value="r.roundId" /></el-select></el-form-item>
              <el-form-item label="方式"><el-tag type="success">equal 均分</el-tag></el-form-item>
              <el-form-item label="原因"><el-input v-model="distributionForm.reason" /></el-form-item>
              <p class="tip warning-text">P0 只做 equal。点击后会把团队池当前余额分配给成员，请确认直播流程。</p>
              <div class="form-actions">
                <el-button native-type="button" type="danger" @click="submitDistribution">确认均分</el-button>
              </div>
            </el-form>
          </el-card>
        </div>
      </el-tab-pane>

      <el-tab-pane label="基础数据" name="basic">
        <div class="grid-two">
          <el-card class="panel-card">
            <div class="panel-title">选手管理</div>
            <el-form @submit.prevent inline>
              <el-form-item label="姓名"><el-input v-model="playerForm.name" /></el-form-item>
              <el-form-item label="序号"><el-input-number v-model="playerForm.number" :min="1" /></el-form-item>
              <el-form-item><el-button native-type="button" type="primary" @click="submitPlayer">新增选手</el-button></el-form-item>
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
            <el-form @submit.prevent inline>
              <el-form-item label="队名"><el-input v-model="teamForm.name" /></el-form-item>
              <el-form-item><el-button native-type="button" type="primary" @click="submitTeam">新增队伍</el-button></el-form-item>
            </el-form>
            <el-table :data="teams" size="small" height="260">
              <el-table-column prop="teamId" label="ID" width="80" />
              <el-table-column prop="name" label="队名" />
            </el-table>
          </el-card>

          <el-card class="panel-card">
            <div class="panel-title">轮次管理</div>
            <el-form @submit.prevent label-width="90px">
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
              <div class="form-actions"><el-button native-type="button" type="primary" @click="submitRound">新增轮次</el-button></div>
            </el-form>
            <el-table :data="rounds" size="small" height="260">
              <el-table-column prop="roundId" label="ID" width="70" />
              <el-table-column prop="name" label="名称" />
              <el-table-column prop="status" label="状态" width="110" />
              <el-table-column label="操作" width="130">
                <template #default="scope">
                  <el-button native-type="button" size="small" type="warning" @click="activateRound(scope.row)">设为 active</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <el-card class="panel-card">
            <div class="panel-title">分队与卧底设置</div>
            <el-form @submit.prevent inline>
              <el-form-item label="轮次"><el-select v-model="playerRoundFilterRoundId" filterable placeholder="请选择轮次" style="width: 200px" @change="refreshPlayerRounds"><el-option v-for="r in rounds" :key="r.roundId" :label="`[${r.roundId}] ${r.name}`" :value="r.roundId" /></el-select></el-form-item>
              <el-form-item><el-button native-type="button" @click="refreshPlayerRounds">查询</el-button></el-form-item>
            </el-form>
            <el-form @submit.prevent label-width="90px">
              <el-form-item label="选手"><el-select v-model="playerRoundForm.playerId" filterable clearable placeholder="请选择选手" style="width: 220px"><el-option v-for="p in players" :key="p.playerId" :label="`${p.number}号 ${p.name}`" :value="p.playerId" /></el-select></el-form-item>
              <el-form-item label="队伍"><el-select v-model="playerRoundForm.teamId" filterable clearable placeholder="请选择队伍" style="width: 220px"><el-option v-for="t in teams" :key="t.teamId" :label="t.name" :value="t.teamId" /></el-select></el-form-item>
              <el-form-item label="是否卧底"><el-switch v-model="playerRoundForm.isSpy" /></el-form-item>
              <el-form-item label="状态">
                <el-select v-model="playerRoundForm.playerStatus">
                  <el-option label="normal" value="normal" />
                  <el-option label="free" value="free" />
                  <el-option label="eliminated" value="eliminated" />
                </el-select>
              </el-form-item>
              <div class="form-actions"><el-button native-type="button" type="primary" @click="submitPlayerRound">保存分队</el-button></div>
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

      <el-tab-pane label="发码与导出" name="tokens">
        <div class="grid-two">
          <el-card class="panel-card">
            <div class="panel-title">生成卡密批次</div>
            <p class="tip warning-text">发码等于印钞，请核对选手和写真！</p>
            <el-form @submit.prevent label-width="90px">
              <el-form-item label="选手">
                <el-select v-model="tokenForm.playerId" filterable @change="onTokenPlayerChange">
                  <el-option v-for="player in players" :key="player.playerId" :label="`${player.number}号 ${player.name}`" :value="player.playerId" />
                </el-select>
              </el-form-item>
              <el-form-item label="绑定写真">
                <el-select v-model="tokenForm.photoAssetId" filterable>
                  <el-option v-for="photo in tokenFormPhotos" :key="photo.assetId" :label="photo.assetId" :value="photo.assetId" />
                </el-select>
              </el-form-item>
              <el-form-item label="单张人气"><el-input-number v-model="tokenForm.points" :min="1" /></el-form-item>
              <el-form-item label="生成数量"><el-input-number v-model="tokenForm.count" :min="1" :max="10000" /></el-form-item>
              <el-form-item label="SKU(选填)"><el-input v-model="tokenForm.productSku" /></el-form-item>
              <div class="form-actions">
                <el-button native-type="button" type="danger" @click="submitTokenGenerate">确认生成</el-button>
              </div>
            </el-form>
          </el-card>

          <el-card class="panel-card" v-if="lastBatchId">
            <div class="panel-title">导出阿奇索卡库</div>
            <p class="tip">最新生成的批次：{{ lastBatchId }}</p>
            <p class="tip warning-text">导出文件为纯文本，一码一行无表头。请直接导入阿奇索对应的商品卡库，切勿跨选手混导！</p>
            <div class="form-actions" style="margin-top: 20px;">
              <el-button native-type="button" type="success" @click="downloadCsv">下载纯文本卡库</el-button>
            </div>
          </el-card>
        </div>
      </el-tab-pane>

      <el-tab-pane label="写真管理" name="photos">
        <div class="grid-two">
          <el-card class="panel-card">
            <div class="panel-title">上传写真</div>
            <p class="tip warning-text">仅上传清新/才艺/舞台风图片；禁止性感擦边素材。系统只接受 jpg/png/webp，禁止 SVG。</p>
            <el-form @submit.prevent label-width="90px">
              <el-form-item label="选手">
                <el-select v-model="photoUploadForm.playerId" filterable style="width: 260px">
                  <el-option v-for="player in players" :key="player.playerId" :label="`${player.number}号 ${player.name}`" :value="player.playerId" />
                </el-select>
              </el-form-item>
              <el-form-item label="图片文件">
                <input type="file" accept="image/jpeg,image/png,image/webp" @change="onPhotoFileChange" />
              </el-form-item>
              <el-form-item label="设为封面"><el-switch v-model="photoUploadForm.isCover" /></el-form-item>
              <el-form-item label="排序"><el-input-number v-model="photoUploadForm.sortOrder" /></el-form-item>
              <div class="form-actions">
                <el-button native-type="button" type="primary" @click="submitPhotoUpload" v-loading="uploading">上传写真</el-button>
              </div>
            </el-form>
          </el-card>

          <el-card class="panel-card">
            <div class="panel-title">写真筛选</div>
            <el-form @submit.prevent label-width="90px">
              <el-form-item label="选手">
                <el-select v-model="photoFilter.playerId" clearable filterable style="width: 260px">
                  <el-option v-for="player in players" :key="player.playerId" :label="`${player.number}号 ${player.name}`" :value="player.playerId" />
                </el-select>
              </el-form-item>
              <el-form-item label="状态">
                <el-select v-model="photoFilter.status" clearable style="width: 160px">
                  <el-option label="active" value="active" />
                  <el-option label="inactive" value="inactive" />
                </el-select>
              </el-form-item>
              <div class="form-actions"><el-button native-type="button" @click="refreshPhotos">刷新写真</el-button></div>
            </el-form>
            <p class="tip">下架只隐藏用户端新查询，不物理删除文件或用户收藏记录。</p>
          </el-card>
        </div>

        <el-card class="panel-card">
          <div class="panel-title">写真资产列表</div>
          <el-table :data="photos" size="small" height="520">
            <el-table-column label="预览" width="110">
              <template #default="scope">
                <img :src="scope.row.previewUrl" class="photo-thumb" alt="写真预览" />
              </template>
            </el-table-column>
            <el-table-column prop="assetId" label="assetId" width="210" />
            <el-table-column label="选手" width="140">
              <template #default="scope">{{ scope.row.playerNumber }}号 {{ scope.row.playerName }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="95" />
            <el-table-column prop="isCover" label="封面" width="80" />
            <el-table-column prop="sortOrder" label="排序" width="80" />
            <el-table-column prop="contentType" label="类型" width="120" />
            <el-table-column prop="fileSize" label="大小" width="100" />
            <el-table-column label="操作" width="360" fixed="right">
              <template #default="scope">
                <el-button native-type="button" size="small" @click="copyPhotoUrl(scope.row)">复制 URL</el-button>
                <el-button native-type="button" size="small" type="success" @click="markPhotoCover(scope.row)">设封面</el-button>
                <el-button native-type="button" size="small" :type="scope.row.status === 'active' ? 'warning' : 'primary'" @click="togglePhotoStatus(scope.row)">{{ scope.row.status === 'active' ? '下架' : '恢复' }}</el-button>
                <input class="replace-input" type="file" accept="image/jpeg,image/png,image/webp" @change="(event) => replacePhotoFile(scope.row, event)" />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { distributeTeam, getAdminBoard, getAdminHome, getSuspicionStatus, manualAdjust, setCollectState, simulateInject } from './api/admin'
import { createPlayer, createRound, createTeam, listPlayerRounds, listPlayers, listRounds, listTeams, savePlayerRound, updateRoundStatus } from './api/basicData'
import { listPhotos, replacePhoto, setPhotoCover, updatePhotoStatus, uploadPhoto } from './api/photos'
import { generateTokens } from './api/tokens'
import { getAdminToken, setAdminToken, clearAdminToken, setUnauthorizedHandler } from './api/http'

const activeTab = ref('monitor')
const operatorId = ref(localStorage.getItem('operatorId') || 'director')
const adminToken = ref(getAdminToken())

function saveAdminTokenValue() {
  const value = adminToken.value.trim()
  if (value) {
    setAdminToken(value)
    ElMessage.success('管理口令已保存（仅存本地）')
  } else {
    clearAdminToken()
  }
}

/** 提示运营输入管理口令（首次进入或 401 后）。 */
async function promptAdminToken(message: string) {
  try {
    const { value } = await ElMessageBox.prompt(message, '输入管理口令', {
      confirmButtonText: '保存',
      cancelButtonText: '稍后',
      inputType: 'password',
      inputPlaceholder: '请输入运维下发的 ADMIN_TOKEN'
    })
    if (value && value.trim()) {
      adminToken.value = value.trim()
      setAdminToken(adminToken.value)
      ElMessage.success('管理口令已保存，请重试操作')
    }
  } catch {
    /* 用户取消，不处理 */
  }
}
const home = ref<any>({})
const board = ref<any>({ items: [] })
const suspicionStatus = ref<any>({ candidates: [] })
const boardTab = ref('player')
const boardRoundId = ref<number | null>(null)
const players = ref<any[]>([])
const teams = ref<any[]>([])
const rounds = ref<any[]>([])
const playerRounds = ref<any[]>([])
const photos = ref<any[]>([])
const playerRoundFilterRoundId = ref<number | null>(null)

const collectForm = reactive<{ mode: string; targetId: number | null; roundId: number | null }>({ mode: 'player', targetId: null, roundId: null })
const simulateForm = reactive<{ eventType: string; value: number; targetId: number | null }>({ eventType: 'like_delta', value: 10, targetId: null })
const manualForm = reactive<{ targetType: string; targetId: number | null; roundId: number | null; rawValue: number; reason: string }>({ targetType: 'player', targetId: null, roundId: null, rawValue: 100, reason: '彩排手动调分' })
const distributionForm = reactive<{ teamId: number | null; roundId: number | null; method: string; reason: string }>({ teamId: null, roundId: null, method: 'equal', reason: '彩排团队均分' })
const playerForm = reactive({ name: '', number: 1 })
const teamForm = reactive({ name: '' })
const roundForm = reactive({ name: '', startTime: '', endTime: '', status: 'upcoming' })
const playerRoundForm = reactive<{ playerId: number | null; teamId: number | null; isSpy: boolean; playerStatus: string }>({ playerId: null, teamId: null, isSpy: false, playerStatus: 'normal' })
const photoFilter = reactive<{ playerId: number | null; status: string | null }>({ playerId: null, status: 'active' })
const photoUploadForm = reactive<{ playerId: number; isCover: boolean; sortOrder: number; file: File | null }>({ playerId: 1, isCover: true, sortOrder: 0, file: null })
const uploading = ref(false)
const tokenForm = reactive({ playerId: 1, photoAssetId: '', points: 100, count: 10, productSku: '' })
const tokenFormPhotos = ref<any[]>([])
const lastBatchId = ref('')

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
  suspicionStatus.value = await getSuspicionStatus(home.value.roundId || boardRoundId.value)
}

async function refreshBoard() {
  board.value = await getAdminBoard(boardTab.value, boardRoundId.value)
}

function getNextPlayerNumber() {
  const maxNumber = players.value.reduce((max, player) => {
    const number = Number(player.number) || 0
    return number > max ? number : max
  }, 0)
  return maxNumber + 1
}

function resetPlayerForm() {
  playerForm.name = ''
  playerForm.number = getNextPlayerNumber()
}

function resetTeamForm() {
  teamForm.name = ''
}

async function refreshBasicData() {
  players.value = await listPlayers()
  teams.value = await listTeams()
  rounds.value = await listRounds()
  if (!playerForm.name.trim()) {
    playerForm.number = getNextPlayerNumber()
  }
  applyDefaultRound()
}

/** 数据加载后给轮次类下拉一个合理默认值：优先 active 轮次，否则第一个轮次。 */
function applyDefaultRound() {
  if (rounds.value.length === 0) return
  const active = rounds.value.find((r) => r.status === 'active')
  const fallbackId = (active || rounds.value[0]).roundId
  if (boardRoundId.value == null) boardRoundId.value = fallbackId
  if (playerRoundFilterRoundId.value == null) playerRoundFilterRoundId.value = fallbackId
  if (collectForm.roundId == null) collectForm.roundId = fallbackId
  if (manualForm.roundId == null) manualForm.roundId = fallbackId
  if (distributionForm.roundId == null) distributionForm.roundId = fallbackId
}

/** 切换集赞模式时清空旧目标，避免选手 ID 被错当成团队 ID 提交（Claude 裁定要求）。 */
function onCollectModeChange() {
  collectForm.targetId = null
}

/** 切换手动调分目标类型时清空旧目标，防止目标值串用（Claude 裁定要求）。 */
function onManualTypeChange() {
  manualForm.targetId = null
}

async function refreshPlayerRounds() {
  if (playerRoundFilterRoundId.value == null) {
    ElMessage.warning('请先选择轮次')
    return
  }
  playerRounds.value = await listPlayerRounds(playerRoundFilterRoundId.value)
}

async function refreshPhotos() {
  photos.value = await listPhotos(photoFilter)
}

function onPhotoFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  photoUploadForm.file = input.files && input.files.length > 0 ? input.files[0] : null
}

async function submitPhotoUpload() {
  if (!photoUploadForm.file) {
    ElMessage.error('请选择jpg/png/webp图片文件')
    return
  }
  uploading.value = true
  try {
    await runAction('写真已上传', () => uploadPhoto(withOperator({
      playerId: photoUploadForm.playerId,
      isCover: photoUploadForm.isCover,
      sortOrder: photoUploadForm.sortOrder,
      file: photoUploadForm.file as File
    })), async () => {
      photoFilter.playerId = photoUploadForm.playerId
      photoFilter.status = 'active'
      photoUploadForm.file = null
      await refreshPhotos()
    })
  } finally {
    uploading.value = false
  }
}

async function replacePhotoFile(row: any, event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files && input.files.length > 0 ? input.files[0] : null
  if (!file) return
  await ElMessageBox.confirm(`确认替换写真【${row.assetId}】的文件？assetId 会保持不变。`, '替换写真文件', { type: 'warning' })
  await runAction('写真文件已替换', () => replacePhoto(row.assetId, { operatorId: operatorId.value, file }), refreshPhotos)
  input.value = ''
}

async function markPhotoCover(row: any) {
  await runAction('已设为封面', () => setPhotoCover(row.assetId, { operatorId: operatorId.value }), refreshPhotos)
}

async function togglePhotoStatus(row: any) {
  const nextStatus = row.status === 'active' ? 'inactive' : 'active'
  await runAction(nextStatus === 'active' ? '写真已恢复' : '写真已下架', () => updatePhotoStatus(row.assetId, { operatorId: operatorId.value, status: nextStatus }), refreshPhotos)
}

async function copyPhotoUrl(row: any) {
  await navigator.clipboard.writeText(row.previewUrl)
  ElMessage.success('图片 URL 已复制')
}

async function submitCollectState() {
  if (collectForm.roundId == null) {
    ElMessage.warning('请选择轮次')
    return
  }
  if (collectForm.mode !== 'pool' && collectForm.targetId == null) {
    ElMessage.warning('请选择目标')
    return
  }
  await ElMessageBox.confirm('确认切换当前集赞目标？该操作会影响点赞/留言归属。', '二次确认', { type: 'warning' })
  const payload = { ...collectForm, targetId: collectForm.mode === 'pool' ? null : collectForm.targetId }
  await runAction('集赞目标已切换', () => setCollectState(withOperator(payload)), refreshMonitor)
}

async function submitSimulate() {
  if (simulateForm.eventType === 'gift' && simulateForm.targetId == null) {
    ElMessage.warning('gift 事件请先选择目标选手')
    return
  }
  await runAction('模拟注入成功', () => simulateInject(withOperator(simulateForm)), refreshMonitor)
}

async function submitManualAdjust() {
  if (manualForm.roundId == null) {
    ElMessage.warning('请选择轮次')
    return
  }
  if (manualForm.targetType !== 'pool' && manualForm.targetId == null) {
    ElMessage.warning('请选择目标')
    return
  }
  await ElMessageBox.confirm('确认执行手动调分？该操作会写入人气流水与审计日志。', '二次确认', { type: 'warning' })
  const payload = { ...manualForm, targetId: manualForm.targetType === 'pool' ? null : manualForm.targetId }
  await runAction('手动调分成功', () => manualAdjust(withOperator(payload)), refreshMonitor)
}

async function submitDistribution() {
  if (distributionForm.teamId == null) {
    ElMessage.warning('请选择团队')
    return
  }
  if (distributionForm.roundId == null) {
    ElMessage.warning('请选择轮次')
    return
  }
  await ElMessageBox.confirm('确认将团队池当前余额均分给团队成员？', '二次确认', { type: 'warning' })
  await runAction('团队人气均分成功', () => distributeTeam(withOperator(distributionForm)), refreshMonitor)
}

async function submitPlayer() {
  await runAction('选手已新增', () => createPlayer(withOperator({ ...playerForm })), async () => {
    await refreshBasicData()
    resetPlayerForm()
  })
}

async function submitTeam() {
  await runAction('队伍已新增', () => createTeam(withOperator({ ...teamForm })), async () => {
    await refreshBasicData()
    resetTeamForm()
  })
}

async function submitRound() {
  await runAction('轮次已新增', () => createRound(withOperator(roundForm)), refreshBasicData)
}

async function activateRound(row: any) {
  await ElMessageBox.confirm(`这会把当前进行中的轮次标记为已结束，并将【${row.name}】设为 active。确认继续？`, '切换 active 轮次', { type: 'warning' })
  await runAction('轮次已切换为 active', () => updateRoundStatus(row.roundId, withOperator({ status: 'active' })), refreshBasicData)
}

async function submitPlayerRound() {
  if (playerRoundFilterRoundId.value == null) {
    ElMessage.warning('请先在上方选择轮次')
    return
  }
  if (playerRoundForm.playerId == null) {
    ElMessage.warning('请选择选手')
    return
  }
  if (playerRoundForm.teamId == null) {
    ElMessage.warning('请选择队伍')
    return
  }
  const payload = { ...playerRoundForm, roundId: playerRoundFilterRoundId.value }
  await runAction('分队信息已保存', () => savePlayerRound(withOperator(payload)), refreshPlayerRounds)
}

onMounted(async () => {
  saveOperator()
  // 注册 401 处理：http 层收到 401 会清空旧口令并触发此回调，提示运营重输。
  setUnauthorizedHandler(() => {
    adminToken.value = ''
    promptAdminToken('管理口令无效或已过期，请重新输入。')
  })
  // 首次进入未设置口令时主动提示输入（生产后端已开鉴权，不输将全部 401）。
  if (!getAdminToken()) {
    await promptAdminToken('请输入运维下发的管理口令 ADMIN_TOKEN，否则场控后台操作将被拒绝。')
  }
  await refreshBasicData()
  if (players.value.length > 0) {
    photoUploadForm.playerId = players.value[0].playerId
  }
  await refreshPhotos()
  await refreshMonitor()
})
</script>

async function onTokenPlayerChange() {
  tokenFormPhotos.value = await listPhotos({ playerId: tokenForm.playerId, status: 'active' })
  if (tokenFormPhotos.value.length > 0) {
    tokenForm.photoAssetId = tokenFormPhotos.value[0].assetId
  } else {
    tokenForm.photoAssetId = ''
  }
}

async function submitTokenGenerate() {
  if (!tokenForm.photoAssetId) {
    ElMessage.error('请选择绑定的写真')
    return
  }
  await ElMessageBox.confirm(`确认生成 ${tokenForm.count} 张卡密？（每张 ${tokenForm.points} 人气）`, '生成确认', { type: 'warning' })
  await runAction('卡密生成成功', async () => {
    const res = await generateTokens(withOperator({
      playerId: tokenForm.playerId,
      photoAssetId: tokenForm.photoAssetId,
      points: tokenForm.points,
      count: tokenForm.count,
      productSku: tokenForm.productSku
    }))
    lastBatchId.value = res.batchId
    return res
  }, async () => {})
}

function downloadCsv() {
  if (!lastBatchId.value) return
  window.open(`/api/admin/tokens/export?batchId=${lastBatchId.value}&operatorId=${operatorId.value}`, '_blank')
}
