plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

import java.util.Properties
import java.io.File
import com.android.build.gradle.tasks.MergeSourceSetFolders

/** Release / 默认线上地址 */
/**val releaseAppBaseUrl = "https://vpn.eodkko.xyz/"**/
/** 本地联调可临时改为：http://192.229.87.112:44080/ */
val releaseAppBaseUrl = "http://192.229.87.112:44080/"



val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

/** Release 签名：keystore.properties > local.properties > 环境变量 */
fun signingProp(vararg keys: String): String? {
    for (key in keys) {
        keystoreProperties.getProperty(key)?.takeIf { it.isNotBlank() }?.let { return it }
        localProperties.getProperty(key)?.takeIf { it.isNotBlank() }?.let { return it }
        val envKey = key.replace('.', '_').uppercase()
        System.getenv(envKey)?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return null
}

fun normalizeBaseUrl(raw: String): String {
    val trimmed = raw.trim()
    return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
}

/**
 * Debug 包 API 根地址，优先级：
 * 1. Gradle -PdebugApiBase=http://192.168.x.x:48080/
 * 2. apps/android/local.properties → debug.api.base=...
 * 3. 模拟器默认 10.0.2.2:48080
 */
val debugAppBaseUrl =
    normalizeBaseUrl(
        (project.findProperty("debugApiBase") as String?)
            ?: localProperties.getProperty("debug.api.base")
            ?: "http://10.0.2.2:48080/",
    )

/** Release 仅 arm64 分包：发版时加 -PreleaseArm64Only=true；开发/模拟器默认全 ABI */
val releaseArm64Only = (project.findProperty("releaseArm64Only") as String?) == "true"
val includeAllAbis = (project.findProperty("includeAllAbis") as String?) == "true"
/** 瘦包：APK 不内置 libclash/libbridge，首连从 CMFA Release 下载（安装包约 3MB） */
val slimNativeLibs = (project.findProperty("slimNativeLibs") as String?) == "true"
val mihomoNativeVersion = (project.findProperty("mihomoNativeVersion") as String?) ?: "v2.11.30"
val mihomoNativeApkUrl = (project.findProperty("mihomoNativeApkUrl") as String?) ?: ""
val fcmEnabled = file("google-services.json").exists()

fun buildConfigString(value: String) = "\"$value\""
fun buildConfigBool(value: Boolean) = value.toString()

android {
    namespace = "com.vpn.member"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vpn.member"
        minSdk = 26
        targetSdk = 34
        versionCode = 120
        versionName = "1.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "APP_BASE_URL", buildConfigString(releaseAppBaseUrl))
        buildConfigField("String", "API_BASE_URL", buildConfigString("${releaseAppBaseUrl}api/v1/"))
        buildConfigField("boolean", "FCM_ENABLED", buildConfigBool(fcmEnabled))
        buildConfigField("boolean", "SLIM_NATIVE_LIBS", buildConfigBool(slimNativeLibs))
        buildConfigField("String", "MIHOMO_NATIVE_VERSION", buildConfigString(mihomoNativeVersion))
        buildConfigField("String", "MIHOMO_NATIVE_APK_URL", buildConfigString(mihomoNativeApkUrl))
    }

    if (fcmEnabled) {
        apply(plugin = "com.google.gms.google-services")
    }

    splits {
        abi {
            isEnable = true
            reset()
            if (releaseArm64Only && !includeAllAbis) {
                include("arm64-v8a")
            } else {
                include("armeabi-v7a", "arm64-v8a", "x86_64")
            }
            isUniversalApk = false
        }
    }

    signingConfigs {
        create("release") {
            val storePath =
                signingProp("release.storeFile", "storeFile")
            if (!storePath.isNullOrBlank()) {
                val store = rootProject.file(storePath)
                if (store.isFile) {
                    storeFile = store
                    storePassword =
                        signingProp("release.storePassword", "storePassword").orEmpty()
                    keyAlias = signingProp("release.keyAlias", "keyAlias") ?: "key0"
                    keyPassword =
                        signingProp("release.keyPassword", "keyPassword").orEmpty()
                }
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "APP_BASE_URL", buildConfigString(debugAppBaseUrl))
            buildConfigField("String", "API_BASE_URL", buildConfigString("${debugAppBaseUrl}api/v1/"))
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val releaseSigning = signingConfigs.getByName("release")
            // 注意：Android Studio Sync 会配置所有 buildType。缺正式签名时不能在配置期 throw，
            // 否则 Debug 也无法打开工程。正式打 Release 包时由下方 taskGraph 再拦截。
            signingConfig =
                when {
                    project.hasProperty("useDebugSigning") ->
                        signingConfigs.getByName("debug")
                    releaseSigning.storeFile != null -> releaseSigning
                    else -> {
                        logger.warn(
                            "[跨云] 未配置 Release 正式签名（keystore.properties / local.properties），" +
                                "本地 Sync/Debug 暂用 debug 签名。正式出包请配置签名；临时内测可加 -PuseDebugSigning",
                        )
                        signingConfigs.getByName("debug")
                    }
                }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = false
            if (slimNativeLibs) {
                excludes += setOf("**/libclash.so", "**/libbridge.so")
            }
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDirs(
                if (fcmEnabled) "src/fcmEnabled/kotlin" else "src/fcmDisabled/kotlin",
            )
        }
    }
}

