<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>{{ pageTitle }}</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新增区域</el-button>
      </div>
    </div>
    <el-card shadow="never">
      <el-table :data="tableData" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="区域名称" width="160" />
        <el-table-column prop="countryCodes" label="国家码（ISO）" min-width="220" />
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'">{{ row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
      <el-form :model="editForm" label-width="120px">
        <el-form-item label="区域名称">
          <el-input v-model="editForm.name" placeholder="如：北美、欧盟" />
        </el-form-item>
        <el-form-item label="国家">
          <!-- 多选下拉：option.value 为 ISO 3166-1 alpha-2 大写码，label 显示「中文 国家名 (码)」便于检索 -->
          <el-select
            v-model="editForm.countryCodeList"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            clearable
            placeholder="请选择国家（可搜索国家名/码）"
            style="width: 100%"
          >
            <el-option
              v-for="item in countryOptions"
              :key="item.code"
              :label="`${item.nameZh} ${item.nameEn} (${item.code})`"
              :value="item.code"
            />
          </el-select>
          <div style="font-size:12px;color:#909399;margin-top:4px">逗号分隔的 ISO 3166-1 alpha-2 大写码；此处维护的 ACTIVE 区域将决定 APP 可发货地址</div>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="editForm.sortOrder" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" placeholder="可选" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
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
import { getShippingZones, createShippingZone, updateShippingZone, deleteShippingZone } from '../api/admin'

