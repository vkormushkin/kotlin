package org.jetbrains.gradle.plugins.native.tools

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import java.io.File

open class NativePlugin: Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create("native", NativeToolsExtension::class.java, project)
    }
}

open class SourceSet(val sourceSets: SourceSets, 
                     val name: String, 
                     val initialDirectory: File = sourceSets.project.projectDir, 
                     val initialSourceSet:SourceSet? = null, 
                     val rule:Pair<String, String>? = null) {
    var collection = sourceSets.project.objects.fileCollection() as FileCollection
    fun file(path:String) {
        collection = collection.plus(sourceSets.project.files("${initialDirectory.absolutePath}/$path"))
    }
    fun dir(path:String) {
        println("initial: $initialDirectory")
        sourceSets.project.fileTree("${initialDirectory.absolutePath}/$path").files.forEach {
            collection = collection.plus(sourceSets.project.files(it))
        }
    }

    fun transform(suffixes:Pair<String, String>): SourceSet {
        return SourceSet(sourceSets,
            "${name}",
            sourceSets.project.file("${sourceSets.project.buildDir}/$name/${suffixes.first}_${suffixes.second}/"), 
            this,
            suffixes)
    }
    
    fun implicitTasks():Array<Task> {
        rule ?: return emptyArray()
        println("implicitTasks: ${rule}")
        initialSourceSet?.implicitTasks()
        println("${rule} implicitTasks: ${initialSourceSet!!.collection.files.joinToString{it.name}}")
        return initialSourceSet!!.collection
            .filter { !it.isDirectory() }
            .filter { it.name.endsWith(rule.first)}
            .map { it.relativeTo(initialSourceSet.initialDirectory) }
            .map { it.path }
            .map { it to (it.substring(0, it.lastIndexOf(rule.first)) + rule.second) }
            .map {
                file(it.second)
                println("second: ${it.second}")
                sourceSets.project.file("${initialSourceSet.initialDirectory.path}/${it.first}") to sourceSets.project.file("${initialDirectory.path}/${it.second}")
            }.map {
                println("create task: ${it.second.name}")
                sourceSets.project.tasks.create(it.second.name) {
                    if(initialSourceSet.rule != null)
                        dependsOn(it.first.name)
                    doLast {
                        val toolConfiguration = object : ToolPattern{
                            var tool:String = ""
                            var args:Array<String> = emptyArray()
                            override fun ruleOut(): String = it.second.path
                            override fun ruleInFirst(): String = it.first.path
                            override fun ruleInAll(): Array<String> = arrayOf(it.second.name)

                            override fun flags(vararg args: String) {
                                this.args = arrayOf(*args)
                            }

                            override fun tool(arg: String) {
                                tool = arg
                            }

                            override fun env(name: String): Array<String> = emptyArray()

                        }
                        sourceSets.extension.toolPatterns[rule]!!.invoke(toolConfiguration)
                        it.second.parentFile.mkdirs()
                        sourceSets.project.exec {
                            executable(toolConfiguration.tool)
                            args(*toolConfiguration.args)
                        }
                    }
                }
            }.toTypedArray()
    }
}

class SourceSets(val project: Project, val extension: NativeToolsExtension, val sources:MutableMap<String, SourceSet>):MutableMap<String, SourceSet> by sources {
    operator fun String.invoke(initialDirectory:File = project.projectDir, configuration:SourceSet.()->Unit) {
        sources[this] = SourceSet(this@SourceSets, this, initialDirectory).also {
            configuration(it)
        }
    }
}



interface Environment {
    operator fun String.invoke(vararg values:String)
}
interface ToolPattern {
    fun ruleOut(): String
    fun ruleInFirst(): String
    fun ruleInAll(): Array<String>
    fun flags(vararg args: String): Unit
    fun tool(arg: String): Unit
    fun env(name: String): Array<String>
}


typealias ToolPatternConfiguration = ToolPattern.() -> Unit
typealias EnvironmentConfiguration = Environment.() -> Unit
class ToolConfigurationPatterns(val extension:NativeToolsExtension, val patterns: MutableMap<Pair<String, String>, ToolPatternConfiguration>):MutableMap<Pair<String, String>, ToolPatternConfiguration> by patterns {
    operator fun Pair<String, String>.invoke(configuration: ToolPatternConfiguration) {
        patterns[this] = configuration
    }
}


open class NativeToolsExtension(val project:Project) {
    val sourceSets = SourceSets(project, this, mutableMapOf<String, SourceSet>())
    val toolPatterns = ToolConfigurationPatterns(this, mutableMapOf<Pair<String, String>, ToolPatternConfiguration>())
    fun sourceSet(configuration: SourceSets.() -> Unit) {
        sourceSets.configuration()
    }
    var environmentConfiguration:EnvironmentConfiguration? = null
    fun environment(configuration: EnvironmentConfiguration) {
        environmentConfiguration = configuration
    }
    fun suffixes(configuration:ToolConfigurationPatterns.()->Unit) = toolPatterns.configuration()

    fun target(name:String, vararg objSet: SourceSet, configuration: ToolPatternConfiguration) {
        project.tasks.create(name) {
            objSet.forEach{
                dependsOn(it.implicitTasks())
            }
            val deps = objSet.flatMap{ it.collection.files }.map { it.path }
            val toolConfiguration = object : ToolPattern{
                var tool:String = ""
                var args:Array<String> = emptyArray()
                override fun ruleOut(): String = "${project.buildDir.path}/$name"
                override fun ruleInFirst(): String = deps.first()
                override fun ruleInAll(): Array<String> = deps.toTypedArray()

                override fun flags(vararg args: String) {
                    this.args = arrayOf(*args)
                }

                override fun tool(arg: String) {
                    tool = arg
                }
                override fun env(name: String): Array<String> = emptyArray()
            }
            toolConfiguration.configuration()
            doLast {
                project.file(toolConfiguration.ruleOut()).parentFile.mkdirs()
                sourceSets.project.exec {
                    executable(toolConfiguration.tool)
                    args(*toolConfiguration.args)
                }
            }
        }
    }
}