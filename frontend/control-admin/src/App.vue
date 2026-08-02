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
            <div class="panel-title">卧底识破监控</div>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="状态">{{ suspicionStatus.open ? '进行中' : '暂未开启' }}</el-descriptions-item>
              <el-descriptions-item label="轮次">{{ suspicionStatus.roundName || suspicionStatus.roundId || '-' }}</el-descriptions-item>
            </el-descriptions>
            <p class="tip">只展示判断分布，按选手序号排列，不展示真实卧底身份。</p>
            <el-table :data="suspicionStatus.candidates || []" size="small" height="260">
              <el-table-column prop="number" label="序号(自增)" width="100" />
              <el-table-column prop="displayCode" label="编号" width="100" />
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
            <div class="panel-title" style="display: flex; justify-content: space-between; align-items: center;">
              <span>集赞目标切换</span>
              <div>
                <span v-if="suspicionStatus.open" style="font-size: 12px; color: #e6a23c; margin-right: 10px;">
                  识破进行中 · {{ home.targetId ? '目标: ' + home.targetDisplayName : '未指定目标' }}
                </span>
                <el-button v-if="suspicionStatus.open" type="primary" size="small" @click="changeSpyTarget">切换目标</el-button>
                <el-button :type="suspicionStatus.open ? 'danger' : 'success'" size="small" @click="toggleSpyMode">
                  {{ suspicionStatus.open ? '关闭卧底识破' : '开启卧底识破' }}
                </el-button>
              </div>
            </div>
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

          <el-card class="panel-card">
            <div class="panel-title">群投票结果录入（C20-3）</div>
            <p class="tip warning-text">8/1 当晚专用：把粉丝群投票结果录入系统。正数累加、负数冲销，同轮同选手可多次录入。提交前请核对群投票截图。</p>
            <el-form @submit.prevent label-width="100px">
              <el-form-item label="轮次">
                <el-select v-model="groupVoteForm.roundId" filterable placeholder="请选择轮次" style="width: 220px" @change="refreshGroupVoteSummary">
                  <el-option v-for="r in rounds" :key="r.roundId" :label="`[${r.roundId}] ${r.name}`" :value="r.roundId" />
                </el-select>
              </el-form-item>
              <el-form-item label="选手">
                <el-select v-model="groupVoteForm.playerId" filterable clearable placeholder="请选择选手" style="width: 220px">
                  <el-option v-for="p in players" :key="p.playerId" :label="`${p.number}号 ${p.name}`" :value="p.playerId" />
                </el-select>
              </el-form-item>
              <el-form-item label="票数增量">
                <el-input-number v-model="groupVoteForm.votes" :step="1" placeholder="正数累加，负数冲销" />
              </el-form-item>
              <el-form-item label="原因">
                <el-input v-model="groupVoteForm.reason" placeholder="例：8/1粉丝群第一轮投票" />
              </el-form-item>
              <div class="form-actions">
                <el-button native-type="button" type="primary" :loading="groupVoteSubmitting" @click="submitGroupVote">确认录入</el-button>
              </div>
            </el-form>
            <div class="panel-title" style="margin-top: 12px;">本轮累计票数（冲销后净值）</div>
            <el-table :data="groupVoteSummary.items || []" size="small" height="220">
              <el-table-column prop="playerNumber" label="序号" width="80" />
              <el-table-column prop="playerName" label="选手" />
              <el-table-column prop="totalVotes" label="累计票数" width="110" />
              <el-table-column prop="entryCount" label="录入笔数" width="100" />
            </el-table>
            <p class="tip">合计：{{ groupVoteSummary.totalVotes ?? 0 }} 票 · <el-button native-type="button" link type="primary" @click="refreshGroupVoteSummary">刷新</el-button></p>
          </el-card>

          <el-card class="panel-card">
            <div class="panel-title">手动加成</div>
            <el-form @submit.prevent label-width="80px">
              <el-form-item label="目标类型">
                <el-select v-model="bonusForm.targetType">
                  <el-option label="选手 player" value="player" />
                  <el-option label="团队 team" value="team" />
                </el-select>
              </el-form-item>
              <el-form-item label="目标 ID"><el-input-number v-model="bonusForm.targetId" :min="1" /></el-form-item>
              <el-form-item label="轮次 ID"><el-input-number v-model="bonusForm.roundId" :min="1" /></el-form-item>
              <el-form-item label="加成变动"><el-input-number v-model="bonusForm.delta" :min="-100" :max="100" placeholder="±10代表±0.1" /></el-form-item>
              <el-form-item label="原因"><el-input v-model="bonusForm.reason" /></el-form-item>
              <div class="form-actions"><el-button native-type="button" type="warning" @click="submitManualBonus">确认加成</el-button></div>
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
              <el-form-item label="编号"><el-input v-model="playerForm.displayCode" placeholder="4位数字" /></el-form-item>
              <el-form-item><el-button native-type="button" type="primary" @click="submitPlayer">新增选手</el-button></el-form-item>
            </el-form>
            <el-table :data="players" size="small" height="260">
              <el-table-column prop="number" label="序号(自增)" width="100" />
              <el-table-column prop="displayCode" label="编号" width="100" />
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
                  <el-option v-for="player in players" :key="player.playerId" :label="`${player.displayCode} ${player.name}`" :value="player.playerId" />
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
                <el-button native-type="button" type="danger" @click="submitTokenGenerate" :loading="generating">确认生成</el-button>
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

      <el-tab-pane label="退款管理" name="refunds">
        <div class="grid-two">
          <el-card class="panel-card">
            <div class="panel-title">卡密退款</div>
            <p class="tip warning-text">第一季退款规则：只回退人气值，不回收会员天数和写真收藏。</p>
            <el-form @submit.prevent label-width="90px">
              <el-form-item label="卡密"><el-input v-model="refundForm.tokenId" placeholder="RFZJ-XXXX-XXXX-XXXX" /></el-form-item>
              <el-form-item label="退款原因"><el-input v-model="refundForm.reason" /></el-form-item>
              <div class="form-actions">
                <el-button native-type="button" type="danger" @click="submitRefund">确认退款</el-button>
              </div>
            </el-form>
          </el-card>
        </div>
      </el-tab-pane>

      <!--
        C20-4C 订单表批量导入。已完成并通过 22 项专项测试，但按 Claude 2026-08-02 裁定
        「已完成但暂不启用，等待订单量上升后启用」——8/9 首场改走 C20-6 后台手工销量录入。
        默认隐藏原因：防止运营在直播现场误入本流程。启用前置条件（不可跳过）：
        必须先修复 players.display_code 无写入入口的缺陷，见
        collaboration/已知缺陷_display_code无写入入口_V1.0.md。
        启用方式：地址栏加 ?experimental=1，或将 showExperimental 默认值改为 true。
      -->
      <el-tab-pane v-if="showExperimental" label="订单导入（实验）" name="orders">
        <el-alert
          type="error"
          :closable="false"
          show-icon
          title="实验功能，8/9 首场不使用"
          class="mb-12"
        >
          本页为 C20-4C 订单表批量导入，已完成但<b>暂未启用</b>。启用前必须先修复
          <code>players.display_code</code> 无写入入口的缺陷，否则每一行都会被判为未归属并阻断整批。
          8/9 首场请使用「销量录入」页。
        </el-alert>
        <el-card class="panel-card">
          <div class="panel-title">商品原价配置</div>
          <p class="tip warning-text">
            人气值按「单价配置 × 件数」计算，<b>不</b>用订单实付金额。
            未配置原价的商家编码会被判为未归属并<b>阻止整批入账</b>，请在开场前配齐。
          </p>
          <el-form @submit.prevent :inline="true">
            <el-form-item label="商家编码">
              <el-input v-model="priceForm.merchantCode" placeholder="如 P12" style="width: 130px" />
            </el-form-item>
            <el-form-item label="商品名">
              <el-input v-model="priceForm.productName" placeholder="如 明信片标准款" style="width: 190px" />
            </el-form-item>
            <el-form-item label="单价（元）">
              <el-input v-model="priceForm.unitPriceYuan" placeholder="19.9" style="width: 110px" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="priceForm.status" style="width: 120px">
                <el-option label="active" value="active" />
                <el-option label="disabled" value="disabled" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button native-type="button" type="primary" @click="submitProductPrice">保存原价</el-button>
              <el-button native-type="button" @click="refreshProductPrices">刷新</el-button>
            </el-form-item>
          </el-form>
          <el-table :data="productPrices" size="small" max-height="200">
            <el-table-column prop="merchantCode" label="商家编码" width="120" />
            <el-table-column prop="productName" label="商品名" />
            <el-table-column label="单价" width="120">
              <template #default="scope">{{ yuan(scope.row.unitPriceCent) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100" />
          </el-table>
        </el-card>

        <el-card class="panel-card">
          <div class="panel-title">上传订单表</div>
          <p class="tip">
            支持抖店导出的 xlsx / csv。两个入口的区别：<b>前置检查</b>只校验不落库、不产生确认按钮，
            用于赛前空跑；<b>上传并预览</b>会生成一次性确认令牌，用于正式入账。
          </p>
          <el-form @submit.prevent :inline="true">
            <el-form-item label="轮次">
              <el-select v-model="orderImportRoundId" clearable placeholder="选轮次" style="width: 200px">
                <el-option v-for="r in rounds" :key="r.roundId" :label="`${r.roundId} ${r.name}`" :value="r.roundId" />
              </el-select>
            </el-form-item>
            <el-form-item label="订单文件">
              <input type="file" accept=".xlsx,.xls,.csv" @change="onOrderFileChange" />
            </el-form-item>
            <el-form-item>
              <el-button native-type="button" @click="runPreflight" :loading="orderLoading">前置检查（不入账）</el-button>
              <el-button native-type="button" type="primary" @click="runPreview" :loading="orderLoading">上传并预览</el-button>
            </el-form-item>
          </el-form>
          <el-alert v-if="orderPreviewIsPreflight" type="info" :closable="false" show-icon
                   title="当前为前置检查结果：未生成确认令牌，不可入账。正式导入请点「上传并预览」" />
        </el-card>

        <template v-if="orderPreview">
          <el-card class="panel-card">
            <div class="panel-title">预览汇总</div>
            <el-alert v-for="err in orderPreview.blockingErrors" :key="err" type="error" :closable="false"
                     show-icon :title="err" style="margin-bottom: 8px" />
            <el-alert v-if="orderPreview.blockedByUnattributed" type="error" :closable="false" show-icon
                     :title="orderPreview.blockReason || '存在未归属订单，已阻止入账'" style="margin-bottom: 8px" />
            <el-alert v-for="w in orderPreview.warnings" :key="w" type="warning" :closable="false"
                     show-icon :title="w" style="margin-bottom: 8px" />
            <div class="metric-row">
              <div class="metric-box">
                <div class="metric-label">总行数</div>
                <div class="metric-number">{{ orderPreview.totalRows }}</div>
              </div>
              <div class="metric-box">
                <div class="metric-label">有效行</div>
                <div class="metric-number">{{ orderPreview.validRows }}</div>
              </div>
              <div class="metric-box">
                <div class="metric-label">无效行</div>
                <div class="metric-number">{{ orderPreview.invalidRows }}</div>
              </div>
              <div class="metric-box metric-danger">
                <div class="metric-label">未归属行</div>
                <div class="metric-number">{{ orderPreview.unattributedRows }}</div>
              </div>
              <div class="metric-box">
                <div class="metric-label">已入账重复</div>
                <div class="metric-number">{{ orderPreview.duplicateRows }}</div>
              </div>
              <div class="metric-box">
                <div class="metric-label">合计件数</div>
                <div class="metric-number">{{ orderPreview.totalQuantity }}</div>
              </div>
              <div class="metric-box">
                <div class="metric-label">合计人气值</div>
                <div class="metric-number">{{ orderPreview.totalPopularity }}</div>
              </div>
              <div class="metric-box metric-warn">
                <div class="metric-label">售后中敲口</div>
                <div class="metric-number">{{ orderPreview.aftersaleExposure }}</div>
              </div>
              <div class="metric-box metric-warn">
                <div class="metric-label">未知状态行</div>
                <div class="metric-number">{{ orderPreview.unknownStatusRows }}</div>
              </div>
            </div>
          </el-card>

          <el-card class="panel-card">
            <div class="panel-title">按选手汇总核对</div>
            <p class="tip">
              确认前请逐行核对。<b>件数</b>与<b>人气值</b>并列的目的是：人气值偏高时，
              看件数就能判断是「真的卖得多」还是「单价配错了」。
            </p>
            <el-table :data="orderPreview.byPlayerDetail" size="small" max-height="320">
              <el-table-column prop="merchantCode" label="商家编码" width="120" />
              <el-table-column label="选手" width="150">
                <template #default="scope">
                  <span v-if="scope.row.playerName">{{ scope.row.playerName }}</span>
                  <span v-else class="warning-text">（未查到姓名）</span>
                </template>
              </el-table-column>
              <el-table-column prop="validRows" label="笔数" width="90" />
              <el-table-column prop="quantity" label="件数" width="90" />
              <el-table-column label="单价" width="110">
                <template #default="scope">{{ yuan(scope.row.unitPriceCent) }}</template>
              </el-table-column>
              <el-table-column prop="popularityValue" label="人气值" width="130" />
              <el-table-column label="售后中敲口" width="150">
                <template #default="scope">
                  <span v-if="scope.row.aftersaleRows > 0" class="warning-text">
                    {{ scope.row.aftersaleRows }} 笔 / {{ scope.row.aftersaleExposure }}
                  </span>
                  <span v-else>-</span>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <el-card v-if="unattributedRowList.length > 0" class="panel-card">
            <div class="panel-title">未归属订单（已阻止入账）</div>
            <p class="tip warning-text">
              这些订单<b>不会计入任何选手的人气值</b>。首选做法是补齐上方商品原价配置后重新上传预览；
              确定无需计入时，必须逐笔勾选并填写原因，系统会记录操作人与原因。
            </p>
            <el-table :data="unattributedRowList" size="small" max-height="300"
                      @selection-change="onOverrideSelectionChange" ref="unattributedTableRef">
              <el-table-column type="selection" width="48" />
              <el-table-column prop="rowNumber" label="行号" width="80" />
              <el-table-column prop="subOrderNo" label="子订单号" width="190" />
              <el-table-column prop="merchantCode" label="商家编码" width="120" />
              <el-table-column prop="quantity" label="件数" width="80" />
              <el-table-column prop="invalidReason" label="未归属原因" />
            </el-table>
            <el-form @submit.prevent label-width="90px" style="margin-top: 12px">
              <el-form-item label="排除原因">
                <el-input v-model="overrideReason" type="textarea" :rows="2"
                          placeholder="必填。例：该编号为测试商品，经 Vincent 确认不计入本场人气" />
              </el-form-item>
              <p class="tip">
                已勾选 {{ overrideSelection.length }} / {{ unattributedRowList.length }} 笔。
                必须全部勾选才能提交——否则未勾选的那几笔会被无声排除。
              </p>
              <div class="form-actions">
                <el-button native-type="button" @click="selectAllUnattributed">全选（逐笔核对后）</el-button>
                <el-button native-type="button" type="danger" :disabled="!canSubmitOverride"
                           :loading="orderLoading" @click="submitConfirmOverride">
                  确认并排除未归属订单
                </el-button>
              </div>
            </el-form>
          </el-card>

          <el-card class="panel-card">
            <div class="panel-title">逐行明细</div>
            <el-radio-group v-model="orderRowFilter" size="small" style="margin-bottom: 10px">
              <el-radio-button label="all">全部 {{ orderPreview.rows.length }}</el-radio-button>
              <el-radio-button label="valid">有效 {{ orderPreview.validRows }}</el-radio-button>
              <el-radio-button label="invalid">无效 {{ orderPreview.invalidRows }}</el-radio-button>
              <el-radio-button label="unattributed">未归属 {{ orderPreview.unattributedRows }}</el-radio-button>
              <el-radio-button label="aftersale">售后中 {{ orderPreview.aftersaleRows }}</el-radio-button>
            </el-radio-group>
            <el-table :data="filteredOrderRows" size="small" max-height="420">
              <el-table-column prop="rowNumber" label="行号" width="70" />
              <el-table-column prop="subOrderNo" label="子订单号" width="180" />
              <el-table-column prop="merchantCode" label="编码" width="90" />
              <el-table-column label="选手" width="110">
                <template #default="scope">{{ scope.row.playerName || '-' }}</template>
              </el-table-column>
              <el-table-column prop="quantity" label="件数" width="70" />
              <el-table-column prop="orderStatus" label="订单状态" width="110" />
              <el-table-column prop="aftersaleStatus" label="售后状态" width="110" />
              <el-table-column label="判定" width="110">
                <template #default="scope">
                  <el-tag size="small" :type="validityTagType(scope.row.validity)">
                    {{ validityLabel(scope.row.validity) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="popularityValue" label="人气值" width="110" />
              <el-table-column prop="invalidReason" label="原因" min-width="220" />
            </el-table>
          </el-card>

          <el-card class="panel-card">
            <div class="panel-title">确认入账</div>
            <p class="tip">
              令牌一次性消费：确认后需重新上传才能再导。这道限制同时防重复点击与「看的是 A 文件、导的是 B 文件」。
            </p>
            <div class="form-actions">
              <el-button native-type="button" type="primary" :disabled="!canConfirmImport"
                         :loading="orderLoading" @click="submitConfirmImport">确认入账</el-button>
            </div>
            <p v-if="orderPreview.blockedByUnattributed" class="tip warning-text">
              存在未归属订单，普通确认已停用。请补齐配置后重新预览，或在上方逐笔勾选后走排除入口。
            </p>
          </el-card>

          <el-card v-if="orderImportResult" class="panel-card">
            <div class="panel-title">入账结果</div>
            <p>{{ orderImportResult.message }}</p>
            <p class="tip">批次号：{{ orderImportResult.importBatchId }}</p>
            <el-alert v-if="orderImportResult.overriddenRows > 0" type="warning" :closable="false" show-icon
                     :title="`本次按运营确认排除 ${orderImportResult.overriddenRows} 笔未归属订单，已记入操作日志`" />
            <div v-if="orderImportResult.failures && orderImportResult.failures.length > 0">
              <p class="tip warning-text">以下行入账失败，需单独补录：</p>
              <ul class="tip">
                <li v-for="f in orderImportResult.failures" :key="f">{{ f }}</li>
              </ul>
            </div>
          </el-card>
        </template>
      </el-tab-pane>

      <!--
        C20-6 后台手工销量录入。8/9 首场的<b>主用</b>销量入账通道。
        与订单导入的关键差别：不依赖 players.display_code（该字段在生产环境无写入入口，
        见 DEFECT-001），选手直接从下拉框选，商家编码只用于取单价。
        防御方向也相反：订单导入防「无意识丢失」，本页防「无意识多算」。
      -->
      <el-tab-pane label="销量录入" name="sales">
        <el-alert type="info" :closable="false" show-icon class="mb-12"
                  title="本页人气值按「商品原价 × 件数」折算，与订单表导入口径完全一致">
          录错可用<b>负数件数</b>冲销纠错，冲销同样留痕。每一笔都会写入操作日志，
          请如实填写操作人与原因。
        </el-alert>

        <div class="grid-two">
          <el-card class="panel-card">
            <div class="panel-title">商品原价配置</div>
            <p class="tip warning-text">
              录入前必须先配好原价。商家编码规则为<b>每位选手每款商品一个独立编码</b>，
              例如 P01-CARD、P01-PHOTO。未配原价的编码无法录入。
            </p>
            <el-form @submit.prevent label-width="96px">
              <el-form-item label="商家编码">
                <el-input v-model="priceForm.merchantCode" placeholder="P01-CARD" />
              </el-form-item>
              <el-form-item label="商品名称">
                <el-input v-model="priceForm.productName" placeholder="林一明信片" />
              </el-form-item>
              <el-form-item label="原价（元）">
                <el-input v-model="priceForm.unitPriceYuan" placeholder="19.9" />
              </el-form-item>
              <el-form-item label="状态">
                <el-select v-model="priceForm.status" style="width: 160px">
                  <el-option label="启用" value="active" />
                  <el-option label="停用" value="disabled" />
                </el-select>
              </el-form-item>
              <div class="form-actions">
                <el-button native-type="button" type="primary" @click="submitProductPrice">保存配置</el-button>
                <el-button native-type="button" @click="refreshProductPrices">刷新列表</el-button>
              </div>
            </el-form>
            <el-table :data="productPrices" size="small" style="width: 100%">
              <el-table-column prop="merchantCode" label="编码" width="120" />
              <el-table-column prop="productName" label="商品" />
              <el-table-column label="原价" width="90">
                <template #default="{ row }">{{ (row.unitPriceCent / 100).toFixed(2) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
                    {{ row.status === 'active' ? '启用' : '停用' }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <el-card class="panel-card">
            <div class="panel-title">录入销量</div>
            <el-form @submit.prevent label-width="96px">
              <el-form-item label="轮次">
                <el-select v-model="salesForm.roundId" style="width: 220px">
                  <el-option v-for="r in rounds" :key="r.roundId" :label="r.name" :value="r.roundId" />
                </el-select>
              </el-form-item>
              <el-form-item label="选手">
                <el-select v-model="salesForm.playerId" filterable style="width: 220px">
                  <el-option v-for="p in players" :key="p.playerId"
                             :label="`${p.number} 号 ${p.name}`" :value="p.playerId" />
                </el-select>
              </el-form-item>
              <el-form-item label="商品编码">
                <el-select v-model="salesForm.merchantCode" filterable style="width: 220px">
                  <el-option v-for="c in activePriceOptions" :key="c.merchantCode"
                             :label="`${c.merchantCode}　${c.productName}　${(c.unitPriceCent / 100).toFixed(2)}元`"
                             :value="c.merchantCode" />
                </el-select>
              </el-form-item>
              <el-form-item label="件数">
                <el-input v-model.number="salesForm.quantity" style="width: 160px" placeholder="30" />
                <span class="tip">负数为冲销纠错</span>
              </el-form-item>
              <el-form-item label="原因">
                <el-input v-model="salesForm.reason" placeholder="现场统计销量录入" />
              </el-form-item>
              <p v-if="salesPreviewText" class="tip">
                将折算人气值：<b>{{ salesPreviewText }}</b>
              </p>
              <!--
                件数达到服务端阈值量级时提前告知。这不是拦截（真正的防线在服务端），
                而是让运营在点提交之前就有机会自己发现多打了一位——
                弹窗后再改比提交前发现的心理成本高得多。
              -->
              <p v-if="salesQuantityLooksLarge" class="tip" style="color: #e6a23c">
                注意：本笔件数绝对值已超过 {{ SALES_QTY_HINT_THRESHOLD }} 件，提交后将要求二次确认。
                请先自行核对是否多打了一位数字。
              </p>
              <div class="form-actions">
                <el-button native-type="button" type="primary" :loading="salesSubmitting"
                           @click="submitManualSales(false)">提交录入</el-button>
              </div>
            </el-form>

            <!--
              needs_confirm 必须显式区别于「成功」：它代表<b>尚未入账</b>。
              若与成功混同，本该入账的销量会凭空消失。
            -->
            <el-alert v-if="salesConfirmReason" type="warning" :closable="false" show-icon
                      class="mb-12" title="这一笔尚未入账，请核对后再确认">
              <p>{{ salesConfirmReason }}</p>
              <div class="form-actions">
                <el-button native-type="button" type="danger" size="small"
                           :loading="salesSubmitting" @click="submitManualSales(true)">
                  我已核对，确认录入
                </el-button>
                <el-button native-type="button" size="small" @click="salesConfirmReason = ''">
                  取消
                </el-button>
              </div>
            </el-alert>

            <el-alert v-if="salesLastResult && salesLastResult.status === 'recorded'"
                      type="success" :closable="false" show-icon class="mb-12"
                      :title="`已入账：${salesLastResult.playerName} ${salesLastResult.productName} ${salesLastResult.quantity} 件`">
              折算人气 {{ salesLastResult.popularityValue.toLocaleString() }}，
              单价 {{ (salesLastResult.unitPriceCent / 100).toFixed(2) }} 元，
              该商品本轮累计 {{ salesLastResult.totalQuantityAfter }} 件。
            </el-alert>
            <el-alert v-if="salesLastResult && salesLastResult.status === 'duplicated'"
                      type="info" :closable="false" show-icon class="mb-12"
                      title="这一笔早已入账，未重复计算">
              若确实要再录一笔相同销量，请重新填写后提交。
            </el-alert>
          </el-card>
        </div>

        <el-card class="panel-card">
          <div class="panel-title">本轮销量合计（核对用）</div>
          <p class="tip warning-text">
            件数与人气值必须并列看：只看人气值无法区分「卖得多」与「单价配错」。
            件数<b>不跨商品相加</b>，因为不同商品单价不同，合并后的件数没有业务含义。
          </p>
          <div class="form-actions">
            <el-button native-type="button" @click="loadSalesSummary">刷新合计</el-button>
            <span class="tip">全场人气合计：<b>{{ (salesSummary?.grandTotalPopularity || 0).toLocaleString() }}</b></span>
          </div>
          <el-alert v-for="w in (salesSummary?.warnings || [])" :key="w"
                    type="warning" :closable="false" show-icon class="mb-12" :title="w" />
          <el-table :data="salesSummary?.players || []" size="small" style="width: 100%" row-key="playerId">
            <el-table-column type="expand">
              <template #default="{ row }">
                <el-table :data="row.products" size="small">
                  <el-table-column prop="merchantCode" label="商品编码" width="140" />
                  <el-table-column prop="productName" label="商品" />
                  <el-table-column prop="totalQuantity" label="净件数" width="90" />
                  <el-table-column label="单价" width="110">
                    <template #default="{ row: p }">
                      <span :class="{ 'warning-text': p.priceInconsistent }">
                        {{ (p.latestUnitPriceCent / 100).toFixed(2) }}
                        <template v-if="p.priceInconsistent">
                          （曾为 {{ (p.earliestUnitPriceCent / 100).toFixed(2) }}）
                        </template>
                      </span>
                    </template>
                  </el-table-column>
                  <el-table-column label="人气值" width="120">
                    <template #default="{ row: p }">{{ p.totalPopularity.toLocaleString() }}</template>
                  </el-table-column>
                  <el-table-column prop="entryCount" label="笔数" width="70" />
                </el-table>
              </template>
            </el-table-column>
            <el-table-column label="选手" width="180">
              <template #default="{ row }">{{ row.playerNumber }} 号 {{ row.playerName }}</template>
            </el-table-column>
            <el-table-column label="人气合计" width="140">
              <template #default="{ row }">{{ row.totalPopularity.toLocaleString() }}</template>
            </el-table-column>
            <el-table-column prop="entryCount" label="录入笔数" width="100" />
            <el-table-column label="异常" >
              <template #default="{ row }">
                <el-tag v-if="row.hasPriceInconsistency" type="warning" size="small">单价不一致</el-tag>
                <span v-else class="tip">正常</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="写真管理" name="photos">
        <div class="grid-two">
          <el-card class="panel-card">
            <div class="panel-title">上传写真</div>
            <p class="tip warning-text">仅上传清新/才艺/舞台风图片；禁止性感擦边素材。系统只接受 jpg/png/webp，禁止 SVG。</p>
            <el-form @submit.prevent label-width="90px">
              <el-form-item label="选手">
                <el-select v-model="photoUploadForm.playerId" filterable style="width: 260px">
                  <el-option v-for="player in players" :key="player.playerId" :label="`${player.displayCode} ${player.name}`" :value="player.playerId" />
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
                  <el-option v-for="player in players" :key="player.playerId" :label="`${player.displayCode} ${player.name}`" :value="player.playerId" />
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
    <el-dialog v-model="spyDialogVisible" title="开启卧底识破 / 切换目标" width="400px">
      <p style="margin-bottom: 20px; color: #666; font-size: 14px;">
        请选择识破阶段与目标：<br>
        阶段一：暂不指定目标（点赞入公共池）<br>
        阶段二：指定当前目标（点赞入该选手卧底人气）
      </p>
      <el-form label-width="80px">
        <el-form-item label="指定目标">
          <el-select v-model="spyDialogTargetId" filterable clearable placeholder="暂不指定目标 (阶段一)" style="width: 100%">
            <el-option v-for="p in players" :key="p.playerId" :label="`${p.number}号 ${p.name}`" :value="p.playerId" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="spyDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="openSpyModeWithTarget">确认提交</el-button>
        </span>
      </template>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { distributeTeam, getAdminBoard, getAdminHome, getGroupVoteSummary, getSuspicionStatus, manualAdjust, recordGroupVote, setCollectState, simulateInject } from './api/admin'
import { createPlayer, createRound, createTeam, listPlayerRounds, listPlayers, listRounds, listTeams, savePlayerRound, updateRoundStatus } from './api/basicData'
import { listPhotos, replacePhoto, setPhotoCover, updatePhotoStatus, uploadPhoto } from './api/photos'
import { generateTokens, exportTokens } from './api/tokens'
import {
  confirmOrderImport,
  confirmOrderImportWithOverride,
  listProductPrices,
  preflightOrderImport,
  previewOrderImport,
  saveProductPrice,
  type OrderImportPreview,
  type OrderRow
} from './api/orders'
import {
  getManualSalesSummary,
  newIdempotencyKey,
  recordManualSales,
  type ManualSalesEntryResult,
  type ManualSalesSummary
} from './api/sales'
import { getAdminToken, setAdminToken, clearAdminToken, setUnauthorizedHandler } from './api/http'

const activeTab = ref('monitor')

/**
 * 实验功能开关。C20-4C 订单导入已完成但按 Claude 2026-08-02 裁定暂不启用，
 * 默认隐藏以防运营在 8/9 直播现场误入该流程。
 * 需要验证或演示时，在地址栏追加 ?experimental=1 即可显示。
 * 注意：这只是防误入的界面开关，不构成权限控制——后端接口依旧可被直接调用。
 */
const showExperimental = ref(
  new URLSearchParams(window.location.search).get('experimental') === '1'
)
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
const bonusForm = reactive({ targetType: 'player', targetId: 1, roundId: 1, delta: 10, reason: '' })
const distributionForm = reactive<{ teamId: number | null; roundId: number | null; method: string; reason: string }>({ teamId: null, roundId: null, method: 'equal', reason: '彩排团队均分' })
const playerForm = reactive<{ name: string; displayCode: string }>({ name: '', displayCode: '' })
const teamForm = reactive({ name: '' })
const roundForm = reactive({ name: '', startTime: '', endTime: '', status: 'upcoming' })
const playerRoundForm = reactive<{ playerId: number | null; teamId: number | null; isSpy: boolean; playerStatus: string }>({ playerId: null, teamId: null, isSpy: false, playerStatus: 'normal' })
const photoFilter = reactive<{ playerId: number | null; status: string | null }>({ playerId: null, status: 'active' })
const photoUploadForm = reactive<{ playerId: number; isCover: boolean; sortOrder: number; file: File | null }>({ playerId: 1, isCover: true, sortOrder: 0, file: null })
const uploading = ref(false)
const generating = ref(false)
const tokenForm = reactive({ playerId: 1, photoAssetId: '', points: 100, count: 10, productSku: '' })
const tokenFormPhotos = ref<any[]>([])
const refundForm = reactive({ tokenId: '', reason: '' })
const lastBatchId = ref('')
const spyDialogVisible = ref(false)
const spyDialogTargetId = ref<number | null>(null)

// C20-3 群投票录入：幂等键在表单重置时预生成，连点重复提交会被后端幂等拦截
const groupVoteForm = reactive<{ roundId: number | null; playerId: number | null; votes: number | null; reason: string }>({ roundId: null, playerId: null, votes: null, reason: '' })
const groupVoteSummary = ref<any>({ items: [], totalVotes: 0 })
const groupVoteSubmitting = ref(false)
let groupVoteIdempotencyKey = generateGroupVoteKey()

function generateGroupVoteKey() {
  return `${Date.now()}_${Math.random().toString(36).substring(2, 10)}`
}

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

function resetPlayerForm() {
  playerForm.name = ''
  playerForm.displayCode = ''
}

function resetTeamForm() {
  teamForm.name = ''
}

async function refreshBasicData() {
  players.value = await listPlayers()
  teams.value = await listTeams()
  rounds.value = await listRounds()

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
  if (groupVoteForm.roundId == null) groupVoteForm.roundId = fallbackId
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

async function toggleSpyMode() {
  const willOpen = !suspicionStatus.value.open
  if (!willOpen) {
    await ElMessageBox.confirm('确认要关闭卧底识破投票吗？关闭后将切回 pool 模式。', '提示', { type: 'warning' })
    await runAction('操作成功', () => setCollectState(withOperator({
      mode: 'pool',
      targetId: null,
      roundId: home.value.roundId || 1
    })), refreshMonitor)
  } else {
    spyDialogTargetId.value = null
    spyDialogVisible.value = true
  }
}

async function openSpyModeWithTarget() {
  spyDialogVisible.value = false
  await runAction('操作成功', () => setCollectState(withOperator({
    mode: 'spy',
    targetId: spyDialogTargetId.value,
    roundId: home.value.roundId || 1
  })), refreshMonitor)
}

async function changeSpyTarget() {
  spyDialogTargetId.value = home.value.targetId || null
  spyDialogVisible.value = true
}

/** C20-3 群投票结果录入：二次确认 + 幂等防连点 + 提交后刷新累计表。 */
async function submitGroupVote() {
  if (groupVoteForm.roundId == null) {
    ElMessage.warning('请选择轮次')
    return
  }
  if (groupVoteForm.playerId == null) {
    ElMessage.warning('请选择选手')
    return
  }
  if (!groupVoteForm.votes) {
    ElMessage.warning('票数不能为空或 0（正数累加，负数冲销）')
    return
  }
  if (!groupVoteForm.reason.trim()) {
    ElMessage.warning('请填写原因（例：8/1粉丝群第一轮投票）')
    return
  }
  const player = players.value.find((p) => p.playerId === groupVoteForm.playerId)
  const actionText = groupVoteForm.votes > 0 ? `累加 ${groupVoteForm.votes} 票` : `冲销 ${-groupVoteForm.votes} 票`
  await ElMessageBox.confirm(`确认给【${player ? player.number + '号 ' + player.name : groupVoteForm.playerId}】${actionText}？将写入人气流水与操作日志。`, '群投票录入确认', { type: 'warning' })
  groupVoteSubmitting.value = true
  try {
    const result = await recordGroupVote(withOperator({
      roundId: groupVoteForm.roundId,
      playerId: groupVoteForm.playerId,
      votes: groupVoteForm.votes,
      reason: groupVoteForm.reason,
      idempotencyKey: groupVoteIdempotencyKey
    }))
    const outcome = result?.result || {}
    if (outcome.duplicated) {
      ElMessage.warning('重复提交已拦截（幂等），未重复记账')
    } else {
      ElMessage.success(`录入成功，该选手本轮累计 ${outcome.currentTotalVotes ?? '-'} 票`)
    }
    // 提交成功后更换幂等键并重置票数，保留轮次/选手方便连续录入
    groupVoteIdempotencyKey = generateGroupVoteKey()
    groupVoteForm.votes = null
    await refreshGroupVoteSummary()
  } catch (error: any) {
    ElMessage.error(error.message || '群投票录入失败')
  } finally {
    groupVoteSubmitting.value = false
  }
}

/** C20-3 刷新本轮各选手群投票累计票数。 */
async function refreshGroupVoteSummary() {
  if (groupVoteForm.roundId == null) return
  try {
    groupVoteSummary.value = await getGroupVoteSummary(groupVoteForm.roundId)
  } catch (error: any) {
    ElMessage.error(error.message || '累计票数查询失败')
  }
}

async function submitManualBonus() {
  await ElMessageBox.confirm(`确认给 ${bonusForm.targetType} ${bonusForm.targetId} 增加系数 ${bonusForm.delta}？`, '加成确认', { type: 'warning' })
  const idempotencyKey = `bonus_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`
  await runAction('加成成功', () => manualAdjust(withOperator({
    ...bonusForm,
    idempotencyKey
  })), refreshMonitor)
}

async function submitRefund() {
  if (!refundForm.tokenId) {
    ElMessage.error('请输入卡密')
    return
  }
  await ElMessageBox.confirm(`确认对卡密 ${refundForm.tokenId} 进行退款？（不可逆）`, '退款确认', { type: 'warning' })
  await runAction('退款成功', async () => {
    // 假设有 jsonPost，由于目前没引入 refund api，这里用 fetch 临时替代
    const token = localStorage.getItem('adminToken')
    const res = await fetch('/api/admin/refund', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Admin-Token': token || '' },
      body: JSON.stringify(withOperator({ tokenId: refundForm.tokenId, reason: refundForm.reason }))
    })
    const data = await res.json()
    if (data.code !== 0) throw new Error(data.message)
    return data
  }, async () => {
    refundForm.tokenId = ''
    refundForm.reason = ''
  })
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
  await refreshGroupVoteSummary()
  // 商品原价列表预先加载：运营开场前第一件事就是确认配价是否齐全。
  await refreshProductPrices()
  // 销量录入默认选中进行中的轮次，没有则选第一个。
  // 不默认为空是因为现场运营容易忘选轮次，而销量录错轮次会直接反映到排名。
  if (rounds.value.length > 0) {
    const running = rounds.value.find((r: any) => r.status === 'running')
    salesForm.roundId = (running || rounds.value[0]).roundId
    await loadSalesSummary()
  }
})

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
    await ElMessageBox.confirm(`当前未绑定写真，生成的卡密仅含人气值，是否继续？`, '警告', { type: 'warning' })
  } else {
    await ElMessageBox.confirm(`确认生成 ${tokenForm.count} 张卡密？（每张 ${tokenForm.points} 人气）`, '生成确认', { type: 'warning' })
  }
  generating.value = true
  const idempotencyKey = `gen_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`
  try {
    await runAction('卡密生成成功', async () => {
      const res = await generateTokens(withOperator({
        playerId: tokenForm.playerId,
        photoAssetId: tokenForm.photoAssetId,
        points: tokenForm.points,
        count: tokenForm.count,
        productSku: tokenForm.productSku,
        idempotencyKey
      }))
      lastBatchId.value = res.batchId
      return res
    }, async () => {})
  } finally {
    generating.value = false
  }
}

