<template>
  <div class="page-wrapper">
    <!-- 页头：返回 GDPR 页 + 编辑/保存操作 -->
    <el-page-header class="page-header" @back="goBack">
      <template #content>
        <span class="page-title">隐私政策全文</span>
      </template>
      <template #extra>
        <el-button v-if="!editing" type="primary" @click="startEdit">编辑</el-button>
        <template v-else>
          <el-button @click="cancelEdit">取消</el-button>
          <el-button @click="loadAppContent">载入 APP 内置文案</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </template>
      </template>
    </el-page-header>

    <el-card shadow="never" class="policy-card">
      <!-- 元信息：版本 / 生效日期 / 当前语言的来源与字数 -->
      <div class="meta-row">
        <span class="meta-item">版本：<b>{{ policy.version || '—' }}</b></span>
        <span class="meta-item">生效日期：<b>{{ policy.effectiveDate || '—' }}</b></span>
        <el-tag size="small" :type="currentFromApp ? 'info' : 'success'">{{ sourceLabel }}</el-tag>
        <span class="meta-count">{{ paragraphCount }} 段 / {{ currentContent.length }} 字符</span>
      </div>

      <el-alert type="info" :closable="false" show-icon class="tip">
        <template #title>
          中文 / 英文各维护一份正文。保存后 APP 端打开隐私政策页会按当前语言实时读取；某语言为空时 APP 回退到另一种语言，再失败才用客户端内置文案。
        </template>
      </el-alert>

      <!-- 中英双语切换 -->
      <el-tabs v-model="activeLang" class="lang-tabs">
        <el-tab-pane label="中文" name="zh">
          <el-input
            v-if="editing"
            v-model="contentZh"
            type="textarea"
            :rows="26"
            resize="vertical"
            class="editor"
            placeholder="请输入中文隐私政策正文"
          />
          <div v-else class="policy-body">
            <p v-for="(line, idx) in zhParagraphs" :key="idx" class="policy-line">{{ line }}</p>
          </div>
        </el-tab-pane>
        <el-tab-pane label="English" name="en">
          <el-input
            v-if="editing"
            v-model="contentEn"
            type="textarea"
            :rows="26"
            resize="vertical"
            class="editor"
            placeholder="Enter the English privacy policy text"
          />
          <div v-else class="policy-body">
            <p v-for="(line, idx) in enParagraphs" :key="idx" class="policy-line">{{ line }}</p>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getActivePolicy, updateGdprPolicy, createGdprPolicy } from '../api/admin'
import {
  PRIVACY_POLICY_TEXT_ZH,
  PRIVACY_POLICY_TEXT_EN,
  PRIVACY_POLICY_TITLE_ZH,
  PRIVACY_POLICY_TITLE_EN,
} from '../constants/privacyPolicyContent'

const router = useRouter()

const policy = ref({ id: null, title: '', version: '', effectiveDate: '' })
const contentZh = ref('')
const contentEn = ref('')
const activeLang = ref('zh')
const editing = ref(false)
const saving = ref(false)
// 各语言正文是否来自 APP 内置文案（政策库为空/只有历史摘要时的兜底）
const zhFromApp = ref(false)
const enFromApp = ref(false)

// 历史政策库中可能只存了一条摘要（例如 24 字的占位说明），不是完整正文。
// 低于该长度时改用 APP 内置全文，保证「查看全文」页始终展示完整正文。
const MIN_FULL_POLICY_LENGTH = 2000

const currentContent = computed(() => (activeLang.value === 'zh' ? contentZh.value : contentEn.value))
const currentFromApp = computed(() => (activeLang.value === 'zh' ? zhFromApp.value : enFromApp.value))
const zhParagraphs = computed(() => contentZh.value.split('\n').filter((line) => line.trim()))
const enParagraphs = computed(() => contentEn.value.split('\n').filter((line) => line.trim()))
const paragraphs = computed(() => (activeLang.value === 'zh' ? zhParagraphs.value : enParagraphs.value))
const paragraphCount = computed(() => paragraphs.value.length)
const sourceLabel = computed(() => (currentFromApp.value ? 'APP 内置文案' : '后台政策库'))

