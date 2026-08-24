<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index'
import api from './api'

const token = ref(localStorage.getItem('smile_candidate_token'))
const active = ref('home')
const jobs = ref([]), recommendations = ref([]), invitations = ref([])
const candidateId = ref(1)
const login = reactive({ username: 'candidate', password: 'demo123' })
const resumeText = ref(''), resumeResult = ref(null), workspace = ref(null)
const optimization = ref(null), acceptedSuggestionIds = ref([])
const greetingResult = ref(null), targetJobId = ref(1), skillsText = ref('')
const previewVersion = ref(null)
const resumeForm = reactive(emptyResume())
const mock = reactive({ sessionId: null, question: '', answer: '', round: 0, report: null })
const official = reactive({ sessionId: null, question: '', answer: '', round: 0, done: false })
const currentVersion = computed(() => workspace.value?.currentVersion)
const quality = computed(() => currentVersion.value?.quality || {})

function emptyResume() {
  return { name: '', phone: '', email: '', city: '', desiredPosition: '', yearsOfExperience: 0,
    summary: '', skills: [], workExperience: '', projectExperience: '', education: '', certificates: '', sourceText: '' }
}
async function run(action) { try { return await action() } catch (error) { ElMessage.error(error.message) } }
async function signIn() { await run(async () => { const data = await api.post('/auth/login', login); localStorage.setItem('smile_candidate_token', data.token); token.value = data.token; await load() }) }
async function load() {
  await run(async () => {
    [jobs.value, invitations.value] = await Promise.all([api.get('/jobs?status=PUBLISHED'), api.get(`/ai-interviews/invitations?candidateId=${candidateId.value}`)])
    if (jobs.value.length && !jobs.value.some(job => job.id === targetJobId.value)) targetJobId.value = jobs.value[0].id
    await loadWorkspace()
  })
}
async function loadWorkspace() {
  workspace.value = await api.get(`/candidates/${candidateId.value}/resume-workspace`)
  const content = workspace.value.currentVersion?.content
  if (content) { Object.assign(resumeForm, emptyResume(), content); skillsText.value = (content.skills || []).join('，') }
}
function notifyImport(result) {
  active.value = 'resume'
  ElMessage.success(result.workspaceImport?.requiresConfirmation ? '解析完成，已生成待确认版本，请核对后发布' : '解析完成，已添加到在线主简历')
}
async function parse() {
  await run(async () => { resumeResult.value = await api.post('/resumes/parse-text', { candidateId: candidateId.value, text: resumeText.value }); await loadWorkspace(); notifyImport(resumeResult.value) })
}
async function upload(event) {
  const file = event.target.files[0]; if (!file) return
  const form = new FormData(); form.append('file', file)
  await run(async () => { resumeResult.value = await api.post(`/resumes/upload?candidateId=${candidateId.value}`, form); await loadWorkspace(); notifyImport(resumeResult.value) })
  event.target.value = ''
}
async function saveResume() {
  await run(async () => {
    const content = { ...resumeForm, yearsOfExperience: Number(resumeForm.yearsOfExperience || 0), skills: skillsText.value.split(/[,，、;；]+/).map(v => v.trim()).filter(Boolean) }
    workspace.value = await api.put(`/candidates/${candidateId.value}/resume-workspace`, { content, changeSummary: '候选人在在线简历编辑器中更新内容' })
    ElMessage.success('在线简历已保存为新版本')
  })
}
async function publishVersion(versionId) {
  await run(async () => { workspace.value = await api.post(`/candidates/${candidateId.value}/resume-workspace/versions/${versionId}/publish`); previewVersion.value = null; await loadWorkspace(); ElMessage.success('该导入版本已发布为在线主简历') })
}
async function optimizeResume() {
  await run(async () => {
    optimization.value = await api.post(`/candidates/${candidateId.value}/resume-workspace/optimize`, { targetJobId: targetJobId.value || null })
    acceptedSuggestionIds.value = optimization.value.suggestions.filter(item => item.operation === 'REPLACE').map(item => item.id)
    ElMessage.success('字段级优化建议已生成，请逐项确认')
  })
}
async function applyOptimization() {
  await run(async () => {
    await api.post(`/candidates/${candidateId.value}/resume-workspace/optimizations/${optimization.value.id}/apply`, { suggestionIds: acceptedSuggestionIds.value })
    optimization.value = null; acceptedSuggestionIds.value = []; await loadWorkspace(); ElMessage.success('已应用所选建议并创建新版本')
  })
}
async function generateResume(jobId = targetJobId.value) {
  await run(async () => {
    targetJobId.value = Number(jobId)
    await api.post(`/candidates/${candidateId.value}/resume-workspace/generate`, { targetJobId: Number(jobId), templateCode: 'ATS_STANDARD_V1' })
    await loadWorkspace(); active.value = 'resume'; ElMessage.success('岗位定制简历已生成，可下载后打印为 PDF')
  })
}
async function exportVersion(versionId) {
  await run(async () => {
    const blob = await api.get(`/candidates/${candidateId.value}/resume-workspace/versions/${versionId}/export`, { responseType: 'blob' })
    const url = URL.createObjectURL(blob), link = document.createElement('a'); link.href = url; link.download = `smileboss-resume-v${versionId}.html`; link.click(); URL.revokeObjectURL(url)
  })
}
async function generateGreeting(jobId = targetJobId.value) {
  await run(async () => {
    targetJobId.value = Number(jobId)
    greetingResult.value = await api.post(`/candidates/${candidateId.value}/resume-workspace/greetings`, { jobId: Number(jobId), tone: 'PROFESSIONAL', maximumCharacters: 120 })
    active.value = 'resume'; ElMessage.success('已生成基于真实简历证据的招呼语')
  })
}
async function copyGreeting(content) { try { await navigator.clipboard.writeText(content); ElMessage.success('招呼语已复制，请确认后使用') } catch { ElMessage.error('复制失败，请手动选择文本') } }
async function recommend() { await run(async () => { recommendations.value = await api.post(`/ai/candidates/${candidateId.value}/recommend-jobs`); active.value = 'jobs' }) }
async function startMock(jobId = 1) {
  await run(async () => { const data = await api.post('/mock-interviews', { candidateId: candidateId.value, jobId, type: 'TECHNICAL' }); Object.assign(mock, { sessionId: data.sessionId, question: data.question, round: data.round, answer: '', report: null }); active.value = 'mock' })
}
async function answerMock() {
  await run(async () => { const data = await api.post(`/mock-interviews/${mock.sessionId}/answers`, { answer: mock.answer }); mock.answer = ''; if (data.action === 'FINISH') { mock.report = data.report; mock.question = '' } else { mock.question = data.question; mock.round = data.round } })
}
async function startOfficial(invitation) {
  await run(async () => { const data = await api.post(`/ai-interviews/invitations/${invitation.id}/start`, { consent: true }); Object.assign(official, { sessionId: data.sessionId, question: data.question, round: data.round, answer: '', done: false }); active.value = 'official' })
}
async function answerOfficial() {
  await run(async () => { const data = await api.post(`/ai-interviews/sessions/${official.sessionId}/answers`, { answer: official.answer }); official.answer = ''; if (data.action === 'FINISH') { official.done = true; official.question = ''; await load() } else { official.question = data.question; official.round = data.round } })
}
function logout() { localStorage.removeItem('smile_candidate_token'); token.value = null }
onMounted(() => token.value && load())
</script>