async function downloadCsv() {
  if (!lastBatchId.value) return
  try {
    await exportTokens(lastBatchId.value, operatorId.value)
  } catch (e: any) {
    ElMessage.error(e.message || '下载失败')
  }
}

// ===================== C20-4C 订单导入 =====================

const productPrices = ref<any[]>([])
const priceForm = reactive({
  merchantCode: '',
  productName: '',
  unitPriceYuan: '',
  status: 'active'
})
const orderFile = ref<File | null>(null)
const orderImportRoundId = ref<number | null>(null)
const orderPreview = ref<OrderImportPreview | null>(null)
/** 当前展示的结果是否来自前置检查。空跑结果必须在界面上自标识，
 *  否则运营会误以为已经导入过一次。 */
const orderPreviewIsPreflight = ref(false)
const orderImportResult = ref<any>(null)
const orderLoading = ref(false)
const orderRowFilter = ref('all')
const overrideSelection = ref<OrderRow[]>([])
const overrideReason = ref('')
const unattributedTableRef = ref<any>(null)

/** 分转元展示。全链路以分为单位做整数运算，仅在展示层除 100。 */
function yuan(cent: number | null | undefined): string {
  if (cent === null || cent === undefined) return '-'
  return `¥${(cent / 100).toFixed(2)}`
}

