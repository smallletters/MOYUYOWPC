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
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="category" label="商品类别" min-width="140" show-overflow-tooltip />
        <el-table-column prop="country" label="国家/地区" width="130" />
        <el-table-column prop="rate" label="税率" width="100">
          <template #default="{ row }">{{ row.rate }}%</template>
        </el-table-column>
        <el-table-column prop="threshold" label="免税阈值" width="120">
          <template #default="{ row }">¥{{ row.threshold }}</template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
        <el-table-column prop="updatedAt" label="更新时间" width="170" />
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
          <el-input v-model="form.category" placeholder="如：电子产品、服装" />
        </el-form-item>
        <el-form-item label="国家/地区" required>
          <el-input v-model="form.country" placeholder="如：日本、韩国" />
        </el-form-item>
        <el-form-item label="税率(%)" required>
          <el-input-number v-model="form.rate" :min="0" :max="100" :precision="2" />
        </el-form-item>
        <el-form-item label="免税阈值(¥)">
          <el-input-number v-model="form.threshold" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="配置说明" />
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
        <el-form-item label="国家/地区" required>
          <el-input v-model="calcForm.country" placeholder="如：日本" />
        </el-form-item>
        <el-form-item label="商品价格(¥)" required>
          <el-input-number v-model="calcForm.amount" :min="0" :precision="2" />
        </el-form-item>
      </el-form>
      <div v-if="calcResult !== null" class="calc-result">
        <el-divider />
        <p>应缴关税：<strong style="color:#e6a23c;font-size:18px">¥{{ calcResult }}</strong></p>
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

const tableData = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)

const form = reactive({
  category: '',
  country: '',
  rate: 0,
  threshold: 0,
  description: '',
})

const calcDialogVisible = ref(false)
const calcResult = ref(null)
const calcForm = reactive({
  category: '',
  country: '',
  amount: 0,
})

function resetForm() {
  form.category = ''
  form.country = ''
  form.rate = 0
  form.threshold = 0
  form.description = ''
}

async function loadData() {
  try {
    const res = await getTariffConfigs()
    tableData.value = res.records || res || []
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
  form.category = row.category
  form.country = row.country
  form.rate = row.rate
  form.threshold = row.threshold
  form.description = row.description
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.category || !form.country) {
    ElMessage.warning('请填写完整信息')
    return
  }
  try {
    if (isEdit.value) {
      await updateTariffConfig({ id: editId.value, ...form })
      ElMessage.success('编辑成功')
    } else {
      await createTariffConfig({ ...form })
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
    await deleteTariffConfig(row.id)
    ElMessage.success('已删除')
    await loadData()
  } catch (e) {
    // 用户取消不处理
  }
}

function handleCalculate() {
  calcForm.category = ''
  calcForm.country = ''
  calcForm.amount = 0
  calcResult.value = null
  calcDialogVisible.value = true
}

async function confirmCalculate() {
  if (!calcForm.category || !calcForm.country || !calcForm.amount) {
    ElMessage.warning('请填写完整信息')
    return
  }
  try {
    const res = await calculateTariff({ ...calcForm })
    calcResult.value = res.tariff ?? res
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