// ISO 3166-1 alpha-2 国家清单（内置完整版，约 250 国/地区）
// label 同时展示中/英文名 + 码，便于用户搜索；value 固定为大写两字母码。
const countryOptions = [
  { code: 'AW', nameZh: '阿鲁巴', nameEn: 'Aruba' },
  { code: 'AF', nameZh: '阿富汗', nameEn: 'Afghanistan' },
  { code: 'AO', nameZh: '安哥拉', nameEn: 'Angola' },
  { code: 'AI', nameZh: '安圭拉', nameEn: 'Anguilla' },
  { code: 'AX', nameZh: '奥兰群岛', nameEn: 'Åland Islands' },
  { code: 'AL', nameZh: '阿尔巴尼亚', nameEn: 'Albania' },
  { code: 'AD', nameZh: '安道尔', nameEn: 'Andorra' },
  { code: 'AE', nameZh: '阿联酋', nameEn: 'United Arab Emirates' },
  { code: 'AR', nameZh: '阿根廷', nameEn: 'Argentina' },
  { code: 'AM', nameZh: '亚美尼亚', nameEn: 'Armenia' },
  { code: 'AS', nameZh: '美属萨摩亚', nameEn: 'American Samoa' },
  { code: 'AQ', nameZh: '南极洲', nameEn: 'Antarctica' },
  { code: 'TF', nameZh: '法属南部领地', nameEn: 'French Southern Territories' },
  { code: 'AG', nameZh: '安提瓜和巴布达', nameEn: 'Antigua and Barbuda' },
  { code: 'AU', nameZh: '澳大利亚', nameEn: 'Australia' },
  { code: 'AT', nameZh: '奥地利', nameEn: 'Austria' },
  { code: 'AZ', nameZh: '阿塞拜疆', nameEn: 'Azerbaijan' },
  { code: 'BI', nameZh: '布隆迪', nameEn: 'Burundi' },
  { code: 'BE', nameZh: '比利时', nameEn: 'Belgium' },
  { code: 'BJ', nameZh: '贝宁', nameEn: 'Benin' },
  { code: 'BQ', nameZh: '荷兰加勒比区', nameEn: 'Bonaire, Sint Eustatius and Saba' },
  { code: 'BF', nameZh: '布基纳法索', nameEn: 'Burkina Faso' },
  { code: 'BD', nameZh: '孟加拉国', nameEn: 'Bangladesh' },
  { code: 'BG', nameZh: '保加利亚', nameEn: 'Bulgaria' },
  { code: 'BH', nameZh: '巴林', nameEn: 'Bahrain' },
  { code: 'BS', nameZh: '巴哈马', nameEn: 'Bahamas' },
  { code: 'BA', nameZh: '波黑', nameEn: 'Bosnia and Herzegovina' },
  { code: 'BL', nameZh: '圣巴泰勒米', nameEn: 'Saint Barthélemy' },
  { code: 'BY', nameZh: '白俄罗斯', nameEn: 'Belarus' },
  { code: 'BZ', nameZh: '伯利兹', nameEn: 'Belize' },
  { code: 'BM', nameZh: '百慕大', nameEn: 'Bermuda' },
  { code: 'BO', nameZh: '玻利维亚', nameEn: 'Bolivia' },
  { code: 'BR', nameZh: '巴西', nameEn: 'Brazil' },
  { code: 'BB', nameZh: '巴巴多斯', nameEn: 'Barbados' },
  { code: 'BN', nameZh: '文莱', nameEn: 'Brunei Darussalam' },
  { code: 'BT', nameZh: '不丹', nameEn: 'Bhutan' },
  { code: 'BV', nameZh: '布韦岛', nameEn: 'Bouvet Island' },
  { code: 'BW', nameZh: '博茨瓦纳', nameEn: 'Botswana' },
  { code: 'CF', nameZh: '中非共和国', nameEn: 'Central African Republic' },
  { code: 'CA', nameZh: '加拿大', nameEn: 'Canada' },
  { code: 'CC', nameZh: '科科斯群岛', nameEn: 'Cocos (Keeling) Islands' },
  { code: 'CH', nameZh: '瑞士', nameEn: 'Switzerland' },
  { code: 'CL', nameZh: '智利', nameEn: 'Chile' },
  { code: 'CN', nameZh: '中国', nameEn: 'China' },
  { code: 'CI', nameZh: '科特迪瓦', nameEn: 'Côte d\'Ivoire' },
  { code: 'CM', nameZh: '喀麦隆', nameEn: 'Cameroon' },
  { code: 'CD', nameZh: '刚果（金）', nameEn: 'Congo, Democratic Republic of the' },
  { code: 'CG', nameZh: '刚果（布）', nameEn: 'Congo' },
  { code: 'CK', nameZh: '库克群岛', nameEn: 'Cook Islands' },
  { code: 'CO', nameZh: '哥伦比亚', nameEn: 'Colombia' },
  { code: 'KM', nameZh: '科摩罗', nameEn: 'Comoros' },
  { code: 'CV', nameZh: '佛得角', nameEn: 'Cabo Verde' },
  { code: 'CR', nameZh: '哥斯达黎加', nameEn: 'Costa Rica' },
  { code: 'CU', nameZh: '古巴', nameEn: 'Cuba' },
  { code: 'CW', nameZh: '库拉索', nameEn: 'Curaçao' },
  { code: 'CX', nameZh: '圣诞岛', nameEn: 'Christmas Island' },
  { code: 'KY', nameZh: '开曼群岛', nameEn: 'Cayman Islands' },
  { code: 'CY', nameZh: '塞浦路斯', nameEn: 'Cyprus' },
  { code: 'CZ', nameZh: '捷克', nameEn: 'Czechia' },
  { code: 'DE', nameZh: '德国', nameEn: 'Germany' },
  { code: 'DJ', nameZh: '吉布提', nameEn: 'Djibouti' },
  { code: 'DM', nameZh: '多米尼克', nameEn: 'Dominica' },
  { code: 'DK', nameZh: '丹麦', nameEn: 'Denmark' },
  { code: 'DO', nameZh: '多米尼加', nameEn: 'Dominican Republic' },
  { code: 'DZ', nameZh: '阿尔及利亚', nameEn: 'Algeria' },
  { code: 'EC', nameZh: '厄瓜多尔', nameEn: 'Ecuador' },
  { code: 'EG', nameZh: '埃及', nameEn: 'Egypt' },
  { code: 'ER', nameZh: '厄立特里亚', nameEn: 'Eritrea' },
  { code: 'EH', nameZh: '西撒哈拉', nameEn: 'Western Sahara' },
  { code: 'ES', nameZh: '西班牙', nameEn: 'Spain' },
  { code: 'EE', nameZh: '爱沙尼亚', nameEn: 'Estonia' },
  { code: 'ET', nameZh: '埃塞俄比亚', nameEn: 'Ethiopia' },
  { code: 'FI', nameZh: '芬兰', nameEn: 'Finland' },
  { code: 'FJ', nameZh: '斐济', nameEn: 'Fiji' },
  { code: 'FK', nameZh: '福克兰群岛', nameEn: 'Falkland Islands' },
  { code: 'FR', nameZh: '法国', nameEn: 'France' },
  { code: 'FO', nameZh: '法罗群岛', nameEn: 'Faroe Islands' },
  { code: 'FM', nameZh: '密克罗尼西亚', nameEn: 'Micronesia' },
  { code: 'GA', nameZh: '加蓬', nameEn: 'Gabon' },
  { code: 'GB', nameZh: '英国', nameEn: 'United Kingdom' },
  { code: 'GE', nameZh: '格鲁吉亚', nameEn: 'Georgia' },
  { code: 'GG', nameZh: '根西', nameEn: 'Guernsey' },
  { code: 'GH', nameZh: '加纳', nameEn: 'Ghana' },
  { code: 'GI', nameZh: '直布罗陀', nameEn: 'Gibraltar' },
  { code: 'GN', nameZh: '几内亚', nameEn: 'Guinea' },
  { code: 'GP', nameZh: '瓜德罗普', nameEn: 'Guadeloupe' },
  { code: 'GM', nameZh: '冈比亚', nameEn: 'Gambia' },
  { code: 'GW', nameZh: '几内亚比绍', nameEn: 'Guinea-Bissau' },
  { code: 'GQ', nameZh: '赤道几内亚', nameEn: 'Equatorial Guinea' },
  { code: 'GR', nameZh: '希腊', nameEn: 'Greece' },
  { code: 'GD', nameZh: '格林纳达', nameEn: 'Grenada' },
  { code: 'GL', nameZh: '格陵兰', nameEn: 'Greenland' },
  { code: 'GT', nameZh: '危地马拉', nameEn: 'Guatemala' },
  { code: 'GF', nameZh: '法属圭亚那', nameEn: 'French Guiana' },
  { code: 'GU', nameZh: '关岛', nameEn: 'Guam' },
  { code: 'GY', nameZh: '圭亚那', nameEn: 'Guyana' },
  { code: 'HK', nameZh: '中国香港', nameEn: 'Hong Kong' },
  { code: 'HM', nameZh: '赫德岛和麦克唐纳群岛', nameEn: 'Heard Island and McDonald Islands' },
  { code: 'HN', nameZh: '洪都拉斯', nameEn: 'Honduras' },
  { code: 'HR', nameZh: '克罗地亚', nameEn: 'Croatia' },
  { code: 'HT', nameZh: '海地', nameEn: 'Haiti' },
  { code: 'HU', nameZh: '匈牙利', nameEn: 'Hungary' },
  { code: 'ID', nameZh: '印度尼西亚', nameEn: 'Indonesia' },
  { code: 'IM', nameZh: '马恩岛', nameEn: 'Isle of Man' },
  { code: 'IN', nameZh: '印度', nameEn: 'India' },
  { code: 'IO', nameZh: '英属印度洋领地', nameEn: 'British Indian Ocean Territory' },
  { code: 'IE', nameZh: '爱尔兰', nameEn: 'Ireland' },
  { code: 'IR', nameZh: '伊朗', nameEn: 'Iran' },
  { code: 'IQ', nameZh: '伊拉克', nameEn: 'Iraq' },
  { code: 'IS', nameZh: '冰岛', nameEn: 'Iceland' },
  { code: 'IL', nameZh: '以色列', nameEn: 'Israel' },
  { code: 'IT', nameZh: '意大利', nameEn: 'Italy' },
  { code: 'JM', nameZh: '牙买加', nameEn: 'Jamaica' },
  { code: 'JE', nameZh: '泽西', nameEn: 'Jersey' },
  { code: 'JO', nameZh: '约旦', nameEn: 'Jordan' },
  { code: 'JP', nameZh: '日本', nameEn: 'Japan' },
  { code: 'KZ', nameZh: '哈萨克斯坦', nameEn: 'Kazakhstan' },
  { code: 'KE', nameZh: '肯尼亚', nameEn: 'Kenya' },
  { code: 'KG', nameZh: '吉尔吉斯斯坦', nameEn: 'Kyrgyzstan' },
  { code: 'KH', nameZh: '柬埔寨', nameEn: 'Cambodia' },
  { code: 'KI', nameZh: '基里巴斯', nameEn: 'Kiribati' },
  { code: 'KN', nameZh: '圣基茨和尼维斯', nameEn: 'Saint Kitts and Nevis' },
  { code: 'KR', nameZh: '韩国', nameEn: 'Korea, Republic of' },
  { code: 'KW', nameZh: '科威特', nameEn: 'Kuwait' },
  { code: 'LA', nameZh: '老挝', nameEn: 'Lao People\'s Democratic Republic' },
  { code: 'LB', nameZh: '黎巴嫩', nameEn: 'Lebanon' },
  { code: 'LR', nameZh: '利比里亚', nameEn: 'Liberia' },
  { code: 'LY', nameZh: '利比亚', nameEn: 'Libya' },
  { code: 'LC', nameZh: '圣卢西亚', nameEn: 'Saint Lucia' },
  { code: 'LI', nameZh: '列支敦士登', nameEn: 'Liechtenstein' },
  { code: 'LK', nameZh: '斯里兰卡', nameEn: 'Sri Lanka' },
  { code: 'LS', nameZh: '莱索托', nameEn: 'Lesotho' },
  { code: 'LT', nameZh: '立陶宛', nameEn: 'Lithuania' },
  { code: 'LU', nameZh: '卢森堡', nameEn: 'Luxembourg' },
  { code: 'LV', nameZh: '拉脱维亚', nameEn: 'Latvia' },
  { code: 'MO', nameZh: '中国澳门', nameEn: 'Macao' },
  { code: 'MF', nameZh: '法属圣马丁', nameEn: 'Saint Martin' },
  { code: 'MA', nameZh: '摩洛哥', nameEn: 'Morocco' },
  { code: 'MC', nameZh: '摩纳哥', nameEn: 'Monaco' },
  { code: 'MD', nameZh: '摩尔多瓦', nameEn: 'Moldova' },
  { code: 'MG', nameZh: '马达加斯加', nameEn: 'Madagascar' },
  { code: 'MV', nameZh: '马尔代夫', nameEn: 'Maldives' },
  { code: 'MX', nameZh: '墨西哥', nameEn: 'Mexico' },
  { code: 'MH', nameZh: '马绍尔群岛', nameEn: 'Marshall Islands' },
  { code: 'MK', nameZh: '北马其顿', nameEn: 'North Macedonia' },
  { code: 'ML', nameZh: '马里', nameEn: 'Mali' },
  { code: 'MT', nameZh: '马耳他', nameEn: 'Malta' },
  { code: 'MM', nameZh: '缅甸', nameEn: 'Myanmar' },
  { code: 'ME', nameZh: '黑山', nameEn: 'Montenegro' },
  { code: 'MN', nameZh: '蒙古', nameEn: 'Mongolia' },
  { code: 'MP', nameZh: '北马里亚纳群岛', nameEn: 'Northern Mariana Islands' },
  { code: 'MZ', nameZh: '莫桑比克', nameEn: 'Mozambique' },
  { code: 'MR', nameZh: '毛里塔尼亚', nameEn: 'Mauritania' },
  { code: 'MS', nameZh: '蒙特塞拉特', nameEn: 'Montserrat' },
  { code: 'MQ', nameZh: '马提尼克', nameEn: 'Martinique' },
  { code: 'MU', nameZh: '毛里求斯', nameEn: 'Mauritius' },
  { code: 'MW', nameZh: '马拉维', nameEn: 'Malawi' },
  { code: 'MY', nameZh: '马来西亚', nameEn: 'Malaysia' },
  { code: 'YT', nameZh: '马约特', nameEn: 'Mayotte' },
  { code: 'NA', nameZh: '纳米比亚', nameEn: 'Namibia' },
  { code: 'NC', nameZh: '新喀里多尼亚', nameEn: 'New Caledonia' },
  { code: 'NE', nameZh: '尼日尔', nameEn: 'Niger' },
  { code: 'NF', nameZh: '诺福克岛', nameEn: 'Norfolk Island' },
  { code: 'NG', nameZh: '尼日利亚', nameEn: 'Nigeria' },
  { code: 'NI', nameZh: '尼加拉瓜', nameEn: 'Nicaragua' },
  { code: 'NU', nameZh: '纽埃', nameEn: 'Niue' },
  { code: 'NL', nameZh: '荷兰', nameEn: 'Netherlands' },
  { code: 'NO', nameZh: '挪威', nameEn: 'Norway' },
  { code: 'NP', nameZh: '尼泊尔', nameEn: 'Nepal' },
  { code: 'NR', nameZh: '瑙鲁', nameEn: 'Nauru' },
  { code: 'NZ', nameZh: '新西兰', nameEn: 'New Zealand' },
  { code: 'OM', nameZh: '阿曼', nameEn: 'Oman' },
  { code: 'PK', nameZh: '巴基斯坦', nameEn: 'Pakistan' },
  { code: 'PA', nameZh: '巴拿马', nameEn: 'Panama' },
  { code: 'PN', nameZh: '皮特凯恩群岛', nameEn: 'Pitcairn' },
  { code: 'PE', nameZh: '秘鲁', nameEn: 'Peru' },
  { code: 'PH', nameZh: '菲律宾', nameEn: 'Philippines' },
  { code: 'PW', nameZh: '帕劳', nameEn: 'Palau' },
  { code: 'PG', nameZh: '巴布亚新几内亚', nameEn: 'Papua New Guinea' },
  { code: 'PL', nameZh: '波兰', nameEn: 'Poland' },
  { code: 'PR', nameZh: '波多黎各', nameEn: 'Puerto Rico' },
  { code: 'KP', nameZh: '朝鲜', nameEn: 'Korea, Democratic People\'s Republic of' },
  { code: 'PT', nameZh: '葡萄牙', nameEn: 'Portugal' },
  { code: 'PY', nameZh: '巴拉圭', nameEn: 'Paraguay' },
  { code: 'PS', nameZh: '巴勒斯坦', nameEn: 'Palestine, State of' },
  { code: 'PF', nameZh: '法属波利尼西亚', nameEn: 'French Polynesia' },
  { code: 'QA', nameZh: '卡塔尔', nameEn: 'Qatar' },
  { code: 'RE', nameZh: '留尼汪', nameEn: 'Réunion' },
  { code: 'RO', nameZh: '罗马尼亚', nameEn: 'Romania' },
  { code: 'RU', nameZh: '俄罗斯', nameEn: 'Russian Federation' },
  { code: 'RW', nameZh: '卢旺达', nameEn: 'Rwanda' },
  { code: 'SA', nameZh: '沙特阿拉伯', nameEn: 'Saudi Arabia' },
  { code: 'SD', nameZh: '苏丹', nameEn: 'Sudan' },
  { code: 'SS', nameZh: '南苏丹', nameEn: 'South Sudan' },
  { code: 'SN', nameZh: '塞内加尔', nameEn: 'Senegal' },
  { code: 'SG', nameZh: '新加坡', nameEn: 'Singapore' },
  { code: 'GS', nameZh: '南乔治亚和南桑威奇群岛', nameEn: 'South Georgia and the South Sandwich Islands' },
  { code: 'SH', nameZh: '圣赫勒拿', nameEn: 'Saint Helena, Ascension and Tristan da Cunha' },
  { code: 'SJ', nameZh: '斯瓦尔巴和扬马延', nameEn: 'Svalbard and Jan Mayen' },
  { code: 'SB', nameZh: '所罗门群岛', nameEn: 'Solomon Islands' },
  { code: 'SL', nameZh: '塞拉利昂', nameEn: 'Sierra Leone' },
  { code: 'SV', nameZh: '萨尔瓦多', nameEn: 'El Salvador' },
  { code: 'SM', nameZh: '圣马力诺', nameEn: 'San Marino' },
  { code: 'SO', nameZh: '索马里', nameEn: 'Somalia' },
  { code: 'PM', nameZh: '圣皮埃尔和密克隆', nameEn: 'Saint Pierre and Miquelon' },
  { code: 'RS', nameZh: '塞尔维亚', nameEn: 'Serbia' },
  { code: 'ST', nameZh: '圣多美和普林西比', nameEn: 'Sao Tome and Principe' },
  { code: 'SR', nameZh: '苏里南', nameEn: 'Suriname' },
  { code: 'SK', nameZh: '斯洛伐克', nameEn: 'Slovakia' },
  { code: 'SI', nameZh: '斯洛文尼亚', nameEn: 'Slovenia' },
  { code: 'SE', nameZh: '瑞典', nameEn: 'Sweden' },
  { code: 'SZ', nameZh: '斯威士兰', nameEn: 'Eswatini' },
  { code: 'SX', nameZh: '荷属圣马丁', nameEn: 'Sint Maarten' },
  { code: 'SC', nameZh: '塞舌尔', nameEn: 'Seychelles' },
  { code: 'SY', nameZh: '叙利亚', nameEn: 'Syrian Arab Republic' },
  { code: 'TC', nameZh: '特克斯和凯科斯群岛', nameEn: 'Turks and Caicos Islands' },
  { code: 'TD', nameZh: '乍得', nameEn: 'Chad' },
  { code: 'TG', nameZh: '多哥', nameEn: 'Togo' },
  { code: 'TH', nameZh: '泰国', nameEn: 'Thailand' },
  { code: 'TJ', nameZh: '塔吉克斯坦', nameEn: 'Tajikistan' },
  { code: 'TK', nameZh: '托克劳', nameEn: 'Tokelau' },
  { code: 'TM', nameZh: '土库曼斯坦', nameEn: 'Turkmenistan' },
  { code: 'TL', nameZh: '东帝汶', nameEn: 'Timor-Leste' },
  { code: 'TO', nameZh: '汤加', nameEn: 'Tonga' },
  { code: 'TT', nameZh: '特立尼达和多巴哥', nameEn: 'Trinidad and Tobago' },
  { code: 'TN', nameZh: '突尼斯', nameEn: 'Tunisia' },
  { code: 'TR', nameZh: '土耳其', nameEn: 'Türkiye' },
  { code: 'TV', nameZh: '图瓦卢', nameEn: 'Tuvalu' },
  { code: 'TW', nameZh: '中国台湾', nameEn: 'Taiwan' },
  { code: 'TZ', nameZh: '坦桑尼亚', nameEn: 'Tanzania' },
  { code: 'UG', nameZh: '乌干达', nameEn: 'Uganda' },
  { code: 'UA', nameZh: '乌克兰', nameEn: 'Ukraine' },
  { code: 'UM', nameZh: '美国本土外小岛屿', nameEn: 'United States Minor Outlying Islands' },
  { code: 'UY', nameZh: '乌拉圭', nameEn: 'Uruguay' },
  { code: 'US', nameZh: '美国', nameEn: 'United States' },
  { code: 'UZ', nameZh: '乌兹别克斯坦', nameEn: 'Uzbekistan' },
  { code: 'VA', nameZh: '梵蒂冈', nameEn: 'Holy See (Vatican City State)' },
  { code: 'VC', nameZh: '圣文森特和格林纳丁斯', nameEn: 'Saint Vincent and the Grenadines' },
  { code: 'VE', nameZh: '委内瑞拉', nameEn: 'Venezuela' },
  { code: 'VG', nameZh: '英属维尔京群岛', nameEn: 'Virgin Islands, British' },
  { code: 'VI', nameZh: '美属维尔京群岛', nameEn: 'Virgin Islands, U.S.' },
  { code: 'VN', nameZh: '越南', nameEn: 'Viet Nam' },
  { code: 'VU', nameZh: '瓦努阿图', nameEn: 'Vanuatu' },
  { code: 'WF', nameZh: '瓦利斯和富图纳', nameEn: 'Wallis and Futuna' },
  { code: 'WS', nameZh: '萨摩亚', nameEn: 'Samoa' },
  { code: 'YE', nameZh: '也门', nameEn: 'Yemen' },
  { code: 'ZA', nameZh: '南非', nameEn: 'South Africa' },
  { code: 'ZM', nameZh: '赞比亚', nameEn: 'Zambia' },
  { code: 'ZW', nameZh: '津巴布韦', nameEn: 'Zimbabwe' },
  { code: 'XK', nameZh: '科索沃', nameEn: 'Kosovo' }
]

