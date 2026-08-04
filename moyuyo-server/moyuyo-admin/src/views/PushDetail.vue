<template>
  <div class="page-wrapper">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>{{ pageTitle }}</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建推送</el-button>
      </div>
    </div>

    <!-- 筛选卡片 -->
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="推送名称">
          <el-input v-model="filters.title" placeholder="请输入推送名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="请选择" clearable style="width:120px">
            <el-option label="发送中" value="发送中" />
            <el-option label="已完成" value="已完成" />
            <el-option label="失败" value="失败" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 推送记录列表 -->
    <el-card shadow="never">
      <el-table :data="tableData" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="推送任务" width="180" />
        <el-table-column label="推送渠道" width="130">
          <template #default="{ row }">
            <el-tag :type="row.channel === 'all' ? 'primary' : row.channel === 'ios' ? 'info' : 'success'">{{ row.channel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sentCount" label="发送数" width="80" />
        <el-table-column prop="openCount" label="到达数" width="80" />
        <el-table-column prop="clickCount" label="点击数" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === '已完成' ? 'success' : row.status === '失败' ? 'danger' : 'warning'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex;justify-content:flex-end;padding:16px 0 0">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @change="loadData"
        />
      </div>
    </el-card>

    <!-- 最近发送记录（示例数据：无真实接口，用于展示区块结构） -->
    <el-card shadow="never" class="recent-card">
      <template #header>
        <div class="recent-card-header">
          <span class="recent-card-title">最近发送记录</span>
          <el-button link type="primary" size="small">查看全部</el-button>
        </div>
      </template>
      <el-table :data="recentRecords" stripe size="small">
        <el-table-column prop="time" label="时间" width="170" />
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="target" label="目标" width="150" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === '已完成' ? 'success' : row.status === '失败' ? 'danger' : 'warning'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建/编辑推送弹窗：推送内容 + 发送设置 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="920px" top="6vh">
      <el-form :model="editForm" label-width="100px">
        <!-- ===== 推送内容 ===== -->
        <div class="form-section-title">推送内容</div>
        <el-form-item label="推送类型">
          <el-select v-model="editForm.type" style="width: 100%">
            <el-option label="营销推送" value="营销推送" />
            <el-option label="活动通知" value="活动通知" />
            <el-option label="订单提醒" value="订单提醒" />
            <el-option label="系统公告" value="系统公告" />
            <el-option label="个性化推荐" value="个性化推荐" />
          </el-select>
        </el-form-item>
        <el-form-item label="推送标题">
          <el-input v-model="editForm.title" maxlength="30" show-word-limit placeholder="请输入推送标题" />
        </el-form-item>
        <el-form-item label="推送正文">
          <el-input v-model="editForm.content" type="textarea" :rows="4" maxlength="200" show-word-limit placeholder="请输入推送正文内容" />
        </el-form-item>
        <el-form-item label="跳转链接">
          <el-input v-model="editForm.link" placeholder="例如：moyuyo://campaign/summer-sale" />
        </el-form-item>
        <el-form-item label="图片URL">
          <el-input v-model="editForm.imageUrl" placeholder="请输入富媒体图片 URL（可选）" />
        </el-form-item>

        <!-- ===== 发送设置 ===== -->
        <div class="form-section-title">发送设置</div>
        <el-form-item label="发送时间">
          <el-radio-group v-model="editForm.sendTimeType">
            <el-radio-button value="now">立即发送</el-radio-button>
            <el-radio-button value="scheduled">定时发送</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="editForm.sendTimeType === 'scheduled'" label="定时时间">
          <el-date-picker
            v-model="editForm.sendTime"
            type="datetime"
            placeholder="选择定时发送时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="目标人群">
          <el-radio-group v-model="editForm.targetType">
            <el-radio-button value="all">全部用户</el-radio-button>
            <el-radio-button value="users">指定用户</el-radio-button>
            <el-radio-button value="tags">指定标签</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="editForm.targetType !== 'all'" :label="editForm.targetType === 'users' ? '用户ID' : '标签'">
          <el-input v-model="editForm.targetValue" :placeholder="editForm.targetType === 'users' ? '多个用户ID用逗号分隔，如：1001,1002' : '请输入用户标签，如：高活跃'" />
        </el-form-item>
        <el-form-item label="推送平台">
          <el-radio-group v-model="editForm.channel">
            <el-radio-button value="all">全部</el-radio-button>
            <el-radio-button value="ios">iOS</el-radio-button>
            <el-radio-button value="android">Android</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status" style="width: 160px">
            <el-option label="发送中" value="发送中" />
            <el-option label="已完成" value="已完成" />
            <el-option label="失败" value="失败" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPushRecords, createPush, updatePush, deletePush } from '../api/admin'

const pageTitle = '推送详情'
const filters = reactive({ title: '', status: '' })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')

// 示例数据：最近发送记录（无真实接口，仅用于展示区块结构，后续可替换为真实 API）
const recentRecords = ref([
  { time: '2026-07-08 10:00', title: '夏日大促开始啦！精选商品低至5折', target: '全量用户', status: '已完成' },
  { time: '2026-07-07 14:30', title: '新品上市：有机冻干零食系列', target: '全量用户', status: '已完成' },
  { time: '2026-07-07 09:15', title: '您的订单已发货，预计3天内送达', target: '指定用户', status: '已完成' },
  { time: '2026-07-06 16:00', title: '系统维护通知：7月10日凌晨升级', target: '全量用户', status: '失败' },
  { time: '2026-07-05 11:45', title: '专属推荐：根据您的宠物喜好精选', target: '标签：高活跃', status: '发送中' }
])

// 新建/编辑表单：推送内容 + 发送设置
const editForm = reactive({
  id: undefined,
  type: '营销推送',          // 推送类型
  title: '',                 // 推送标题
  content: '',               // 推送正文
  link: '',                  // 跳转链接（Deep Link）
  imageUrl: '',              // 富媒体图片 URL
  channel: 'all',            // 推送平台：all / ios / android（与后端字段对齐）
  status: '发送中',          // 推送状态
  sendTimeType: 'now',       // 发送时间：now 立即 / scheduled 定时
  sendTime: '',              // 定时发送时间
  targetType: 'all',         // 目标人群：all 全部 / users 指定用户 / tags 指定标签
  targetValue: ''            // 目标人群参数（用户ID或标签）
})

// 从API加载推送记录数据
async function loadData() {
  try {
    const res = await getPushRecords()
    const records = (res && res.records) || res || []
    let filtered = records
    if (filters.title) {
      filtered = filtered.filter(item => (item.title || '').includes(filters.title))
    }
    if (filters.status) {
      filtered = filtered.filter(item => item.status === filters.status)
    }
    tableData.value = filtered
    total.value = filtered.length
  } catch (e) {
    console.error('加载推送记录失败:', e)
    ElMessage.error('加载推送记录失败')
  }
}
function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.title = ''; filters.status = ''; handleSearch() }

