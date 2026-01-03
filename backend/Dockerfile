***REMOVED*** Multi-stage build for Scanium Backend
***REMOVED*** Node 20 LTS

***REMOVED*** Stage 1: Production dependencies + Prisma generate
FROM node:20-bookworm-slim AS prod-deps

WORKDIR /app

***REMOVED*** Copy package files
COPY package.json package-lock.json* ./

***REMOVED*** Install production dependencies
RUN npm ci --only=production && \
    npm cache clean --force

***REMOVED*** Prisma schema is needed for generate
COPY prisma ./prisma

***REMOVED*** Generate Prisma client into production node_modules
RUN npx prisma generate

***REMOVED*** Stage 2: Builder
FROM node:20-bookworm-slim AS builder

WORKDIR /app

***REMOVED*** Copy package files
COPY package.json package-lock.json* ./

***REMOVED*** Install all dependencies (including dev)
RUN npm ci

***REMOVED*** Copy source code
COPY . .

***REMOVED*** Generate Prisma client
RUN npx prisma generate

***REMOVED*** Build TypeScript
RUN npm run build

***REMOVED*** Stage 3: Runner
FROM node:20-bookworm-slim AS runner

WORKDIR /app

***REMOVED*** Install dumb-init for proper signal handling and OpenSSL for Prisma engines
RUN apt-get update && \
    apt-get install -y --no-install-recommends dumb-init openssl ca-certificates && \
    rm -rf /var/lib/apt/lists/*

***REMOVED*** Create non-root user
RUN addgroup --system --gid 1001 nodejs && \
    adduser --system --uid 1001 nodejs

***REMOVED*** Copy production dependencies
COPY --from=prod-deps --chown=nodejs:nodejs /app/node_modules ./node_modules

***REMOVED*** Copy built application
COPY --from=builder --chown=nodejs:nodejs /app/dist ./dist
COPY --from=builder --chown=nodejs:nodejs /app/prisma ./prisma
COPY --from=builder --chown=nodejs:nodejs /app/package.json ./package.json
COPY --from=builder --chown=nodejs:nodejs /app/config ./config

***REMOVED*** Switch to non-root user
USER nodejs

***REMOVED*** Expose port
EXPOSE 8080

***REMOVED*** Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD node -e "require('http').get('http://127.0.0.1:8080/health', (r) => {process.exit(r.statusCode === 200 ? 0 : 1)}).on('error', () => process.exit(1))"

***REMOVED*** Start server with dumb-init for signal handling
ENTRYPOINT ["dumb-init", "--"]
CMD ["node", "dist/main.js"]
