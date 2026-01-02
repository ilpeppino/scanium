export type RateLimitResult =
  | { allowed: true }
  | { allowed: false; retryAfterSeconds: number };

export type RedisClient = {
  zremrangebyscore(key: string, min: number, max: number): Promise<number>;
  hmget(key: string, ...fields: string[]): Promise<(string | null)[]>;
  zcard(key: string): Promise<number>;
  hincrby(key: string, field: string, increment: number): Promise<number>;
  hset(key: string, ...args: (string | number)[]): Promise<number>;
  pexpire(key: string, milliseconds: number): Promise<number>;
  zadd(key: string, score: number, member: string): Promise<number>;
};

export type SlidingWindowRateLimiterOptions = {
  windowMs: number;
  max: number;
  baseBackoffMs: number;
  maxBackoffMs: number;
  prefix: string;
  redis?: RedisClient;
};

type MemoryState = {
  hits: number[];
  violations: number;
  blockUntil?: number;
};

  export class SlidingWindowRateLimiter {
    private readonly windowMs: number;
    private readonly max: number;
    private readonly baseBackoffMs: number;
    private readonly maxBackoffMs: number;
    private readonly prefix: string;
    private readonly redis?: RedisClient;
    private readonly memoryState = new Map<string, MemoryState>();

  constructor(options: SlidingWindowRateLimiterOptions) {
    this.windowMs = options.windowMs;
    this.max = options.max;
    this.baseBackoffMs = options.baseBackoffMs;
    this.maxBackoffMs = options.maxBackoffMs;
    this.prefix = options.prefix;
    this.redis = options.redis;
  }

  async consume(key: string, now: number = Date.now()): Promise<RateLimitResult> {
    if (this.redis) {
      return this.consumeWithRedis(key, now);
    }
    return this.consumeInMemory(key, now);
  }

  private async consumeInMemory(
    key: string,
    now: number
  ): Promise<RateLimitResult> {
    const state = this.memoryState.get(key) ?? { hits: [], violations: 0 };
    const windowStart = now - this.windowMs;
    state.hits = state.hits.filter((ts) => ts >= windowStart);

    if (state.blockUntil && state.blockUntil > now) {
      return { allowed: false, retryAfterSeconds: Math.ceil((state.blockUntil - now) / 1000) };
    }

    if (state.hits.length >= this.max) {
      state.violations += 1;
      const retryMs = Math.min(this.maxBackoffMs, this.baseBackoffMs * 2 ** (state.violations - 1));
      state.blockUntil = now + retryMs;
      this.memoryState.set(key, state);
      return { allowed: false, retryAfterSeconds: Math.ceil(retryMs / 1000) };
    }

    state.hits.push(now);

    if (state.hits.length === 1) {
      state.violations = 0;
      state.blockUntil = undefined;
    }

    this.memoryState.set(key, state);
    return { allowed: true };
  }

  private async consumeWithRedis(
    key: string,
    now: number
  ): Promise<RateLimitResult> {
    const redis = this.redis!;
    const windowKey = `${this.prefix}:${key}:window`;
    const metaKey = `${this.prefix}:${key}:meta`;
    const windowStart = now - this.windowMs;

    await redis.zremrangebyscore(windowKey, 0, windowStart);
    const [blockUntilRaw, _violationsRaw] = await redis.hmget(metaKey, 'blockUntil', 'violations');
    const blockUntil = Number(blockUntilRaw ?? 0);
    // Note: violations count is tracked in Redis but only used during hincrby below

    if (blockUntil > now) {
      return { allowed: false, retryAfterSeconds: Math.ceil((blockUntil - now) / 1000) };
    }

    const current = await redis.zcard(windowKey);

    if (current >= this.max) {
      const nextViolations = await redis.hincrby(metaKey, 'violations', 1);
      const retryMs = Math.min(
        this.maxBackoffMs,
        this.baseBackoffMs * 2 ** (Math.max(nextViolations, 1) - 1)
      );
      await redis.hset(metaKey, 'blockUntil', now + retryMs);
      await redis.pexpire(metaKey, this.windowMs + this.maxBackoffMs);
      return { allowed: false, retryAfterSeconds: Math.ceil(retryMs / 1000) };
    }

    await redis.zadd(windowKey, now, String(now));
    await redis.pexpire(windowKey, this.windowMs);

    if (current === 0) {
      await redis.hset(metaKey, 'violations', 0, 'blockUntil', 0);
    }
    await redis.pexpire(metaKey, this.windowMs);

    return { allowed: true };
  }
}