function validityLabel(validity: string): string {
  if (validity === 'valid') return '有效'
  if (validity === 'unattributed') return '未归属'
  return '无效'
}

function validityTagType(validity: string): string {
  if (validity === 'valid') return 'success'
  if (validity === 'unattributed') return 'danger'
  return 'info'
}

const unattributedRowList = computed<OrderRow[]>(() => {
  if (!orderPreview.value) return []
  return orderPreview.value.rows.filter((r) => r.validity === 'unattributed')
})

const filteredOrderRows = computed<OrderRow[]>(() => {
  if (!orderPreview.value) return []
  const rows = orderPreview.value.rows
  if (orderRowFilter.value === 'valid') return rows.filter((r) => r.validity === 'valid')
  if (orderRowFilter.value === 'invalid') return rows.filter((r) => r.validity === 'invalid')
  if (orderRowFilter.value === 'unattributed') return rows.filter((r) => r.validity === 'unattributed')
  if (orderRowFilter.value === 'aftersale') return rows.filter((r) => r.inAftersale)
  return rows
})

/** 普通确认只在无阻断且持有令牌时可用。前端先置灰是为了少一次无效点击，
 *  但真正的阻断在后端（409/40920）——前端置灰可被绕过，不能当作保障。 */
const canConfirmImport = computed(() => {
  const p = orderPreview.value
  if (!p || !p.previewToken) return false
  if (p.blockingErrors.length > 0) return false
  return !p.blockedByUnattributed
})

