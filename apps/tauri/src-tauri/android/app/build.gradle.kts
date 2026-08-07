// Tauri Android 自定义依赖：由 gen/android/app/build.gradle.kts apply 引入
val implementation by configurations
dependencies {
    implementation(project(":mihomo-core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.core:core-ktx:1.13.1")
}
