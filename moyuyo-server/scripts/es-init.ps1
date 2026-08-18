# ============================================================
# es-init.ps1
# Elasticsearch 索引初始化脚本
# 用法：
#   .\es-init.ps1 -EsUrl "http://localhost:9200" -EsUser "elastic" -EsPassword "<pwd>"
#
# 完成：
#   1. 创建 ILM 策略（moyuyo-product-policy）
#   2. 创建索引模板（moyuyo-product-template）
#   3. 创建索引（moyuyo_product_v1，含中文 IK 分词 mapping）
#   4. 创建别名将 moyuyo_product 指向 moyuyo_product_v1
#
# 设计要点：
#   - 使用 IK 分词器（需 ES 端预先安装 analysis-ik 插件）
#     镜像：docker.elastic.co/elasticsearch/elasticsearch:8.15.0
#           需执行：bin/elasticsearch-plugin install https://github.com/medcl/elasticsearch-analysis-ik/releases/download/v8.15.0/elasticsearch-analysis-ik-8.15.0.zip
#   - 使用版本化索引（_v1）+ 别名，便于未来零停机重建（reindex + 切换别名）
#   - ILM 30 天热 → 60 天冷 → 90 天删除（适用于商品索引）
# ============================================================

param(
  [string]$EsUrl = "http://localhost:9200",
  [string]$EsUser = "elastic",
  [string]$EsPassword = ""
)

$ErrorActionPreference = "Stop"
$auth = if ($EsPassword) { "$EsUser`:$EsPassword" } else { $EsUser }
$cred = [System.Convert]::ToBase64String([System.Text.Encoding]::ASCII.GetBytes($auth))
$headers = @{
  "Authorization" = "Basic $cred"
  "Content-Type"  = "application/json"
}

function Invoke-EsPut($url, $body) {
  $fullUrl = "$EsUrl$url"
  Write-Host "PUT $fullUrl" -ForegroundColor Cyan
  try {
    $resp = Invoke-RestMethod -Method PUT -Uri $fullUrl -Headers $headers -Body $body -ErrorAction Stop
    Write-Host "  ✓ OK" -ForegroundColor Green
    return $resp
  } catch {
    $err = $_.Exception.Response
    if ($err) {
      $reader = [System.IO.StreamReader]::new($err.GetResponseStream())
      Write-Host "  ✗ $($reader.ReadToEnd())" -ForegroundColor Red
    } else {
      Write-Host "  ✗ $($_.Exception.Message)" -ForegroundColor Red
    }
    throw
  }
}

# ---------- 1. ILM 策略 ----------
$ilmPolicy = @"
{
  "policy": {
    "phases": {
      "hot": {
        "actions": {
          "rollover": {
            "max_age": "30d",
            "max_primary_shard_size": "10gb"
          },
          "set_priority": { "priority": 100 }
        }
      },
      "warm": {
        "min_age": "30d",
        "actions": {
          "forcemerge": { "max_num_segments": 1 },
          "set_priority": { "priority": 50 }
        }
      },
      "cold": {
        "min_age": "90d",
        "actions": {
          "freeze": {},
          "set_priority": { "priority": 0 }
        }
      },
      "delete": {
        "min_age": "365d",
        "actions": { "delete": {} }
      }
    }
  }
}
"@
Invoke-EsPut "/_ilm/policy/moyuyo-product-policy" $ilmPolicy | Out-Null

# ---------- 2. 索引模板（moyuyo_product_* 都套用）----------
$indexTemplate = @"
{
  "index_patterns": ["moyuyo_product_*"],
  "priority": 200,
  "template": {
    "settings": {
      "number_of_shards": 1,
      "number_of_replicas": 1,
      "index.lifecycle.name": "moyuyo-product-policy",
      "index.lifecycle.rollover_alias": "moyuyo_product",
      "analysis": {
        "analyzer": {
          "ik_smart_pinyin": {
            "type": "custom",
            "tokenizer": "ik_smart",
            "filter": ["lowercase", "pinyin_filter"]
          }
        },
        "filter": {
          "pinyin_filter": {
            "type": "pinyin",
            "keep_full_pinyin": false,
            "keep_joined_full_pinyin": true,
            "keep_original": true,
            "limit_first_letter_length": 16,
            "remove_duplicated_term": true
          }
        }
      }
    },
    "mappings": {
      "properties": {
        "name":         { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart",
                           "fields": { "keyword": { "type": "keyword", "ignore_above": 256 } } },
        "detail":       { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
        "spuCode":      { "type": "keyword" },
        "categoryId":   { "type": "long" },
        "categoryName": { "type": "keyword" },
        "brandId":      { "type": "long" },
        "brandName":    { "type": "keyword" },
        "price":        { "type": "scaled_float", "scaling_factor": 100 },
        "originalPrice":{ "type": "scaled_float", "scaling_factor": 100 },
        "stock":        { "type": "integer" },
        "stockStatus":  { "type": "keyword" },
        "sales":        { "type": "integer" },
        "mainImage":    { "type": "keyword", "index": false, "doc_values": false },
        "onSale":       { "type": "boolean" },
        "createTime":   { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||strict_date_optional_time||epoch_millis" }
      }
    }
  }
}
"@
Invoke-EsPut "/_index_template/moyuyo-product-template" $indexTemplate | Out-Null

# ---------- 3. 创建索引 + 别名 ----------
$indexBody = @"
{
  "aliases": {
    "moyuyo_product": {
      "is_write_index": true
    }
  }
}
"@
Invoke-EsPut "/moyuyo_product_v1" $indexBody | Out-Null

Write-Host "`n✅ Elasticsearch 索引初始化完成" -ForegroundColor Green
Write-Host "   - 索引：moyuyo_product_v1（写入别名 moyuyo_product）"
Write-Host "   - ILM 策略：moyuyo-product-policy（30d 热 / 30d 暖 / 365d 删除）"
Write-Host "   - 中文分词：ik_max_word（索引）/ ik_smart（搜索）"
Write-Host "   - 拼音搜索：pinyin 过滤（keep_joined_full_pinyin）"
Write-Host "`n注意：使用前必须先在 ES 节点安装 analysis-ik 与 analysis-pinyin 插件" -ForegroundColor Yellow