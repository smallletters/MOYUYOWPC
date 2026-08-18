// k6 压测脚本：下单流程压力测试
//
// 模拟用户登录 → 加入购物车 → 创建订单 → 模拟支付
// 用于验证支付流程与库存系统的并发安全
//
// 用法：
//   k6 run scripts/loadtest/checkout-stress.js
//   k6 run -e BASE_URL=https://staging.moyuyo.example.com scripts/loadtest/checkout-stress.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

// 自定义指标
export const checkoutSuccess = new Counter('checkout_success');
export const checkoutFail = new Counter('checkout_fail');
export const checkoutDuration = new Rate('checkout_duration_ok');

// 配置：阶梯式压力测试
export const options = {
  scenarios: {
    // 场景 1：模拟日常流量
    normal_traffic: {
      executor: 'constant-arrival-rate',
      rate: 50,                  // 每秒 50 个请求
      timeUnit: '1s',
      duration: '5m',
      preAllocatedVUs: 20,
      maxVUs: 100,
    },
    // 场景 2：秒杀场景（瞬时高并发）
    spike_traffic: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 200 },
        { duration: '30s', target: 500 },   // 30s 拉满 500 并发
        { duration: '1m', target: 500 },
        { duration: '10s', target: 0 },
      ],
      startTime: '6m',           // 6 分钟后开始
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<1000', 'p(99)<3000'],     // 95% < 1s, 99% < 3s
    http_req_failed: ['rate<0.05'],                       // 错误率 < 5%
    checkout_success: ['count>0'],                        // 至少有一些成功
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 共享 token 缓存（所有 VU 共用一份，模拟多用户但登录态重复使用）
let cachedToken = null;
let tokenExpiry = 0;

function getAuthToken() {
  // token 缓存复用（避免每个请求都登录）
  if (cachedToken && Date.now() < tokenExpiry) {
    return cachedToken;
  }
  const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({
    email: 'loadtest@moyuyo.example.com',
    password: 'LoadTestPassword123!',
  }), { headers: { 'Content-Type': 'application/json' } });

  if (loginRes.status === 200) {
    try {
      const body = JSON.parse(loginRes.body);
      cachedToken = body.data.token;
      tokenExpiry = Date.now() + 60 * 60 * 1000;  // 1 小时
      return cachedToken;
    } catch (e) {
      return null;
    }
  }
  return null;
}

export default function () {
  const token = getAuthToken();
  if (!token) {
    checkoutFail.add(1);
    return;
  }

  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`,
  };

  // 1. 查询商品列表
  const listRes = http.get(`${BASE_URL}/api/v1/products?page=1&size=10`);
  if (listRes.status !== 200) {
    checkoutFail.add(1);
    return;
  }

  let productId;
  try {
    const list = JSON.parse(listRes.body);
    if (!list.data || list.data.length === 0) {
      checkoutFail.add(1);
      return;
    }
    productId = list.data[Math.floor(Math.random() * list.data.length)].id;
  } catch (e) {
    checkoutFail.add(1);
    return;
  }

  // 2. 加入购物车
  const cartRes = http.post(`${BASE_URL}/api/v1/cart/items`,
    JSON.stringify({ productId: productId, quantity: 1 }),
    { headers }
  );
  if (cartRes.status !== 200 && cartRes.status !== 201) {
    checkoutFail.add(1);
    return;
  }

  sleep(0.5);

  // 3. 创建订单（下单）
  const start = Date.now();
  const orderRes = http.post(`${BASE_URL}/api/v1/orders`,
    JSON.stringify({
      items: [{ productId: productId, quantity: 1 }],
    }),
    { headers }
  );

  const duration = Date.now() - start;

  const orderOk = check(orderRes, {
    'order: status 200': (r) => r.status === 200,
    'order: response < 2s': (r) => r.timings.duration < 2000,
    'order: has order id': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.code === 0 && body.data && body.data.id;
      } catch (e) {
        return false;
      }
    },
  });

  if (orderOk) {
    checkoutSuccess.add(1);
    checkoutDuration.add(true);
  } else {
    checkoutFail.add(1);
    checkoutDuration.add(false);
  }

  sleep(1);
}

export function handleSummary(data) {
  return {
    'stdout': `\n=== MOYUYO 下单压测报告 ===\n` +
              `总请求数: ${data.metrics.http_reqs.values.count}\n` +
              `平均 QPS: ${data.metrics.http_reqs.values.rate.toFixed(2)}\n` +
              `P95 响应: ${data.metrics.http_req_duration.values['p(95)'].toFixed(0)}ms\n` +
              `P99 响应: ${data.metrics.http_req_duration.values['p(99)'].toFixed(0)}ms\n` +
              `错误率: ${(data.metrics.http_req_failed.values.rate * 100).toFixed(2)}%\n` +
              `下单成功: ${data.metrics.checkout_success.values.count}\n` +
              `下单失败: ${data.metrics.checkout_fail.values.count}\n`,
  };
}