/** 排除提交要求：持有令牌、已填原因、且未归属行<b>全部</b>勾选。 */
const canSubmitOverride = computed(() => {
  const p = orderPreview.value
  if (!p || !p.previewToken) return false
  if (unattributedRowList.value.length === 0) return false
  if (!overrideReason.value.trim()) return false
  return overrideSelection.value.length === unattributedRowList.value.length
})

function onOrderFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  orderFile.value = input.files && input.files.length > 0 ? input.files[0] : null
  // 换文件必须作废旧预览：否则屏幕上是 A 文件的数字、手里是 B 文件的令牌。
  resetOrderPreview()
}

function resetOrderPreview() {
  orderPreview.value = null
  orderPreviewIsPreflight.value = false
  orderImportResult.value = null
  overrideSelection.value = []
  overrideReason.value = ''
  orderRowFilter.value = 'all'
}

async function refreshProductPrices() {
  try {
    productPrices.value = await listProductPrices()
  } catch (e: any) {
    ElMessage.error(e.message || '加载商品原价失败')
  }
}

async function submitProductPrice() {
  if (!priceForm.merchantCode.trim() || !priceForm.unitPriceYuan.trim()) {
    ElMessage.error('商家编码与单价必填')
    return
  }
  try {
    await saveProductPrice({
      merchantCode: priceForm.merchantCode.trim(),
      productName: priceForm.productName.trim(),
      unitPriceYuan: priceForm.unitPriceYuan.trim(),
      status: priceForm.status,
      operatorId: operatorId.value
    })
    ElMessage.success('原价已保存（仅影响此后导入，已入账订单不追溯）')
    await refreshProductPrices()
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  }
}

