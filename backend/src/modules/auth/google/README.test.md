***REMOVED*** Google Auth Module Tests

This directory contains comprehensive tests for the Google Auth module (Phase C).

***REMOVED******REMOVED*** Test Files

***REMOVED******REMOVED******REMOVED*** 1. `session-service.test.ts`
Tests for the session service functions:
- `createSession()` - Creating sessions with/without refresh tokens
- `verifySession()` - Token verification and expiry
- `refreshSession()` - Token refresh and rotation
- `revokeSession()` - Session revocation
- `cleanupExpiredSessions()` - Cleanup logic
- Token security (randomness, hashing)

**Coverage**: ~30 test cases

***REMOVED******REMOVED******REMOVED*** 2. `cleanup-job.test.ts`
Tests for the session cleanup job:
- Constructor and initialization
- Immediate cleanup on start
- Periodic cleanup scheduling
- Stop/shutdown behavior
- Error handling
- Integration with session lifecycle

**Coverage**: ~10 test cases

***REMOVED******REMOVED******REMOVED*** 3. `routes.test.ts`
Integration tests for HTTP endpoints:
- `POST /v1/auth/google` - Login flow
- `POST /v1/auth/refresh` - Token refresh
- `POST /v1/auth/logout` - Logout
- `GET /v1/auth/me` - User profile
- Observability (metrics and logs)
- Error handling and validation

**Coverage**: ~20 test cases

***REMOVED******REMOVED*** Running Tests

***REMOVED******REMOVED******REMOVED*** Prerequisites
1. Database running (tests use Prisma)
2. Environment variables configured (handled by `test-setup.ts`)

***REMOVED******REMOVED******REMOVED*** Run All Tests
```bash
npm test
```

***REMOVED******REMOVED******REMOVED*** Run Auth Tests Only
```bash
npm test -- session-service.test.ts
npm test -- cleanup-job.test.ts
npm test -- routes.test.ts
```

***REMOVED******REMOVED******REMOVED*** Run in Watch Mode
```bash
npm run test:watch
```

***REMOVED******REMOVED******REMOVED*** Run with Coverage
```bash
npm test -- --coverage
```

***REMOVED******REMOVED*** Test Database

Tests use the `DATABASE_URL` from environment or fall back to:
```
postgresql://user:pass@localhost:5432/testdb
```

**IMPORTANT**: Tests will create and delete test data. Use a separate test database!

***REMOVED******REMOVED******REMOVED*** Setup Test Database
```bash
***REMOVED*** Option 1: Use local Postgres
docker run -d --name scanium-test-db \
  -e POSTGRES_USER=scanium_test \
  -e POSTGRES_PASSWORD=test_password \
  -e POSTGRES_DB=scanium_test \
  -p 5433:5432 \
  postgres:15

***REMOVED*** Set DATABASE_URL for tests
export DATABASE_URL="postgresql://scanium_test:test_password@localhost:5433/scanium_test"

***REMOVED*** Run migrations
cd backend
npx prisma migrate deploy
```

***REMOVED******REMOVED*** Test Patterns

***REMOVED******REMOVED******REMOVED*** Session Service Tests
- Use `beforeEach`/`afterEach` to create/cleanup test users
- Use short expiry times (1-60 seconds) for faster tests
- Use `setTimeout` with `await new Promise` for expiry tests
- Always cleanup test data to avoid interference

***REMOVED******REMOVED******REMOVED*** Route Tests
- Build full Fastify app with `buildApp(mockConfig)`
- Use `app.inject()` for HTTP requests (no actual network)
- Mock external dependencies (`GoogleOAuth2Verifier.verify`)
- Verify both response body and database state

***REMOVED******REMOVED******REMOVED*** Cleanup Job Tests
- Use custom short intervals (200-500ms) for faster tests
- Mock `console.log`/`console.error` to verify logging
- Test both immediate and periodic cleanup
- Always call `job.stop()` in `afterEach`

***REMOVED******REMOVED*** Mocking

***REMOVED******REMOVED******REMOVED*** Google Token Verifier
```typescript
vi.spyOn(tokenVerifier.GoogleOAuth2Verifier.prototype, 'verify')
  .mockResolvedValue({
    sub: 'google-sub-123',
    email: 'test@example.com',
    name: 'Test User',
    picture: 'https://example.com/avatar.jpg',
  });
```

***REMOVED******REMOVED******REMOVED*** Prisma
Tests use real Prisma client against test database. To mock Prisma:
```typescript
vi.spyOn(prisma.session, 'deleteMany')
  .mockResolvedValue({ count: 5 });
```

***REMOVED******REMOVED*** Common Issues

***REMOVED******REMOVED******REMOVED*** Tests Timeout
- Increase Vitest timeout: `it('test', async () => {}, 10000)`
- Check database connection
- Verify cleanup jobs are stopped in `afterEach`

***REMOVED******REMOVED******REMOVED*** Tests Fail with "User not found"
- Ensure `beforeEach` creates test user
- Verify `afterEach` cleanup doesn't run before assertions
- Check for race conditions in async cleanup

***REMOVED******REMOVED******REMOVED*** Tests Fail with "Token expired"
- Short expiry times may cause flakiness
- Add buffer time (e.g., 1100ms for 1s expiry)
- Use longer expiry for non-expiry tests

***REMOVED******REMOVED*** Coverage Goals

- Session Service: >90% coverage
- Cleanup Job: >85% coverage
- Routes: >80% coverage (excludes some error paths)

***REMOVED******REMOVED*** Future Enhancements

- Add rate limiting tests
- Add concurrency tests (multiple simultaneous logins)
- Add performance benchmarks
- Add integration tests with real Google OAuth
- Add E2E tests with Android client
