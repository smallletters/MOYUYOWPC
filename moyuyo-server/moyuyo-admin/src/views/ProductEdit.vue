<template>
  <div class="product-edit">
    <!-- 面包屑 -->
    <div class="breadcrumb">
      <router-link to="/products">商品管理</router-link>
      <span class="separator">/</span>
      <span class="current">编辑商品</span>
    </div>

    <h2 class="page-title">编辑商品</h2>

    <div class="form-card card">
      <div class="card-body">
        <div class="form-group">
          <label>商品名称 <span class="required">*</span></label>
          <input v-model="form.name" placeholder="请输入商品名称" />
        </div>

        <div class="form-row">
          <div class="form-group">
            <label>商品分类 <span class="required">*</span></label>
            <select v-model="form.category">
              <option value="">请选择分类</option>
              <option value="health">保健品</option>
              <option value="food">食品</option>
              <option value="beauty">美妆</option>
              <option value="daily">日用品</option>
            </select>
          </div>
          <div class="form-group">
            <label>SKU <span class="required">*</span></label>
            <input v-model="form.sku" placeholder="请输入SKU编号" />
          </div>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label>售价 <span class="required">*</span></label>
            <input v-model="form.price" type="number" step="0.01" placeholder="0.00" />
          </div>
          <div class="form-group">
            <label>库存数量 <span class="required">*</span></label>
            <input v-model="form.stock" type="number" placeholder="0" />
          </div>
        </div>

        <div class="form-group">
          <label>商品描述</label>
          <textarea v-model="form.description" rows="5" placeholder="请输入商品描述"></textarea>
        </div>

        <div class="form-group">
          <label>商品主图</label>
          <div class="upload-area">
            <span class="upload-icon">+</span>
            <span class="upload-text">点击上传图片</span>
            <span class="upload-hint">支持 JPG、PNG 格式，不超过 2MB</span>
          </div>
        </div>

        <div class="form-group">
          <label>商品状态</label>
          <div class="status-toggle">
            <label class="toggle-switch">
              <input type="checkbox" v-model="form.status" />
              <span class="toggle-slider"></span>
            </label>
            <span class="status-text">{{ form.status ? '上架' : '下架' }}</span>
          </div>
        </div>

        <div class="form-actions">
          <button class="btn btn-primary btn-lg" @click="handleSave">保存</button>
          <button class="btn btn-outline btn-lg" @click="handleCancel">取消</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import api from '../api/index'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()

const form = reactive({
  name: '',
  category: '',
  sku: '',
  price: 0,
  stock: 0,
  description: '',
  status: true
})

// 获取商品详情
async function fetchProduct() {
  const id = route.query.id
  if (!id) return
  try {
    const res = await api.get(`/products/${id}`)
    if (res) {
      Object.assign(form, res)
    }
  } catch (err) {
    console.error('获取商品详情失败:', err)
    ElMessage.error('获取商品详情失败')
  }
}

async function handleSave() {
  try {
    const id = route.query.id
    if (id) {
      await api.put(`/products/${id}`, { ...form })
      ElMessage.success('商品已更新')
    } else {
      await api.post('/products/create', { ...form })
      ElMessage.success('商品已创建')
    }
    router.push('/products')
  } catch (err) {
    console.error('保存商品失败:', err)
    ElMessage.error('保存商品失败')
  }
}

function handleCancel() {
  router.push('/products')
}

onMounted(() => {
  fetchProduct()
})
</script>

<style scoped lang="css">
.breadcrumb {
  margin-bottom: 16px;
}

.page-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-800);
  margin: 0 0 20px;
}

.form-card {
  max-width: 720px;
}

.form-card .form-group {
  margin-bottom: 20px;
}

.form-card .form-row {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}

.form-card .form-row .form-group {
  flex: 1;
  margin-bottom: 0;
}

.form-card label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-700);
  margin-bottom: 6px;
}

.required {
  color: var(--state-error);
}

.form-card input,
.form-card select,
.form-card textarea {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--input);
  border-radius: var(--radius-sm);
  background: var(--background);
  color: var(--foreground);
  font-size: 14px;
  outline: none;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.form-card input:focus,
.form-card select:focus,
.form-card textarea:focus {
  border-color: var(--ring);
  box-shadow: 0 0 0 1px var(--ring);
}

.form-card textarea {
  resize: vertical;
}

/* 上传区域 */
.upload-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px 20px;
  border: 2px dashed var(--border);
  border-radius: var(--radius);
  background: var(--background-50);
  cursor: pointer;
  transition: all 0.18s ease;
}

.upload-area:hover {
  border-color: var(--primary);
  background: var(--brand-50);
}

.upload-icon {
  font-size: 32px;
  color: var(--text-300);
  font-weight: 300;
}

.upload-text {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-500);
}

.upload-hint {
  font-size: 11px;
  color: var(--text-400);
}

/* 状态开关 */
.status-toggle {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-text {
  font-size: 13px;
  color: var(--text-600);
}

/* 开关 */
.toggle-switch {
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;
  cursor: pointer;
}

.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
  padding: 0;
  border: none;
}

.toggle-slider {
  position: absolute;
  inset: 0;
  background: var(--background-200);
  border-radius: 12px;
  transition: all 0.2s ease;
}

.toggle-slider::before {
  content: '';
  position: absolute;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  top: 2px;
  left: 2px;
  background: #fff;
  transition: all 0.2s ease;
  box-shadow: 0 1px 3px rgba(0,0,0,0.15);
}

.toggle-switch input:checked + .toggle-slider {
  background: var(--state-success);
}

.toggle-switch input:checked + .toggle-slider::before {
  transform: translateX(20px);
}

/* 按钮 */
.form-actions {
  display: flex;
  gap: 12px;
  padding-top: 8px;
}
</style>