// 重置编辑表单为默认值（新建推送）
function resetForm() {
  editForm.id = undefined
  editForm.type = '营销推送'
  editForm.title = ''
  editForm.content = ''
  editForm.link = ''
  editForm.imageUrl = ''
  editForm.channel = 'all'
  editForm.status = '发送中'
  editForm.sendTimeType = 'now'
  editForm.sendTime = ''
  editForm.targetType = 'all'
  editForm.targetValue = ''
}
function handleAdd() { dialogTitle.value = '新建推送'; resetForm(); dialogVisible.value = true }
function handleEdit(row) { dialogTitle.value = '编辑推送'; resetForm(); Object.assign(editForm, row); dialogVisible.value = true }

// 删除推送（调用API）
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除？', '提示')
    await deletePush(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') {
      console.error('删除推送失败:', e)
    }
  }
}

// 保存推送（调用API：新建或编辑）
async function handleSave() {
  try {
    // 后端 create/update 只读取 title/content/channel/type/targetType/targetIds，
    // link/imageUrl/status/sendTimeType/sendTime 均不提交（表单仍保留展示）
    const payload = {
      title: editForm.title,
      type: editForm.type,
      content: editForm.content,
      channel: editForm.channel,
      targetType: editForm.targetType,
      // 页面为单选值，包装成数组提交
      targetIds: editForm.targetType === 'all' ? [] : [editForm.targetValue]
    }
    if (editForm.id) {
      // 已有ID，调用更新API
      await updatePush(editForm.id, payload)
      ElMessage.success('更新成功')
    } else {
      // 无ID，调用创建API
      await createPush(payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (e) {
    console.error('保存推送失败:', e)
    ElMessage.error('保存失败')
  }
}
onMounted(() => loadData())
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
.recent-card { margin-top: 16px; }
.recent-card-header { display: flex; align-items: center; justify-content: space-between; }
.recent-card-title { font-size: 14px; font-weight: 600; color: var(--text-800); }
.form-section-title {
  margin: 0 0 14px;
  padding-left: 10px;
  border-left: 3px solid var(--primary);
  font-size: 14px;
  font-weight: 600;
  color: var(--text-800);
}
.form-section-title + .el-form-item { margin-top: 0; }
</style>
