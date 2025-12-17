***REMOVED*** Scanium - Development Setup

***REMOVED******REMOVED*** Prerequisites

***REMOVED******REMOVED******REMOVED*** Required
- **Java 17** (JDK 17)
- **Android SDK** (automatically detected from `local.properties`)
- **Android Studio** (recommended) or command-line tools

***REMOVED******REMOVED******REMOVED*** Java 17 Installation

The project requires Java 17 to build. Gradle will automatically detect and use it via the toolchain feature.

***REMOVED******REMOVED******REMOVED******REMOVED*** macOS
```bash
***REMOVED*** Using Homebrew
brew install openjdk@17

***REMOVED*** Using SDKMAN
sdk install java 17.0.9-tem

***REMOVED*** Or download from: https://adoptium.net/temurin/releases/?version=17
```

***REMOVED******REMOVED******REMOVED******REMOVED*** Linux
```bash
***REMOVED*** Ubuntu/Debian
sudo apt install openjdk-17-jdk

***REMOVED*** Fedora/RHEL
sudo dnf install java-17-openjdk-devel

***REMOVED*** Using SDKMAN (all Linux distros)
sdk install java 17.0.9-tem
```

***REMOVED******REMOVED******REMOVED******REMOVED*** Windows
```powershell
***REMOVED*** Using Chocolatey
choco install temurin17

***REMOVED*** Or download from: https://adoptium.net/temurin/releases/?version=17
```

***REMOVED******REMOVED******REMOVED******REMOVED*** Using mise (all platforms)
```bash
mise install java@17
mise use java@17
```

***REMOVED******REMOVED*** Building the Project

***REMOVED******REMOVED******REMOVED*** OS-Agnostic Build (Recommended)
```bash
***REMOVED*** Gradle will automatically find Java 17
./gradlew assembleDebug
```

***REMOVED******REMOVED******REMOVED*** If You Have Multiple Java Versions

Gradle's toolchain feature will automatically find Java 17. No manual configuration needed!

If the build fails with Java version errors, verify Java 17 is installed:

```bash
***REMOVED*** Check installed Java versions (macOS)
/usr/libexec/java_home -V

***REMOVED*** Check installed Java versions (Linux)
update-java-alternatives -l

***REMOVED*** Check current Java version
java -version
```

***REMOVED******REMOVED******REMOVED*** Troubleshooting

***REMOVED******REMOVED******REMOVED******REMOVED*** Error: "Value given for org.gradle.java.home is invalid"
This means a hardcoded Java path was set in `gradle.properties`. This file should **not** contain `org.gradle.java.home` to remain portable.

**Fix:** Remove or comment out `org.gradle.java.home` in `gradle.properties`

***REMOVED******REMOVED******REMOVED******REMOVED*** Build works on one machine but not another
1. Ensure Java 17 is installed on both machines
2. Verify `gradle.properties` doesn't contain system-specific paths
3. Stop all Gradle daemons: `./gradlew --stop`
4. Try again: `./gradlew assembleDebug`

***REMOVED******REMOVED******REMOVED******REMOVED*** Using a specific Java version temporarily
```bash
***REMOVED*** macOS/Linux
JAVA_HOME=/path/to/jdk-17 ./gradlew assembleDebug

***REMOVED*** Windows PowerShell
$env:JAVA_HOME="C:\Path\To\jdk-17"; ./gradlew assembleDebug

***REMOVED*** Windows CMD
set JAVA_HOME=C:\Path\To\jdk-17 && gradlew assembleDebug
```

***REMOVED******REMOVED*** Development Workflow

***REMOVED******REMOVED******REMOVED*** Clean Build
```bash
./gradlew clean assembleDebug
```

***REMOVED******REMOVED******REMOVED*** Run Tests
```bash
./gradlew test
```

***REMOVED******REMOVED******REMOVED*** Install on Device
```bash
./gradlew installDebug
```

***REMOVED******REMOVED******REMOVED*** View Logs
```bash
adb logcat | grep Scanium
```

***REMOVED******REMOVED*** Project Structure

The project is intentionally **portable** and **OS-agnostic**:
- ✅ No hardcoded paths in build files
- ✅ Gradle toolchain auto-detects Java 17
- ✅ `local.properties` (machine-specific) is gitignored
- ✅ Works on macOS, Linux, and Windows
- ✅ Compatible with multiple laptops/environments

***REMOVED******REMOVED*** IDE Setup

***REMOVED******REMOVED******REMOVED*** Android Studio (Recommended)
1. Open project in Android Studio
2. Android Studio will automatically configure Java toolchain
3. No additional setup needed

***REMOVED******REMOVED******REMOVED*** IntelliJ IDEA
1. Open project
2. Go to: **File** → **Project Structure** → **Project**
3. Set Project SDK to Java 17
4. Gradle will use toolchain automatically

***REMOVED******REMOVED******REMOVED*** VS Code
1. Install extensions:
   - Kotlin Language
   - Gradle for Java
   - Android
2. Ensure Java 17 is in PATH
3. Open project and sync Gradle

***REMOVED******REMOVED*** Important Files (Don't Commit!)

These files are **machine-specific** and already in `.gitignore`:
- `local.properties` - Your Android SDK path
- `.idea/` - IDE settings
- `*.iml` - IDE module files
- `.gradle/` - Gradle cache

***REMOVED******REMOVED*** Configuration Files (Safe to Commit)

These files are **portable** and should be committed:
- `gradle.properties` - Project-wide Gradle settings (no system-specific paths)
- `build.gradle.kts` - Build configuration
- `.gitignore` - Ignore patterns

***REMOVED******REMOVED*** Getting Help

If you encounter issues:
1. Check you have Java 17 installed: `java -version`
2. Stop Gradle daemons: `./gradlew --stop`
3. Clean and rebuild: `./gradlew clean assembleDebug`
4. Check GitHub issues or create a new one

***REMOVED******REMOVED*** Multi-Machine Development

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
