package org.nixos.gradle2nix

import org.gradle.api.Project
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.internal.artifacts.dsl.RepositoryHandlerInternal
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository
import org.gradle.plugin.management.PluginRequest
import org.gradle.plugin.use.internal.PluginDependencyResolutionServices
import java.lang.reflect.Method
import javax.inject.Inject

internal open class PluginResolver @Inject constructor(
    private val project: Project,
    private val pluginDependencyResolutionServices: PluginDependencyResolutionServices
) {
    private val drs: DependencyResolutionServices
        get() {
            val method: Method = PluginDependencyResolutionServices::class.java.getDeclaredMethod("getDependencyResolutionServices")
            method.isAccessible = true
            return method.invoke(pluginDependencyResolutionServices) as DependencyResolutionServices
        }

    private val configurations = drs.configurationContainer

    private val resolver = ConfigurationResolverFactory(
        project,
        ConfigurationScope.PLUGIN,
        drs.resolveRepositoryHandler.filterIsInstance<ResolutionAwareRepository>()
    ).create(drs.dependencyHandler)

    fun resolve(pluginRequests: List<PluginRequest>): List<DefaultArtifact> {
        val markerDependencies = pluginRequests.map { request ->
            request.module?.let { module ->
                ApiHack.defaultExternalModuleDependency(module.group, module.name, module.version)
            } ?: request.id.id.let { id ->
                ApiHack.defaultExternalModuleDependency(id, "$id.gradle.plugin", request.version)
            }
        }
        return resolver.resolve(configurations.detachedConfiguration(*markerDependencies.toTypedArray()))
    }
}