// 真正打 Release 包时才强制要求正式签名（避免 Android Studio Sync 因缺 keystore.properties 失败）
gradle.taskGraph.whenReady {
    val packingRelease =
        allTasks.any { task ->
            val n = task.name
            (n.startsWith("assemble") || n.startsWith("bundle") || n.startsWith("package")) &&
                n.contains("Release")
        }
    if (!packingRelease || project.hasProperty("useDebugSigning")) return@whenReady
    val store = android.signingConfigs.getByName("release").storeFile
    if (store == null || !store.isFile) {
        throw GradleException(
            "[跨云] Release 必须配置正式签名（keystore.properties 或 local.properties）。" +
                "临时内测可加 -PuseDebugSigning",
        )
    }
}

// packaging.resources 不作用于 assets；Release 在 mergeAssets 后剔除内置 geodata
afterEvaluate {
    tasks.named<MergeSourceSetFolders>("mergeReleaseAssets") {
        doLast {
            val outDir = outputDir.get().asFile
            delete(File(outDir, "mihomo/geosite.dat"))
            delete(File(outDir, "mihomo/geoip.metadb"))
            delete(fileTree(outDir) { include("mihomo/ruleset/**") })
        }
    }
}

// geodata 仅用于本地调试打包进 assets；Release 通过 MihomoGeoAssetManager 在线拉取
val geodataDir = layout.projectDirectory.dir("src/main/assets/mihomo")

tasks.register<Exec>("fetchMihomoGeodata") {
    group = "mihomo"
    description = "Download geodata into app assets (optional local debug fallback only)"
    workingDir = rootProject.projectDir
    commandLine("bash", "scripts/fetch-mihomo-geodata.sh")
    onlyIf {
        val geosite = geodataDir.file("geosite.dat").asFile
        val geoip = geodataDir.file("geoip.metadb").asFile
        val reject = geodataDir.file("ruleset/reject.yaml").asFile
        val cn = geodataDir.file("ruleset/cn.yaml").asFile
        listOf(geosite, geoip, reject, cn).any { !it.exists() || it.length() == 0L }
    }
}

// 不再强制 preBuild 拉 geodata；需要本地 assets 兜底时手动执行 ./gradlew fetchMihomoGeodata

/** VPN 前台服务存活时 Android Studio 常无法 terminate；install 前先 force-stop。 */
fun forceStopAppBeforeInstall() {
    val sdkDir = localProperties.getProperty("sdk.dir")?.trim().orEmpty()
    if (sdkDir.isBlank()) return
    val adbName = if (System.getProperty("os.name").lowercase().contains("windows")) "adb.exe" else "adb"
    val adb = File(sdkDir, "platform-tools/$adbName")
    if (!adb.isFile) return
    exec {
        commandLine(adb.absolutePath, "shell", "am", "force-stop", "com.vpn.member")
        isIgnoreExitValue = true
    }
    Thread.sleep(800)
}

afterEvaluate {
    listOf("installDebug", "installRelease").forEach { taskName ->
        tasks.findByName(taskName)?.doFirst { forceStopAppBeforeInstall() }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-process:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.zxing:core:3.5.3")

    if (fcmEnabled) {
        implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
        implementation("com.google.firebase:firebase-messaging-ktx")
    }

    // Mihomo 内核（Clash Meta，与订阅 Clash YAML 同源）
    implementation(project(":mihomo-core"))

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
