<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>关税管理</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建税率</el-button>
        <el-button @click="handleCalculate">试算关税</el-button>
      </div>
    </div>
    <el-card shadow="never">
      <el-table :data="tableData" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="productCategory" label="商品类别" min-width="140" show-overflow-tooltip />
        <el-table-column prop="countryCode" label="国家/地区" width="110" />
        <el-table-column prop="rate" label="税率" width="100">
          <template #default="{ row }">{{ row.rate }}%</template>
        </el-table-column>
        <el-table-column label="免税阈值" width="160">
          <template #default="{ row }">
            <span>¥{{ row.minThreshold ?? '-' }}</span>
            <template v-if="row.maxThreshold != null"> ~ ¥{{ row.maxThreshold }}</template>
          </template>
        </el-table-column>
        <el-table-column prop="currency" label="币种" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span :class="['tag', row.status === 'ENABLED' ? 'tag-green' : 'tag-gray']">
              {{ row.status === 'ENABLED' ? '生效' : '停用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑税率' : '新建税率'" width="500px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="商品类别" required>
          <el-input v-model="form.productCategory" placeholder="如：电子产品、服装" />
        </el-form-item>
        <el-form-item label="国家代码" required>
          <el-input v-model="form.countryCode" placeholder="如：JP、KR、US" />
        </el-form-item>
        <el-form-item label="税率(%)" required>
          <el-input-number v-model="form.rate" :min="0" :max="100" :precision="2" />
        </el-form-item>
        <el-form-item label="免税阈值下限(¥)">
          <el-input-number v-model="form.minThreshold" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="免税阈值上限(¥)">
          <el-input-number v-model="form.maxThreshold" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="币种">
          <el-input v-model="form.currency" placeholder="如：USD、CNY" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.statusEnabled" active-text="生效" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
    <!-- 试算弹窗 -->
    <el-dialog v-model="calcDialogVisible" title="关税试算" width="450px">
      <el-form :model="calcForm" label-width="110px">
        <el-form-item label="商品类别" required>
          <el-input v-model="calcForm.category" placeholder="如：电子产品" />
        </el-form-item>
        <el-form-item label="国家代码" required>
          <el-input v-model="calcForm.countryCode" placeholder="如：JP" />
        </el-form-item>
        <el-form-item label="商品价格(¥)" required>
          <el-input-number v-model="calcForm.amount" :min="0" :precision="2" />
        </el-form-item>
      </el-form>
      <div v-if="calcResult" class="calc-result">
        <el-divider />
        <p>应缴关税：<strong style="color:var(--state-warning);font-size:18px">¥{{ calcResult.tariff ?? calcResult }}</strong></p>
        <p v-if="calcResult.tariff != null" style="color:var(--text-500);font-size:13px">含税总额：¥{{ calcResult.total }}</p>
      </div>
      <template #footer>
        <el-button @click="calcDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="confirmCalculate">计算</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTariffConfigs, createTariffConfig, updateTariffConfig, deleteTariffConfig, calculateTariff } from '../api/admin'
import { toArray } from '../utils/safeArray'

const tableData = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)

const form = reactive({
  productCategory: '',
  countryCode: '',
  rate: 0,
  minThreshold: 0,
  maxThreshold: 0,
  currency: 'USD',
  statusEnabled: true,
})

const calcDialogVisible = ref(false)
const calcResult = ref(null)
const calcForm = reactive({
  category: '',
  countryCode: '',
  amount: 0,
})

function resetForm() {
  form.productCategory = ''
  form.countryCode = ''
  form.rate = 0
  form.minThreshold = 0
  form.maxThreshold = 0
  form.currency = 'USD'
  form.statusEnabled = true
}

async function loadData() {
  try {
    const res = await getTariffConfigs()
    tableData.value = toArray(res)
  } catch (e) {
    ElMessage.error('获取关税配置失败')
  }
}

function handleAdd() {
  isEdit.value = false
  editId.value = null
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  editId.value = row.id
  form.productCategory = row.productCategory || ''
  form.countryCode = row.countryCode || ''
  form.rate = row.rate != null ? Number(row.rate) : 0
  form.minThreshold = row.minThreshold != null ? Number(row.minThreshold) : 0
  form.maxThreshold = row.maxThreshold != null ? Number(row.maxThreshold) : 0
  form.currency = row.currency || 'USD'
  form.statusEnabled = row.status !== 'DISABLED'
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.productCategory || !form.countryCode) {
    ElMessage.warning('请填写完整信息')
    return
  }
  // 组装与后端字段一致的数据
  const payload = {
    productCategory: form.productCategory,
    countryCode: form.countryCode,
    rate: form.rate,
    minThreshold: form.minThreshold,
    maxThreshold: form.maxThreshold,
    currency: form.currency,
    status: form.statusEnabled ? 'ENABLED' : 'DISABLED',
  }
  try {
    if (isEdit.value) {
      await updateTariffConfig({ id: editId.value, ...payload })
      ElMessage.success('编辑成功')
    } else {
      await createTariffConfig({ ...payload })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadData()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除该关税配置吗？', '提示')
  } catch (e) {
    return // 用户取消或关闭
  }
  try {
    await deleteTariffConfig(row.id)
    ElMessage.success('已删除')
    await loadData()
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

function handleCalculate() {
  calcForm.category = ''
  calcForm.countryCode = ''
  calcForm.amount = 0
  calcResult.value = null
  calcDialogVisible.value = true
}

async function confirmCalculate() {
  if (!calcForm.category || !calcForm.countryCode || !calcForm.amount) {
    ElMessage.warning('请填写完整信息')
    return
  }
  try {
    // 后端读取 countryCode / category / amount
    const res = await calculateTariff({
      countryCode: calcForm.countryCode,
      category: calcForm.category,
      amount: calcForm.amount,
    })
    calcResult.value = res && typeof res === 'object' ? res : { tariff: res }
  } catch (e) {
    ElMessage.error('计算失败')
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.header-actions { display: flex; gap: 8px; }
.calc-result { padding: 0 10px; text-align: center; }
</style>
