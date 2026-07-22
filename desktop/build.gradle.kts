import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm("desktop")
    
    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.materialIconsExtended)
                
                implementation(libs.androidx.datastore.core)
                implementation(libs.androidx.datastore.preferences)
                implementation(libs.kotlinx.serialization.json)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")

                implementation("com.google.api-client:google-api-client:2.2.0")
                implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
                implementation("com.google.apis:google-api-services-calendar:v3-rev20220715-2.0.0")
                implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0")
                implementation("com.google.auth:google-auth-library-oauth2-http:1.19.0")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "TheBigCalendar"
            packageVersion = "1.0.0"

            linux {
                shortcut = true // garantir a criação do atalho no menu
                menuGroup = "Office;Calendar;"
                appCategory = "Office"

                iconFile.set(project.file("src/desktopMain/resources/icon.png"))
            }
        }
    }
}
