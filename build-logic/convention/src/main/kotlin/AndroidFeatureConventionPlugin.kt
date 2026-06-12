import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("vigia.android.library")
                apply("vigia.android.library.compose")
                apply("vigia.android.hilt")
            }
            dependencies {
                add("implementation", project(":core:model"))
                add("implementation", catalogLibrary("androidx.hilt.navigation.compose"))
                add("implementation", catalogLibrary("androidx.lifecycle.viewmodel.compose"))
                add("implementation", catalogLibrary("androidx.lifecycle.runtime.compose"))
            }
        }
    }
}

internal fun Project.catalogLibrary(alias: String) =
    extensions.getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java)
        .named("libs")
        .findLibrary(alias)
        .get()
