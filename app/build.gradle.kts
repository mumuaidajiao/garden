import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// 签名密码、服务器地址、称呼都放 local.properties（那个文件不进版本库）。
// 读不到就退回默认值 —— 调试时不该因为缺个文件就编译不过。
//
// ⚠️ 必须用 **Reader + UTF-8**，不能图省事用 `load(inputStream)`：
//    那个重载按 ISO-8859-1 解码，里面的中文（称呼、名字）会变成
//    「å ¥å ¥å」这种乱码，而且**编译期一声不吭**，装到手机上才现形。
//    2026-09-19 真机验证时抓到的。
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.reader(Charsets.UTF_8).use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 注意：这个 App 里【没有】任何 API Key。
// 所有模型调用都在服务器侧完成，APK 传出去也拿不到密钥，
// 而且提示词和护栏改一次不用让她重装。

android {
    namespace = "com.garden.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.garden.app"
        minSdk = 26
        targetSdk = 36
        // 【发版规则 · 2026-09-18 起】每次发版都要往上顶一档，不许原地不动：
        //     versionCode +1，versionName 小数点后 +1（1.0 → 1.1 → 1.2 ……）
        //   原因：她手机上装的是 release 版，荣耀会掐掉三方 logcat，
        //   出了事只能靠版本号判断她手里到底是哪一版 —— 全都叫 1.0 就等于没有版本号。
        //   1.0 = 2026-09-18 20:25 装上去的那一版（本规则的起点）。
        //   1.1 = 2026-09-19 UI 改造（设计系统 + 底部导航 + 快捷入口）。
        versionCode = 1
        versionName = "1.0"

        // 服务器地址与令牌的【编译时默认值】。
        //
        // 从 local.properties 读 —— 那个文件不进版本库（见 .gitignore），
        // 所以真值只存在于这台机器上，代码和 APK 里都不写死。
        // 读不到就是空串，App 启动后会自动引导去设置页填。
        buildConfigField(
            "String", "DEFAULT_BASE_URL",
            "\"${localProps.getProperty("garden.baseUrl").orEmpty()}\""
        )
        buildConfigField(
            "String", "DEFAULT_TOKEN",
            "\"${localProps.getProperty("garden.token").orEmpty()}\""
        )
        // 她在昵称栏显示的名字、他对她的称呼 —— 开源版改成自己的
        buildConfigField(
            "String", "HER_NAME",
            "\"${localProps.getProperty("garden.herName") ?: "我"}\""
        )
        buildConfigField(
            "String", "HIM_NAME",
            "\"${localProps.getProperty("garden.himName") ?: "他"}\""
        )
        buildConfigField(
            "String", "HIM_CALL",
            "\"${localProps.getProperty("garden.himCall") ?: "他"}\""
        )
        buildConfigField(
            "String", "HER_CALL",
            "\"${localProps.getProperty("garden.herCall") ?: "你"}\""
        )
        buildConfigField(
            "String", "AI_NAME",
            "\"${localProps.getProperty("garden.aiName") ?: "小园"}\""
        )
        // 在一起的起算日，格式 yyyy-MM-dd
        buildConfigField(
            "String", "START_DATE",
            "\"${localProps.getProperty("garden.startDate") ?: ""}\""
        )
    }

    // 正式签名。
    //
    // 想发 release 包就自己生成一把（命令见 local.properties.example），
    // 而且**这把钥匙要一直留着**：以后升级必须用同一把，否则会提示
    // "签名不一致"、装不上，只能卸载重装（数据全丢）。
    // 不配也行 —— 会退回 debug 签名，能装能跑，只是将来不能覆盖升级。
    val ksFile = rootProject.file("release.jks")
    signingConfigs {
        if (ksFile.exists()) {
            create("release") {
                storeFile = ksFile
                storePassword = localProps.getProperty("garden.storePassword").orEmpty()
                keyAlias = localProps.getProperty("garden.keyAlias") ?: "release"
                keyPassword = localProps.getProperty("garden.keyPassword").orEmpty()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // ⚠️ 没配密钥时必须退回 debug 签名，不能什么都不配 ——
            //    那样产物是 app-release-unsigned.apk，装不上，
            //    而报错（INSTALL_PARSE_FAILED_NO_CERTIFICATES）看不出是签名的事。
            signingConfig = if (ksFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
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

    buildFeatures {
        compose = true
        // AGP 8 起默认关着 —— 上面那几个 buildConfigField 要用它
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)
}
