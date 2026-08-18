-- ============================================================
-- 滑动窗口限流 Lua 脚本（Redis 原子操作）
-- ============================================================
-- KEYS[1]   : Redis key（滑动窗口 ZSet）
-- ARGV[1]   : 当前时间戳（毫秒）
-- ARGV[2]   : 窗口起点（now - windowMs）
-- ARGV[3]   : 窗口内允许的最大请求数
-- ARGV[4]   : 窗口长度（毫秒，用于设置 key 过期）
-- ARGV[5]   : 本次请求 member（毫秒时间戳 + 随机数，保证唯一）
--
-- 返回值：
--   0 : 已通过限流（已写入一条记录到 ZSet）
--   1 : 触发限流（未写入新记录）
-- ============================================================

local key       = KEYS[1]
local now       = tonumber(ARGV[1])
local winStart  = tonumber(ARGV[2])
local maxReq    = tonumber(ARGV[3])
local expireMs  = tonumber(ARGV[4])
local member    = ARGV[5]

-- 1. 清理窗口外的旧记录
redis.call('ZREMRANGEBYSCORE', key, 0, winStart)

-- 2. 读取窗口内当前请求数
local curCount = redis.call('ZCARD', key)

if curCount >= maxReq then
    -- 已超限：直接返回 1，不写入新记录
    return 1
end

-- 3. 未超限：写入当前请求并刷新 key 过期时间
redis.call('ZADD', key, now, member)
redis.call('PEXPIRE', key, expireMs)

return 0