<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index'
import { ElMessageBox } from 'element-plus/es/components/message-box/index'
import api from '../api'

const loading = ref(false)
const runs = ref([])
const current = ref(null)
const form = reactive({ question: '每个城市有多少候选人？', execute: true, maxRows: 200 })

const execution = computed(() => current.value?.executions?.at(-1) || null)
const resultRows = computed(() => execution.value?.result_json || [])
const resultColumns = computed(() => execution.value?.columns_json || [])
const statusStep = computed(() => {
  const status = current.value?.status
  if (['REJECTED', 'FAILED'].includes(status)) return 2
  if (status === 'WAITING_HUMAN') return 3
  if (status === 'COMPLETED') return 5
  if (status === 'VALIDATED') return 4
  return 1
})

async function loadRuns() {
  try { runs.value = await api.get('/text-to-sql/queries') }
  catch (e) { ElMessage.error(e.message) }
}

async function ask(execute = true) {
  loading.value = true
  try {
    current.value = await api.post('/text-to-sql/queries', { ...form, execute })
    await loadRuns()
    const message = current.value.status === 'WAITING_HUMAN'
      ? '检测到敏感数据，已提交人工审核'
      : current.value.status === 'COMPLETED' ? '查询已安全执行' : 'SQL 已生成并完成校验'
    ElMessage.success(message)
  } catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}

async function openRun(row) {
  loading.value = true
  try { current.value = await api.get(`/text-to-sql/queries/${row.id}`) }
  catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}

async function decide(decision) {
  const action = decision === 'APPROVE' ? '批准执行' : '拒绝查询'
  const input = await ElMessageBox.prompt(`请输入${action}的审核说明`, '敏感查询人工审核', {
    inputType: 'textarea', confirmButtonText: action
  }).catch(() => null)
  if (!input) return
  loading.value = true
  try {
    current.value = await api.post(`/text-to-sql/queries/${current.value.id}/decisions`, {
      decision, comment: input.value
    })
    await loadRuns()
    ElMessage.success(`已${action}`)
  } catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}

function tagType(status) {
  return ({ COMPLETED: 'success', VALIDATED: 'success', WAITING_HUMAN: 'warning', REJECTED: 'danger', FAILED: 'danger' })[status] || 'info'
}

onMounted(loadRuns)
</script>