async function runPreflight() {
  if (!orderFile.value) {
    ElMessage.error('请先选择订单文件')
    return
  }
  orderLoading.value = true
  try {
    resetOrderPreview()
    orderPreview.value = await preflightOrderImport(orderFile.value, orderImportRoundId.value)
    orderPreviewIsPreflight.value = true
    ElMessage.success('前置检查完成：未写入任何数据')
  } catch (e: any) {
    ElMessage.error(e.message || '前置检查失败')
  } finally {
    orderLoading.value = false
  }
}

async function runPreview() {
  if (!orderFile.value) {
    ElMessage.error('请先选择订单文件')
    return
  }
  orderLoading.value = true
  try {
    resetOrderPreview()
    orderPreview.value = await previewOrderImport(orderFile.value, orderImportRoundId.value)
    orderPreviewIsPreflight.value = false
    if (orderPreview.value.blockedByUnattributed) {
      ElMessage.warning('存在未归属订单，已阻止普通确认入账')
    } else {
      ElMessage.success('预览完成，请按选手汇总核对后确认')
    }
  } catch (e: any) {
    ElMessage.error(e.message || '预览失败')
  } finally {
    orderLoading.value = false
  }
}

function onOverrideSelectionChange(rows: OrderRow[]) {
  overrideSelection.value = rows
}

