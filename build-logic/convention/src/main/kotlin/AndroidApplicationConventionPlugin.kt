import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import java.util.Properties

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("org.jetbrains.kotlin.android")
            }
            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = 36
                defaultConfig.applicationId = "com.vigia.copilot"
                buildFeatures.buildConfig = true

                // Read secrets.properties (local dev) — gitignored, never committed.
                // CI: export the same keys as environment variables; env vars take precedence.
                // See secrets.properties.example for the required keys.
                val secretsFile = rootProject.file("secrets.properties")
                val secrets = Properties().also { props ->
                    if (secretsFile.exists()) secretsFile.inputStream().use(props::load)
                }

                // Returns env var if set, secrets.properties value if present, or the fallback.
                fun secret(envKey: String, fallback: String = ""): String =
                    System.getenv(envKey)?.takeIf(String::isNotBlank)
                        ?: secrets.getProperty(envKey)?.takeIf(String::isNotBlank)
                        ?: fallback

                flavorDimensions += "env"
                productFlavors {
                    // demo — developer sandbox; uses secrets.properties locally,
                    //         test Stripe keys, real IoT endpoint.
                    create("demo") {
                        dimension = "env"
                        applicationIdSuffix = ".demo"
                        buildConfigField("String", "STRIPE_PUBLISHABLE_KEY",
                            "\"${secret("STRIPE_PUBLISHABLE_KEY", "pk_test_REPLACE_ME")}\"")
                        buildConfigField("String", "MQTT_BROKER_URI",
                            "\"${secret("MQTT_BROKER_URI")}\"")
                        buildConfigField("String", "VIGIA_API_BASE_URL",
                            "\"${secret("VIGIA_API_BASE_URL")}\"")
                        buildConfigField("String", "BLACKBOX_MAC",
                            "\"${secret("BLACKBOX_MAC", "00:00:00:00:00:00")}\"")
                        buildConfigField("String", "SARVAM_API_KEY",
                            "\"${secret("SARVAM_API_KEY")}\"")
                    }
                    // prod — set all env vars in CI; secrets.properties is not present
                    //         in CI runners, so env vars are the sole source.
                    create("prod") {
                        dimension = "env"
                        buildConfigField("String", "STRIPE_PUBLISHABLE_KEY",
                            "\"${secret("STRIPE_PUBLISHABLE_KEY")}\"")
                        buildConfigField("String", "MQTT_BROKER_URI",
                            "\"${secret("MQTT_BROKER_URI")}\"")
                        buildConfigField("String", "VIGIA_API_BASE_URL",
                            "\"${secret("VIGIA_API_BASE_URL")}\"")
                        buildConfigField("String", "BLACKBOX_MAC",
                            "\"${secret("BLACKBOX_MAC")}\"")
                        buildConfigField("String", "SARVAM_API_KEY",
                            "\"${secret("SARVAM_API_KEY")}\"")
                    }
                }
            }
        }
    }
}
