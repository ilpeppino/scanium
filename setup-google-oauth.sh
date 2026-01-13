***REMOVED***!/bin/bash
***REMOVED*** Quick setup script for Google OAuth credentials

set -e

echo "🔐 Google OAuth Setup for Scanium"
echo "=================================="
echo ""
echo "Required Information:"
echo "  Package: com.scanium.app.dev"
echo "  SHA-1: 03:C5:1E:2F:FA:EA:B6:F9:AA:F9:C6:25:D4:14:08:14:57:C1:FC:A2"
echo ""
echo "Setup Guide: howto/GOOGLE_OAUTH_SETUP.md"
echo ""

***REMOVED*** Step 1: Get Client ID from user
read -p "Enter your Web OAuth Client ID from Google Cloud Console: " CLIENT_ID

if [[ -z "$CLIENT_ID" ]]; then
    echo "❌ Error: Client ID cannot be empty"
    exit 1
fi

***REMOVED*** Validate format
if [[ ! "$CLIENT_ID" =~ \.apps\.googleusercontent\.com$ ]]; then
    echo "⚠️  Warning: Client ID should end with .apps.googleusercontent.com"
    read -p "Continue anyway? (y/n): " CONTINUE
    if [[ "$CONTINUE" != "y" ]]; then
        exit 1
    fi
fi

echo ""
echo "📝 Updating Android app configuration..."

***REMOVED*** Step 2: Update Android app
sed -i '' "s/YOUR_ANDROID_CLIENT_ID\.apps\.googleusercontent\.com/$CLIENT_ID/" \
    androidApp/src/main/java/com/scanium/app/auth/CredentialManagerAuthLauncher.kt

echo "✅ Updated: androidApp/src/main/java/com/scanium/app/auth/CredentialManagerAuthLauncher.kt"

***REMOVED*** Step 3: Commit changes
echo ""
echo "📦 Committing changes..."
git add androidApp/src/main/java/com/scanium/app/auth/CredentialManagerAuthLauncher.kt
git commit -m "feat(auth): configure Google OAuth Client ID"

echo ""
echo "🚀 Pushing to origin..."
git push origin main

***REMOVED*** Step 4: Update NAS backend
echo ""
echo "🔄 Syncing with NAS and updating backend..."
ssh nas "cd /volume1/docker/scanium/repo && git pull origin main"

echo ""
echo "📝 Updating backend .env on NAS..."
ssh nas "echo 'GOOGLE_OAUTH_CLIENT_ID=$CLIENT_ID' >> /volume1/docker/scanium/repo/backend/.env"

echo ""
echo "🔄 Restarting backend API..."
ssh nas "cd /volume1/docker/scanium && docker-compose restart api"

echo ""
echo "✅ Setup complete!"
echo ""
echo "Next steps:"
echo "  1. Rebuild app: ./gradlew :androidApp:assembleDevDebug"
echo "  2. Install: adb install androidApp/build/outputs/apk/dev/debug/androidApp-dev-debug.apk"
echo "  3. Test sign-in in Settings → General"
echo ""
echo "If you still see errors, wait 5-10 minutes for Google to propagate your credentials."
echo ""