function selectAllUnattributed() {
  const table = unattributedTableRef.value
  if (!table) return
  unattributedRowList.value.forEach((row) => table.toggleRowSelection(row, true))
}

async function submitConfirmImport() {
  const p = orderPreview.value
  if (!p || !p.previewToken) return
  await ElMessageBox.confirm(
    `将为 ${p.byPlayerDetail.length} 位选手计入合计 ${p.totalPopularity} 人气值（${p.totalQuantity} 件）。确认后令牌失效，不可重复导入。`,
    '确认入账',
    { type: 'warning' }
  )
  orderLoading.value = true
  try {
    orderImportResult.value = await confirmOrderImport({
      previewToken: p.previewToken,
      operatorId: operatorId.value
    })
    ElMessage.success('入账完成')
    // 令牌已消费，立即作废预览以防重复点击
    orderPreview.value = null
    await refreshProductPrices()
  } catch (e: any) {
    ElMessage.error(e.message || '入账失败')
  } finally {
    orderLoading.value = false
  }
}

async function submitConfirmOverride() {
  const p = orderPreview.value
  if (!p || !p.previewToken) return
  const subOrderNos = unattributedRowList.value.map((r) => r.subOrderNo)
  await ElMessageBox.confirm(
    `将排除 ${subOrderNos.length} 笔未归属订单（不计入任何选手人气），并将操作人与原因写入操作日志。此操作不可撤回。`,
    '确认排除未归属订单',
    { type: 'warning' }
  )
  orderLoading.value = true
  try {
    orderImportResult.value = await confirmOrderImportWithOverride({
      previewToken: p.previewToken,
      operatorId: operatorId.value,
      overrideSubOrderNos: subOrderNos,
      overrideReason: overrideReason.value.trim()
    })
    ElMessage.success('已入账并留痕')
    orderPreview.value = null
  } catch (e: any) {
    ElMessage.error(e.message || '排除入账失败')
  } finally {
    orderLoading.value = false
  }
}




