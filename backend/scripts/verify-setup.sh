***REMOVED***!/bin/bash

***REMOVED*** Scanium Backend Setup Verification Script
***REMOVED*** Run this after initial setup to verify everything is configured correctly

set -e

echo "🔍 Verifying Scanium Backend Setup..."
echo ""

***REMOVED*** Check Node version
echo "📦 Checking Node.js version..."
NODE_VERSION=$(node -v | cut -d'v' -f2 | cut -d'.' -f1)
if [ "$NODE_VERSION" -lt 20 ]; then
    echo "❌ Node.js 20 or higher required. Current: $(node -v)"
    exit 1
fi
echo "✅ Node.js version: $(node -v)"
echo ""

***REMOVED*** Check if .env exists
echo "📄 Checking .env file..."
if [ ! -f .env ]; then
    echo "❌ .env file not found!"
    echo "   Copy .env.example to .env and fill in your configuration:"
    echo "   cp .env.example .env"
    exit 1
fi
echo "✅ .env file found"
echo ""

***REMOVED*** Check required environment variables
echo "🔑 Checking required environment variables..."
REQUIRED_VARS=(
    "NODE_ENV"
    "PORT"
    "PUBLIC_BASE_URL"
    "DATABASE_URL"
    "EBAY_ENV"
    "EBAY_CLIENT_ID"
    "EBAY_CLIENT_SECRET"
    "EBAY_SCOPES"
    "SESSION_SIGNING_SECRET"
    "CORS_ORIGINS"
)

source .env
for VAR in "${REQUIRED_VARS[@]}"; do
    if [ -z "${!VAR}" ]; then
        echo "❌ Missing required variable: $VAR"
        exit 1
    fi
done
echo "✅ All required environment variables present"
echo ""

***REMOVED*** Check session secret length
echo "🔐 Checking SESSION_SIGNING_SECRET length..."
SECRET_LENGTH=${***REMOVED***SESSION_SIGNING_SECRET}
if [ "$SECRET_LENGTH" -lt 32 ]; then
    echo "⚠️  SESSION_SIGNING_SECRET is too short ($SECRET_LENGTH chars)"
    echo "   Generate a strong secret with: openssl rand -base64 32"
fi
echo "✅ Session secret length: $SECRET_LENGTH chars"
echo ""

***REMOVED*** Check if node_modules exists
echo "📦 Checking dependencies..."
if [ ! -d "node_modules" ]; then
    echo "⚠️  node_modules not found. Installing dependencies..."
    npm install
fi
echo "✅ Dependencies installed"
echo ""

***REMOVED*** Generate Prisma client
echo "🗄️  Generating Prisma client..."
npm run prisma:generate > /dev/null 2>&1
echo "✅ Prisma client generated"
echo ""

***REMOVED*** Type check
echo "📝 Running TypeScript type check..."
if npm run typecheck > /dev/null 2>&1; then
    echo "✅ TypeScript compilation successful"
else
    echo "❌ TypeScript compilation failed"
    echo "   Run 'npm run typecheck' for details"
    exit 1
fi
echo ""

***REMOVED*** Run tests
echo "🧪 Running tests..."
if npm run test > /dev/null 2>&1; then
    echo "✅ Tests passed"
else
    echo "⚠️  Some tests failed"
    echo "   Run 'npm run test' for details"
fi
echo ""

***REMOVED*** Build
echo "🔨 Building application..."
if npm run build > /dev/null 2>&1; then
    echo "✅ Build successful"
else
    echo "❌ Build failed"
    echo "   Run 'npm run build' for details"
    exit 1
fi
echo ""

***REMOVED*** Docker build (if Docker is available)
if command -v docker &> /dev/null; then
    echo "🐳 Testing Docker build..."
    if docker build -t scanium-backend:test . > /dev/null 2>&1; then
        echo "✅ Docker build successful"
    else
        echo "⚠️  Docker build failed"
        echo "   Run 'docker build -t scanium-backend:test .' for details"
    fi
    echo ""
else
    echo "⚠️  Docker not found, skipping Docker build test"
    echo ""
fi

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Setup verification complete!"
echo ""
echo "Next steps:"
echo "  1. Start PostgreSQL: docker-compose up postgres -d"
echo "  2. Run migrations: npm run prisma:migrate"
echo "  3. Start dev server: npm run dev"
echo "  4. Test health check: curl http://localhost:8080/healthz"
echo ""
echo "For deployment to NAS, see SETUP_GUIDE.md"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