<template>
  <div v-if="!token" class="login">
    <div class="login-copy"><div class="logo">S</div><span>SmileBoss AI</span><h1>找到真正适合你的工作</h1><p>完善简历、发现机会、练习面试。每一项 AI 建议都有明确依据，最终选择始终属于你。</p></div>
    <el-card class="login-box"><h2>候选人登录</h2><p>继续你的求职旅程</p><el-form label-position="top"><el-form-item label="用户名"><el-input v-model="login.username" size="large" /></el-form-item><el-form-item label="密码"><el-input v-model="login.password" type="password" show-password size="large" @keyup.enter="signIn" /></el-form-item><el-button type="primary" size="large" @click="signIn">登录</el-button></el-form><small>演示账号 candidate / demo123</small></el-card>
  </div>
  <div v-else>
    <header><div class="wordmark"><div class="logo mini">S</div><b>SmileBoss</b></div><nav><button v-for="item in [{k:'home',n:'首页'},{k:'resume',n:'AI 简历工作台'},{k:'jobs',n:'岗位推荐'},{k:'mock',n:'模拟面试'},{k:'official',n:'AI 面试'}]" :key="item.k" :class="{active:active===item.k}" @click="active=item.k">{{ item.n }}</button></nav><button class="exit" @click="logout">退出</button></header>
    <main>
      <section v-if="active==='home'" class="hero"><div><span class="pill">AI CAREER COPILOT</span><h1>让好机会，<br><em>看见真实的你。</em></h1><p>从 PDF 导入、在线简历和岗位定制，到个性化招呼语与面试练习，所有 AI 内容都由你确认。</p><div class="hero-actions"><el-button type="primary" size="large" @click="active='resume'">打开 AI 简历工作台</el-button><el-button size="large" @click="recommend">查看岗位推荐</el-button></div></div><div class="hero-card"><div class="orbit one">PDF 转在线简历</div><div class="orbit two">岗位定制</div><div class="orbit three">个性化沟通</div><div class="core"><b>AI</b><span>求职助手</span></div></div></section>
      <section v-if="active==='home'" class="feature-grid"><article><span>01</span><h3>在线主简历</h3><p>PDF 自动解析成可编辑字段，所有修改都有版本并支持回溯。</p></article><article><span>02</span><h3>岗位定制简历</h3><p>只使用已确认事实，突出与目标岗位匹配的技能和经历。</p></article><article><span>03</span><h3>个性化招呼语</h3><p>根据简历证据和岗位要求生成，确认后复制，不会自动群发。</p></article></section>

      <section v-if="active==='resume'" class="page resume-workbench">
        <div class="title inline"><div><span>RESUME COPILOT</span><h1>AI 简历工作台</h1><p>主简历是事实来源；PDF 导入和 AI 优化不会静默覆盖已有内容。</p></div><el-button type="primary" @click="saveResume">保存为新版本</el-button></div>
        <div v-if="workspace" class="quality-grid"><article><strong>{{ quality.overall || 0 }}</strong><span>综合质量</span></article><article><strong>{{ quality.completeness || 0 }}</strong><span>完整度</span></article><article><strong>{{ quality.evidence || 0 }}</strong><span>事实证据度</span></article><article><strong>{{ quality.atsReadability || 0 }}</strong><span>ATS 可读性</span></article></div>
        <div class="workbench-grid">
          <div class="card resume-editor"><h3>在线主简历</h3><div class="form-grid"><label>姓名<el-input v-model="resumeForm.name" /></label><label>目标职位<el-input v-model="resumeForm.desiredPosition" /></label><label>手机号<el-input v-model="resumeForm.phone" /></label><label>邮箱<el-input v-model="resumeForm.email" /></label><label>城市<el-input v-model="resumeForm.city" /></label><label>工作年限<el-input v-model="resumeForm.yearsOfExperience" type="number" /></label></div><label>专业技能<el-input v-model="skillsText" placeholder="使用逗号分隔，如 Java，Spring Boot，MySQL" /></label><label>个人概述<el-input v-model="resumeForm.summary" type="textarea" :rows="4" /></label><label>工作经历<el-input v-model="resumeForm.workExperience" type="textarea" :rows="6" /></label><label>项目经历<el-input v-model="resumeForm.projectExperience" type="textarea" :rows="6" /></label><label>教育经历<el-input v-model="resumeForm.education" type="textarea" :rows="4" /></label><label>证书与认证<el-input v-model="resumeForm.certificates" type="textarea" :rows="3" /></label><el-button type="primary" class="wide" @click="saveResume">保存在线简历</el-button></div>
          <div class="workbench-side">
            <div class="card"><h3>从文件更新在线简历</h3><label class="upload compact-upload"><input type="file" accept=".pdf,.doc,.docx,.txt,.md" @change="upload"><b>↑</b><span>选择 PDF / DOCX / TXT</span><small>文本型 PDF 可直接导入；扫描件仍需 OCR</small></label><div class="or">或者粘贴简历文本</div><el-input v-model="resumeText" type="textarea" :rows="6" placeholder="粘贴简历原文……" /><el-button class="wide" @click="parse">解析并生成在线版本</el-button><div v-if="resumeResult" class="import-result"><b>解析完整度 {{ resumeResult.completeness.score }}</b><span>{{ resumeResult.workspaceImport?.requiresConfirmation ? '已创建待确认版本' : '已发布到在线简历' }}</span></div></div>
            <div class="card"><h3>目标岗位</h3><select v-model.number="targetJobId" class="native-select"><option v-for="job in jobs" :key="job.id" :value="job.id">{{ job.title }} · {{ job.city }}</option></select><div class="action-stack"><el-button type="primary" @click="optimizeResume">生成字段级优化建议</el-button><el-button @click="generateResume()">一键生成岗位简历</el-button><el-button @click="generateGreeting()">生成招呼语</el-button></div></div>
          </div>
        </div>
        <div v-if="optimization" class="card suggestion-panel"><div class="panel-heading"><div><h3>AI 字段级优化建议</h3><p>质量分预估：{{ optimization.scoreBefore }} → {{ optimization.scoreAfter }}。高风险事实只提示补充，不会自动编造。</p></div><el-button type="primary" @click="applyOptimization">应用选中建议</el-button></div><article v-for="item in optimization.suggestions" :key="item.id" class="suggestion-item"><input v-if="item.operation==='REPLACE'" v-model="acceptedSuggestionIds" type="checkbox" :value="item.id"><div><div class="suggestion-title"><b>{{ item.section }}</b><el-tag :type="item.riskLevel==='HIGH'?'danger':'warning'">{{ item.riskLevel }}</el-tag><span>{{ item.operation }}</span></div><p>{{ item.reason }}</p><div v-if="item.after" class="diff"><del>{{ item.before || '原内容为空' }}</del><ins>{{ item.after }}</ins></div><small>依据：{{ item.evidence }}</small></div></article></div>
        <div v-if="greetingResult" class="card greeting-panel"><h3>{{ greetingResult.jobTitle }} · 个性化招呼语</h3><p>{{ greetingResult.notice }}</p><article v-for="variant in greetingResult.variants" :key="variant.style"><div><b>{{ variant.style }}</b><small>{{ variant.characterCount }} 字</small></div><p>{{ variant.content }}</p><el-button size="small" @click="copyGreeting(variant.content)">复制</el-button></article><small>事实依据：{{ greetingResult.evidence.join('；') }}</small></div>
        <div v-if="previewVersion" class="card import-preview"><div class="panel-heading"><div><h3>导入版本 V{{ previewVersion.versionNo }} 预览</h3><p>新文件中的空字段已经保留当前值；请检查冲突内容后再发布。</p></div><el-button type="primary" @click="publishVersion(previewVersion.id)">确认并发布</el-button></div><div class="preview-grid"><article><b>个人概述</b><p>{{ previewVersion.content.summary || '待补充' }}</p></article><article><b>专业技能</b><p>{{ previewVersion.content.skills.join('、') || '待补充' }}</p></article><article><b>工作经历</b><p>{{ previewVersion.content.workExperience || '待补充' }}</p></article><article><b>项目经历</b><p>{{ previewVersion.content.projectExperience || '待补充' }}</p></article><article><b>教育经历</b><p>{{ previewVersion.content.education || '待补充' }}</p></article></div></div>
        <div v-if="workspace" class="card version-panel"><h3>简历版本</h3><p>当前发布版本 V{{ currentVersion?.versionNo }}。PDF 导入不会覆盖当前内容，确认后才发布。</p><div class="version-list"><article v-for="version in workspace.versions" :key="version.id"><div><b>V{{ version.versionNo }} · {{ version.versionType }}</b><span>{{ version.sourceType }} · {{ version.changeSummary }}</span></div><div class="version-actions"><el-tag v-if="version.id===workspace.currentVersionId" type="success">当前版本</el-tag><el-button v-else-if="version.versionType==='MASTER' && version.sourceType.includes('IMPORT')" size="small" @click="previewVersion=version">查看并确认</el-button><el-button size="small" @click="exportVersion(version.id)">下载可打印版</el-button></div></article></div></div>
      </section>

      <section v-if="active==='jobs'" class="page"><div class="title inline"><div><span>JOB MATCHING</span><h1>为你推荐的岗位</h1><p>匹配分和账号活跃度完全分开，活跃度不会降低你的能力评价。</p></div><el-button type="primary" @click="recommend">重新匹配</el-button></div><div class="jobs"><article v-for="job in (recommendations.length?recommendations:jobs)" :key="job.jobId||job.id"><div class="job-top"><div><small>{{ job.city }}</small><h2>{{ job.jobTitle||job.title }}</h2></div><div v-if="job.score" class="score">{{ job.score }}<small>匹配分</small></div></div><p>{{ job.reason||job.description }}</p><div class="tags"><el-tag v-for="skill in (job.matchedSkills||String(job.required_skills||'').split(','))" :key="skill" type="success">{{ skill }}</el-tag><el-tag v-for="skill in job.missingSkills" :key="skill" type="warning">待核实 {{ skill }}</el-tag></div><div class="job-actions"><el-button @click="generateGreeting(job.jobId||job.id)">生成招呼语</el-button><el-button @click="generateResume(job.jobId||job.id)">生成岗位简历</el-button><el-button @click="startMock(job.jobId||job.id)">模拟面试</el-button></div></article></div></section>
      <section v-if="active==='mock'" class="page interview-page"><div class="title"><span>PRACTICE INTERVIEW</span><h1>AI 模拟面试</h1><p>练习结果归你本人，不会自动提供给招聘企业。</p></div><div v-if="!mock.sessionId" class="start-card"><div class="mic">✦</div><h2>准备好开始一次 5 题模拟面试了吗？</h2><p>建议预留 15 分钟，用 STAR 结构回答并尽量提供真实数据。</p><el-button type="primary" size="large" @click="startMock(1)">开始模拟面试</el-button></div><div v-else-if="!mock.report" class="chat-card"><div class="progress"><span>问题 {{ mock.round }} / 5</span><el-progress :percentage="mock.round*20" :show-text="false" /></div><div class="ai-question"><div class="ai-avatar">AI</div><p>{{ mock.question }}</p></div><el-input v-model="mock.answer" type="textarea" :rows="8" /><el-button type="primary" size="large" :disabled="!mock.answer.trim()" @click="answerMock">提交回答</el-button></div><div v-else class="report-card"><div class="report-score"><strong>{{ mock.report.score }}</strong><span>{{ mock.report.level }}</span></div><h2>模拟面试完成</h2><p>{{ mock.report.notice }}</p><ul><li v-for="item in mock.report.improvements" :key="item">{{ item }}</li></ul><el-button @click="mock.sessionId=null;mock.report=null">再练一次</el-button></div></section>
      <section v-if="active==='official'" class="page interview-page"><div class="title"><span>OFFICIAL AI INTERVIEW</span><h1>企业 AI 初面</h1><p>正式面试结果只作招聘辅助，并由企业招聘人员人工审核。</p></div><div v-if="!official.sessionId" class="invites"><article v-for="invitation in invitations" :key="invitation.id"><div><el-tag>{{ invitation.status }}</el-tag><h2>{{ invitation.job_title }}</h2><p>{{ invitation.title }} · {{ invitation.duration_minutes }} 分钟</p></div><el-button v-if="invitation.status==='INVITED'" type="primary" @click="startOfficial(invitation)">阅读授权并开始</el-button></article><div v-if="!invitations.length" class="placeholder">当前没有 AI 面试邀请</div></div><div v-else-if="!official.done" class="chat-card official"><el-alert title="这是一场正式 AI 初面。请基于真实经历回答；系统不会通过表情、声音或敏感属性评价你。" type="info" :closable="false" /><div class="progress"><span>正式问题 {{ official.round }} / 6</span><el-progress :percentage="official.round*100/6" :show-text="false" /></div><div class="ai-question"><div class="ai-avatar">AI</div><p>{{ official.question }}</p></div><el-input v-model="official.answer" type="textarea" :rows="8" /><el-button type="primary" size="large" :disabled="!official.answer.trim()" @click="answerOfficial">提交并继续</el-button></div><div v-else class="report-card"><div class="done">✓</div><h2>面试已提交</h2><p>感谢你的认真回答。AI 正在整理带证据的辅助报告，企业招聘人员审核后会更新招聘进度。</p><el-button @click="official.sessionId=null;load()">返回邀请列表</el-button></div></section>
    </main><footer>SmileBoss AI · AI 辅助招聘，不替代人的最终判断</footer>
  </div>
</template>