// 读取当前生效政策的中英双语文案；对应语言为空或只有摘要时回退到 APP 内置全文
async function load() {
  let loaded = false
  try {
    const data = await getActivePolicy()
    if (data && data.id) {
      loaded = true
      policy.value = {
        id: data.id,
        title: data.title || PRIVACY_POLICY_TITLE_ZH,
        version: data.version || '',
        effectiveDate: data.effectiveDate || ''
      }
      const zhOk = data.content && data.content.trim().length >= MIN_FULL_POLICY_LENGTH
      const enOk = data.contentEn && data.contentEn.trim().length >= MIN_FULL_POLICY_LENGTH
      contentZh.value = zhOk ? data.content : PRIVACY_POLICY_TEXT_ZH
      contentEn.value = enOk ? data.contentEn : PRIVACY_POLICY_TEXT_EN
      zhFromApp.value = !zhOk
      enFromApp.value = !enOk
    }
  } catch (err) {
    console.error('获取隐私政策失败，回退到 APP 内置文案', err)
  }
  if (!loaded) {
    contentZh.value = PRIVACY_POLICY_TEXT_ZH
    contentEn.value = PRIVACY_POLICY_TEXT_EN
    zhFromApp.value = true
    enFromApp.value = true
  }
}

function startEdit() {
  editing.value = true
}

// 取消编辑：丢弃未保存修改，重新从政策库/APP 文案加载
function cancelEdit() {
  editing.value = false
  load()
}

// 只把当前语言重置为 APP 端内置文案，另一语言保留现有内容
function loadAppContent() {
  if (activeLang.value === 'zh') {
    contentZh.value = PRIVACY_POLICY_TEXT_ZH
    zhFromApp.value = true
  } else {
    contentEn.value = PRIVACY_POLICY_TEXT_EN
    enFromApp.value = true
  }
  ElMessage.success(`已载入 APP 内置${activeLang.value === 'zh' ? '中文' : '英文'}隐私政策文案`)
}

async function handleSave() {
  if (!contentZh.value.trim() && !contentEn.value.trim()) {
    ElMessage.warning('中文或英文正文至少填写一份')
    return
  }
  saving.value = true
  try {
    if (policy.value.id) {
      // 已有政策：一次提交中英两份正文
      const res = await updateGdprPolicy(policy.value.id, {
        content: contentZh.value,
        contentEn: contentEn.value
      })
      policy.value.version = res?.version || policy.value.version
      policy.value.effectiveDate = res?.effectiveDate || policy.value.effectiveDate
    } else {
      // 政策库为空：首次保存时创建并生效
      const res = await createGdprPolicy({
        version: '1.0',
        title: PRIVACY_POLICY_TITLE_ZH,
        titleEn: PRIVACY_POLICY_TITLE_EN,
        content: contentZh.value,
        contentEn: contentEn.value
      })
      policy.value = {
        id: res?.id || null,
        title: res?.title || PRIVACY_POLICY_TITLE_ZH,
        version: res?.version || '',
        effectiveDate: res?.effectiveDate || ''
      }
    }
    zhFromApp.value = false
    enFromApp.value = false
    editing.value = false
    ElMessage.success('隐私政策（中英双语）已保存')
  } catch (err) {
    console.error('保存隐私政策失败', err)
    ElMessage.error('保存失败：' + (err?.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push('/gdpr')
}

onMounted(load)
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { margin-bottom: 16px; }
.page-title { font-size: 18px; font-weight: 700; color: var(--text-800); }
.policy-card { border-radius: var(--radius); }
.meta-row {
  display: flex; align-items: center; gap: 20px; flex-wrap: wrap;
  padding-bottom: 14px; margin-bottom: 14px;
  border-bottom: 1px solid var(--border);
}
.meta-item { font-size: 13px; color: var(--text-500); }
.meta-item b { color: var(--text-800); }
.meta-count { margin-left: auto; font-size: 12px; color: var(--text-400); }
.tip { margin-bottom: 8px; }
.lang-tabs :deep(.el-tabs__header) { margin-bottom: 12px; }
.editor :deep(.el-textarea__inner) {
  font-size: 14px; line-height: 1.8; font-family: inherit;
}
/* 阅读态：整页正文，段落间距与 APP 端协议页保持一致 */
.policy-body { padding: 8px 4px 24px; }
.policy-line {
  margin: 0 0 14px;
  font-size: 14px; line-height: 1.8; color: var(--text-700);
  white-space: pre-wrap;
}
</style>