// ===================== C20-6 后台手工销量录入 =====================

const salesForm = reactive({
  roundId: null as number | null,
  playerId: null as number | null,
  merchantCode: '',
  quantity: null as number | null,
  reason: '现场统计销量录入'
})
const salesSubmitting = ref(false)
const salesLastResult = ref<ManualSalesEntryResult | null>(null)
/** needs_confirm 的提示语。非空即表示「上一笔尚未入账，正等待运营确认」。 */
const salesConfirmReason = ref('')
const salesSummary = ref<ManualSalesSummary | null>(null)
/**
 * 幂等键在「开始一笔新录入」时生成一次，二次确认时<b>复用同一个键</b>。
 * 若二次确认时重新生成，运营连点确认按钮就会入账两笔——这正是幂等键要防的事。
 */
const salesIdempotencyKey = ref('')

/** 只有启用中的价格配置才能选，停用意味着该商品不该再产生人气。 */
const activePriceOptions = computed(() =>
  productPrices.value.filter((p: any) => p.status === 'active')
)

/**
 * 提交前的折算预览。让运营在点下按钮之前就看到件数与人气数量级，
 * 这是比服务端件数异常提示更早的一道自查——数字在眼前时更容易发现多打了一位。
 */
const salesPreviewText = computed(() => {
  const cfg = productPrices.value.find((p: any) => p.merchantCode === salesForm.merchantCode)
  const qty = salesForm.quantity
  if (!cfg || typeof qty !== 'number' || !Number.isFinite(qty) || qty === 0) return ''
  const popularity = cfg.unitPriceCent * qty * 10
  const sign = qty < 0 ? '冲销 ' : ''
  return `${sign}${(cfg.unitPriceCent / 100).toFixed(2)} 元 × ${Math.abs(qty)} 件 = ${popularity.toLocaleString()}`
})