<template>
  <div class="sql-console" v-loading="loading">
    <div class="sql-hero panel">
      <div>
        <span class="sql-eyebrow">GOVERNED TEXT-TO-SQL</span>
        <h2>用自然语言安全查询招聘数据</h2>
        <p>SQL 生成只是中间步骤。系统还会执行表级白名单、危险语句拦截、敏感字段分级、EXPLAIN 和行数限制。</p>
      </div>
      <div class="guard-badges"><span>SELECT ONLY</span><span>MAX 1000 ROWS</span><span>HITL FOR L2</span></div>
    </div>

    <div class="sql-layout">
      <section class="panel ask-panel">
        <div class="panel-head"><div><h3>向招聘数据提问</h3><p>可询问职位、候选人、推荐、面试和活跃度聚合指标</p></div></div>
        <el-input v-model="form.question" type="textarea" :rows="4" placeholder="例如：每个职位的推荐平均分是多少？" />
        <div class="examples">
          <button @click="form.question='每个城市有多少候选人？'">城市候选人数</button>
          <button @click="form.question='每个职位的推荐平均分是多少？'">推荐平均分</button>
          <button @click="form.question='列出候选人的电话和邮箱'">敏感查询演示</button>
        </div>
        <div class="ask-actions">
          <el-input-number v-model="form.maxRows" :min="1" :max="1000" />
          <span>最大返回行数</span>
          <div class="spacer" />
          <el-button @click="ask(false)">仅生成与校验</el-button>
          <el-button type="primary" @click="ask(true)">生成并安全执行</el-button>
        </div>
      </section>

      <section class="panel run-list">
        <div class="panel-head"><div><h3>最近运行</h3><p>每次生成、校验、审批和执行均可追溯</p></div><el-button link @click="loadRuns">刷新</el-button></div>
        <div v-if="runs.length" class="run-items">
          <button v-for="r in runs.slice(0,8)" :key="r.id" :class="{ selected: current?.id===r.id }" @click="openRun(r)">
            <span><b>#{{ r.id }} {{ r.question }}</b><small>{{ r.created_at }} · {{ r.risk_level || '分析中' }}</small></span>
            <el-tag size="small" :type="tagType(r.status)">{{ r.status }}</el-tag>
          </button>
        </div>
        <div v-else class="sql-empty">尚无智能问数记录</div>
      </section>
    </div>

    <template v-if="current">
      <section class="panel trace-panel">
        <div class="trace-title">
          <div><h3>执行链路 · Query #{{ current.id }}</h3><p>Trace {{ current.trace_id }}</p></div>
          <div><el-tag :type="tagType(current.status)" size="large">{{ current.status }}</el-tag><el-tag type="info" size="large">风险 {{ current.risk_level }}</el-tag></div>
        </div>
        <el-steps :active="statusStep" finish-status="success" align-center>
          <el-step title="理解问题" description="意图与指标" />
          <el-step title="生成 SQL" description="模型/规则候选" />
          <el-step title="安全校验" description="白名单与 EXPLAIN" />
          <el-step title="人工审核" description="L2/L3 必经" />
          <el-step title="只读执行" description="超时与行数限制" />
        </el-steps>
      </section>

      <div class="sql-layout detail-layout">
        <section class="panel">
          <div class="panel-head"><div><h3>最终 SQL</h3><p>{{ current.candidates?.[0]?.rationale }}</p></div></div>
          <pre class="sql-code">{{ current.final_sql || current.generated_sql }}</pre>
          <div class="validation-list">
            <article v-for="v in current.validations" :key="v.id">
              <el-tag :type="v.passed ? 'success' : 'danger'">{{ v.passed ? 'PASS' : 'BLOCK' }}</el-tag>
              <div><b>{{ v.validator_code }}</b><small>{{ v.details_json?.message || v.details_json?.warnings?.join('；') || '校验完成' }}</small></div>
            </article>
          </div>
        </section>

        <section class="panel review-card" :class="{ waiting: current.status==='WAITING_HUMAN' }">
          <div class="panel-head"><div><h3>风险与人工审核</h3><p>模型无权绕过确定性的执行策略</p></div></div>
          <template v-if="current.status==='WAITING_HUMAN'">
            <el-alert title="该查询涉及个人敏感字段，已暂停执行" type="warning" :closable="false" show-icon />
            <dl><dt>策略</dt><dd>{{ current.approval?.policy_code }}</dd><dt>审核组</dt><dd>{{ current.approval?.reviewer_group }}</dd><dt>敏感字段</dt><dd>{{ current.approval?.request_payload_json?.sensitiveColumns?.join(', ') }}</dd><dt>到期时间</dt><dd>{{ current.approval?.due_at }}</dd></dl>
            <div class="review-actions"><el-button @click="decide('REJECT')">拒绝</el-button><el-button type="primary" @click="decide('APPROVE')">批准并执行</el-button></div>
          </template>
          <template v-else>
            <div class="risk-summary"><strong>{{ current.risk_level }}</strong><span>{{ current.risk_level==='L0' ? '聚合数据，可自动执行' : current.risk_level==='L1' ? '普通明细，受行数限制' : '敏感数据，需要人工审核' }}</span></div>
          </template>
        </section>
      </div>

      <section v-if="execution" class="panel result-panel">
        <div class="panel-head"><div><h3>查询结果</h3><p>{{ execution.row_count }} 行 · {{ execution.duration_ms }} ms <span v-if="execution.truncated_flag">· 已截断</span></p></div></div>
        <el-table :data="resultRows" max-height="430" stripe>
          <el-table-column v-for="column in resultColumns" :key="column" :prop="column" :label="column" min-width="150" show-overflow-tooltip />
        </el-table>
      </section>
      <el-alert v-if="current.error_message" :title="current.error_message" type="error" :closable="false" show-icon />
    </template>
  </div>