const pageTitle = '发货区域'
const tableData = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
// 表单字段：countryCodeList 为下拉多选用的数组（与后端字符串字段互转）
const editForm = reactive({
  name: '',
  countryCodeList: [],
  countryCodes: '',
  sortOrder: 0,
  remark: '',
  status: 'ACTIVE'
})

async function loadData() {
  try {
    tableData.value = (await getShippingZones()) || []
  } catch (err) {
    console.error('获取发货区域失败', err)
  }
}
// 把后端存的 "US,CA,GB" 字符串拆回 code 数组，去空格/转大写/过滤未知码
function parseCodesToList(raw) {
  if (!raw) return []
  return raw
    .split(',')
    .map(s => s.trim().toUpperCase())
    .filter(Boolean)
}
function handleAdd() {
  dialogTitle.value = '新增区域'
  editForm.name = ''
  editForm.countryCodeList = []
  editForm.countryCodes = ''
  editForm.sortOrder = 0
  editForm.remark = ''
  editForm.status = 'ACTIVE'
  dialogVisible.value = true
}
function handleEdit(row) {
  dialogTitle.value = '编辑区域'
  // 把后端字段名先搬过来（含 countryCodes 字符串）
  Object.assign(editForm, row)
  // 再用拆分后的数组覆盖下拉绑定，保证下拉正确高亮已选项
  editForm.countryCodeList = parseCodesToList(row.countryCodes)
  dialogVisible.value = true
}
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('删除后 APP 端将无法发往该区域，确定删除？', '提示', { type: 'warning' })
    await deleteShippingZone(row.id)
    ElMessage.success('删除成功')
    await loadData()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + (e.message || '未知错误'))
    }
  }
}
async function handleSave() {
  // 校验：必选至少一个国家
  if (!editForm.countryCodeList || editForm.countryCodeList.length === 0) {
    ElMessage.error('请至少选择一个国家')
    return
  }
  try {
    // 把选中数组按用户当前选择顺序拼接为后端期望的 "US,CA,GB" 字符串
    const codesStr = editForm.countryCodeList.join(',')
    const payload = {
      name: editForm.name,
      countryCodes: codesStr,
      sortOrder: editForm.sortOrder,
      remark: editForm.remark,
      status: editForm.status
    }
    if (editForm.id) {
      await updateShippingZone(editForm.id, payload)
    } else {
      await createShippingZone(payload)
    }
    // 同步表单内字符串字段，保持下一次打开的值一致
    editForm.countryCodes = codesStr
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadData()
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  }
}
onMounted(() => loadData())
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.header-actions { display: flex; gap: 8px; }
</style>