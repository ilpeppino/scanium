# Scanium - Development Setup

## Prerequisites

### Required
- **Java 17** (JDK 17)
- **Android SDK** (automatically detected from `local.properties`)
- **Android Studio** (recommended) or command-line tools

### Java 17 Installation

The project requires Java 17 to build. Gradle will automatically detect and use it via the toolchain feature.

#### macOS
```bash
# Using Homebrew
brew install openjdk@17

# Using SDKMAN
sdk install java 17.0.9-tem

# Or download from: https://adoptium.net/temurin/releases/?version=17
```

#### Linux
```bash
# Ubuntu/Debian
sudo apt install openjdk-17-jdk

# Fedora/RHEL
sudo dnf install java-17-openjdk-devel

# Using SDKMAN (all Linux distros)
sdk install java 17.0.9-tem
```

#### Windows
```powershell
# Using Chocolatey
choco install temurin17

# Or download from: https://adoptium.net/temurin/releases/?version=17
```

#### Using mise (all platforms)
```bash
mise install java@17
mise use java@17
```

## Building the Project

### OS-Agnostic Build (Recommended)
```bash
# Gradle will automatically find Java 17
./gradlew assembleDebug
```

### If You Have Multiple Java Versions

Gradle's toolchain feature will automatically find Java 17. No manual configuration needed!

If the build fails with Java version errors, verify Java 17 is installed:

```bash
# Check installed Java versions (macOS)
/usr/libexec/java_home -V

# Check installed Java versions (Linux)
update-java-alternatives -l

# Check current Java version
java -version
```

### Troubleshooting

#### Error: "Value given for org.gradle.java.home is invalid"
This means a hardcoded Java path was set in `gradle.properties`. This file should **not** contain `org.gradle.java.home` to remain portable.

**Fix:** Remove or comment out `org.gradle.java.home` in `gradle.properties`

#### Build works on one machine but not another
1. Ensure Java 17 is installed on both machines
2. Verify `gradle.properties` doesn't contain system-specific paths
3. Stop all Gradle daemons: `./gradlew --stop`
4. Try again: `./gradlew assembleDebug`

#### Using a specific Java version temporarily
```bash
# macOS/Linux
JAVA_HOME=/path/to/jdk-17 ./gradlew assembleDebug

# Windows PowerShell
$env:JAVA_HOME="C:\Path\To\jdk-17"; ./gradlew assembleDebug

# Windows CMD
set JAVA_HOME=C:\Path\To\jdk-17 && gradlew assembleDebug
```

## Development Workflow

### Clean Build
```bash
./gradlew clean assembleDebug
```

### Run Tests
```bash
./gradlew test
```

### Install on Device
```bash
./gradlew installDebug
```

### View Logs
```bash
adb logcat | grep Scanium
```

## Project Structure

The project is intentionally **portable** and **OS-agnostic**:
- ✅ No hardcoded paths in build files
- ✅ Gradle toolchain auto-detects Java 17
- ✅ `local.properties` (machine-specific) is gitignored
- ✅ Works on macOS, Linux, and Windows
- ✅ Compatible with multiple laptops/environments

## IDE Setup

### Android Studio (Recommended)
1. Open project in Android Studio
2. Android Studio will automatically configure Java toolchain
3. No additional setup needed

### IntelliJ IDEA
1. Open project
2. Go to: **File** → **Project Structure** → **Project**
3. Set Project SDK to Java 17
4. Gradle will use toolchain automatically

### VS Code
1. Install extensions:
   - Kotlin Language
   - Gradle for Java
   - Android
2. Ensure Java 17 is in PATH
3. Open project and sync Gradle

## Important Files (Don't Commit!)

These files are **machine-specific** and already in `.gitignore`:
- `local.properties` - Your Android SDK path
- `.idea/` - IDE settings
- `*.iml` - IDE module files
- `.gradle/` - Gradle cache

## Configuration Files (Safe to Commit)

These files are **portable** and should be committed:
- `gradle.properties` - Project-wide Gradle settings (no system-specific paths)
- `build.gradle.kts` - Build configuration
- `.gitignore` - Ignore patterns

## Getting Help

If you encounter issues:
1. Check you have Java 17 installed: `java -version`
2. Stop Gradle daemons: `./gradlew --stop`
3. Clean and rebuild: `./gradlew clean assembleDebug`
4. Check GitHub issues or create a new one

## Multi-Machine Development

Working across multiple machines? This setup is designed for you:

1. **Clone on new machine**
   ```bash
   git clone <repo-url>
   cd scanium
   ```

2. **Install Java 17** (see platform instructions above)

3. **Build**
   ```bash
   ./gradlew assembleDebug
   ```

That's it! The project will work the same on every machine.