/**
 * 件数是否已达服务端会拦下的量级（与后端 Claude 裁定 A1 的 200 件保持一致）。
 *
 * 前端硬编码这个数字是一个已知弱点：后端阈值可由环境变量调整，
 * 而前端不会跟着变，二者不一致时前端提示会与实际拦截行为错位。
 * 不做成接口下发是因为它仅用于提示强弱（文字颜色），不参与任何拦截判定——
 * 真正的防线在服务端。若日后阈值频繁调整，应改为从接口读取。
 */
const SALES_QTY_HINT_THRESHOLD = 200
const salesQuantityLooksLarge = computed(() => {
  const qty = salesForm.quantity
  return typeof qty === 'number' && Number.isFinite(qty) && Math.abs(qty) > SALES_QTY_HINT_THRESHOLD
})

async function submitManualSales(confirmed: boolean) {
  if (!salesForm.roundId) {
    ElMessage.error('请选择轮次')
    return
  }
  if (!salesForm.playerId) {
    ElMessage.error('请选择选手')
    return
  }
  if (!salesForm.merchantCode) {
    ElMessage.error('请选择商品编码')
    return
  }
  const qty = salesForm.quantity
  if (typeof qty !== 'number' || !Number.isFinite(qty) || qty === 0) {
    ElMessage.error('件数必须是非零整数，负数表示冲销')
    return
  }
  if (!Number.isInteger(qty)) {
    ElMessage.error('件数必须是整数')
    return
  }
  if (!salesForm.reason.trim()) {
    ElMessage.error('请填写原因，每一笔人气变更都必须可追溯到人')
    return
  }
  // 新的一笔才换幂等键；二次确认沿用上一次的键。
  if (!confirmed || !salesIdempotencyKey.value) {
    salesIdempotencyKey.value = newIdempotencyKey()
  }
  salesSubmitting.value = true
  try {
    const result = await recordManualSales({
      roundId: salesForm.roundId,
      playerId: salesForm.playerId,
      merchantCode: salesForm.merchantCode,
      quantity: qty,
      operatorId: operatorId.value,
      reason: salesForm.reason.trim(),
      idempotencyKey: salesIdempotencyKey.value,
      confirmed
    })
    salesLastResult.value = result
    if (result.status === 'needs_confirm') {
      // 关键：此时<b>尚未入账</b>，不能提示成功，也不能清空表单。
      salesConfirmReason.value = result.confirmReason || '请再次核对后确认'
      return
    }
    salesConfirmReason.value = ''
    if (result.status === 'duplicated') {
      ElMessage.info('这一笔早已入账，未重复计算')
    } else {
      ElMessage.success(
        `已入账：${result.playerName} ${result.productName} ${result.quantity} 件，` +
        `人气 ${result.popularityValue.toLocaleString()}`
      )
      salesForm.quantity = null
    }
    salesIdempotencyKey.value = ''
    await loadSalesSummary()
  } catch (e: any) {
    ElMessage.error(e.message || '录入失败')
  } finally {
    salesSubmitting.value = false
  }
}

async function loadSalesSummary() {
  if (!salesForm.roundId) return
  try {
    salesSummary.value = await getManualSalesSummary(salesForm.roundId)
  } catch (e: any) {
    ElMessage.error(e.message || '加载销量合计失败')
  }
}
</script>