</template>

<style scoped>
.sql-console{display:grid;gap:20px}.sql-hero{display:flex;justify-content:space-between;align-items:center;color:#fff;border:0;background:radial-gradient(circle at 85% 10%,#14b8a655,transparent 34%),linear-gradient(135deg,#0f172a,#134e4a)}.sql-hero h2{margin:8px 0;font-size:26px}.sql-hero p{color:#cbd5e1;max-width:780px;line-height:1.7}.sql-eyebrow{font-size:11px;letter-spacing:2px;color:#5eead4;font-weight:800}.guard-badges{display:flex;gap:8px;flex-wrap:wrap;justify-content:flex-end}.guard-badges span{font-size:11px;padding:8px 10px;border:1px solid #5eead455;border-radius:99px;background:#ffffff0d;color:#ccfbf1}.sql-layout{display:grid;grid-template-columns:1.35fr 1fr;gap:20px}.ask-panel textarea{font-family:inherit}.examples{display:flex;gap:8px;flex-wrap:wrap;margin:12px 0}.examples button{border:0;border-radius:99px;background:#ecfeff;color:#0f766e;padding:7px 11px;cursor:pointer}.ask-actions{display:flex;align-items:center;gap:10px;margin-top:16px;color:#64748b;font-size:13px}.spacer{flex:1}.run-items{display:grid;gap:8px;max-height:255px;overflow:auto}.run-items>button{display:flex;justify-content:space-between;align-items:center;gap:12px;text-align:left;border:1px solid #e8edf5;background:#fafcff;border-radius:10px;padding:11px;cursor:pointer}.run-items>button.selected{border-color:#2dd4bf;background:#f0fdfa}.run-items span{min-width:0}.run-items b{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:350px}.run-items small{display:block;color:#94a3b8;margin-top:5px}.sql-empty{min-height:180px;display:grid;place-items:center;color:#94a3b8}.trace-title{display:flex;justify-content:space-between;align-items:center;margin-bottom:24px}.trace-title h3{margin:0}.trace-title p{margin:5px 0 0}.trace-title>div:last-child{display:flex;gap:8px}.detail-layout{grid-template-columns:1.5fr 1fr}.sql-code{white-space:pre-wrap;word-break:break-word;background:#0f172a;color:#a7f3d0;padding:18px;border-radius:12px;line-height:1.7;min-height:110px}.validation-list{display:grid;gap:8px}.validation-list article{display:flex;gap:10px;align-items:center;padding:10px;border:1px solid #eef2f7;border-radius:9px}.validation-list b,.validation-list small{display:block}.validation-list small{margin-top:3px;color:#64748b}.review-card.waiting{border-color:#fbbf24;background:#fffbeb}.review-card dl{display:grid;grid-template-columns:90px 1fr;gap:10px;font-size:13px}.review-card dt{color:#64748b}.review-card dd{margin:0;word-break:break-word}.review-actions{display:flex;justify-content:flex-end;margin-top:18px}.risk-summary{min-height:180px;display:grid;place-content:center;text-align:center}.risk-summary strong{font-size:55px;color:#0f766e}.risk-summary span{color:#64748b}.result-panel{overflow:hidden}@media(max-width:1100px){.sql-layout,.detail-layout{grid-template-columns:1fr}.sql-hero{align-items:flex-start;gap:20px;flex-direction:column}.guard-badges{justify-content:flex-start}}@media(max-width:680px){.ask-actions{align-items:flex-start;flex-wrap:wrap}.spacer{display:none}.trace-panel :deep(.el-step__description){display:none}}
</style>
