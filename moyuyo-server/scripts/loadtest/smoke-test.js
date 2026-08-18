// k6 压测脚本：MOYUYO API 烟雾测试
//
// 安装：choco install k6 （Windows）
// 或：https://k6.io/docs/getting-started/installation/
//
// 用法：
//   k6 run scripts/loadtest/smoke-test.js
//   k6 run --out json=result.json scripts/loadtest/smoke-test.js
//   k6 run -e BASE_URL=https://api.moyuyo.example.com scripts/loadtest/smoke-test.js

import http from 'k6/http';
import { check, sleep } from 'k6';

// 配置：烟雾测试（低并发 + 短时间）
export const options = {
  stages: [
    { duration: '30s', target: 20 },   // 30s 升至 20 VUs
    { duration: '1m', target: 50 },    // 1m 升至 50 VUs
    { duration: '2m', target: 50 },    // 2m 保持 50 VUs
    { duration: '30s', target: 0 },    // 30s 降至 0
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],   // 95% 请求 < 500ms
    http_req_failed: ['rate<0.01'],                     // 错误率 < 1%
    http_reqs: ['rate>100'],                            // QPS > 100
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  // 1. 健康检查
  let res = http.get(`${BASE_URL}/actuator/health`);
  check(res, {
    'health: status 200': (r) => r.status === 200,
    'health: response < 100ms': (r) => r.timings.duration < 100,
  });

  // 2. 商品列表
  res = http.get(`${BASE_URL}/api/v1/products?page=1&size=20`);
  check(res, {
    'products: status 200': (r) => r.status === 200,
    'products: has data': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.code === 0 && Array.isArray(body.data);
      } catch (e) {
        return false;
      }
    },
    'products: response < 500ms': (r) => r.timings.duration < 500,
  });

  sleep(1);

  // 3. 商品详情（随机 ID）
  const productId = Math.floor(Math.random() * 1000) + 1;
  res = http.get(`${BASE_URL}/api/v1/products/${productId}`);
  check(res, {
    'product detail: status 200 or 404': (r) => r.status === 200 || r.status === 404,
  });

  sleep(1);

  // 4. 分类树
  res = http.get(`${BASE_URL}/api/v1/categories/tree`);
  check(res, {
    'categories: status 200': (r) => r.status === 200,
    'categories: response < 300ms': (r) => r.timings.duration < 300,
  });
}

export function handleSummary(data) {
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    'summary.html': htmlReport(data),
  };
}

// 简化版 HTML 报告（不依赖 k6 html reporter）
function htmlReport(data) {
  return `
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8"/>
<title>MOYUYO API 压测报告</title>
<style>
body { font-family: sans-serif; margin: 40px; }
table { border-collapse: collapse; width: 100%; }
th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
th { background: #f5f5f5; }
.passed { color: green; }
.failed { color: red; }
</style>
</head>
<body>
<h1>MOYUYO API 压测报告</h1>
<p>时间：${new Date().toISOString()}</p>
<h2>总览</h2>
<table>
<tr><th>指标</th><th>值</th></tr>
<tr><td>总请求数</td><td>${data.metrics.http_reqs.values.count}</td></tr>
<tr><td>平均 QPS</td><td>${data.metrics.http_reqs.values.rate.toFixed(2)}</td></tr>
<tr><td>P95 响应时间</td><td>${data.metrics.http_req_duration.values['p(95)'].toFixed(2)}ms</td></tr>
<tr><td>P99 响应时间</td><td>${data.metrics.http_req_duration.values['p(99)'].toFixed(2)}ms</td></tr>
<tr><td>错误率</td><td>${(data.metrics.http_req_failed.values.rate * 100).toFixed(2)}%</td></tr>
</table>
</body>
</html>`;
}

function textSummary(data, opts) {
  return JSON.stringify(data.metrics, null, 2);
}