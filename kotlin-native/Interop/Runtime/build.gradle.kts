/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import org.jetbrains.kotlin.*
import org.jetbrains.kotlin.konan.target.HostManager
import java.io.ByteArrayOutputStream

val kotlinVersion = project.bootstrapKotlinVersion
plugins {
   `native`
    `kotlin`
}
//apply plugin: 'c'



val solib = when {
    org.jetbrains.kotlin.konan.target.HostManager.hostIsMingw -> "dll"
    org.jetbrains.kotlin.konan.target.HostManager.hostIsMac -> "dylib"
    else -> "so"
}

native {
    val isWindows = PlatformInfo.isWindows()
    val obj = if (isWindows) "obj" else "o"
    val lib = if (isWindows) "lib" else "a"
    val host = rootProject.project(":kotlin-native").extra["hostName"]
    val hostLibffiDir = rootProject.project(":kotlin-native").extra["${host}LibffiDir"]
    suffixes {
        (".c" to ".$obj") {
            tool(*platformManager.hostPlatform.clang.clangC("").toTypedArray())
            when (org.jetbrains.kotlin.konan.target.HostManager.host.family) {
                org.jetbrains.kotlin.konan.target.Family.LINUX -> {
                    flags("-I$hostLibffiDir/include", *platformManager.hostPlatform.clang.hostCompilerArgsForJni, "-fPIC", "-c", "-o", ruleOut(), ruleInFirst())
                }
                org.jetbrains.kotlin.konan.target.Family.MINGW -> {
                    flags("-I$hostLibffiDir/include", *platformManager.hostPlatform.clang.hostCompilerArgsForJni, "-c", "-o", ruleOut(), ruleInFirst())
                }
                org.jetbrains.kotlin.konan.target.Family.OSX -> {
                    flags("-I$hostLibffiDir/include", "-g", *platformManager.hostPlatform.clang.hostCompilerArgsForJni,
                          "-fPIC", "-c", "-o", ruleOut(), ruleInFirst())
                }
            }
        }
    }
    sourceSet {
        "callbacks" {
            dir("src/callbacks/c")
        }
    }
    val objSet = sourceSets["callbacks"]!!.transform(".c" to ".$obj")

    target("libcallbacks.$solib", objSet) {
        tool(*platformManager.hostPlatform.clang.clangCXX("").toTypedArray())
        flags("-shared",
              "-o",ruleOut(), *ruleInAll(),
              "-L${project(":kotlin-native:libclangext").buildDir}",
              "$hostLibffiDir/lib/libffi.$lib",
              "-lclangext")
    }
    tasks["libcallbacks.$solib"].apply {
        dependsOn(":kotlin-native:libclangext:libclangext.$lib")
    }
}

/*
model {
    tasks.compileCallbacksSharedLibraryCallbacksC {
        UtilsKt.configureNativePluginTask(project, delegate)
    }
    components {
        callbacks(NativeLibrarySpec) {
            sources.c.source {
                srcDir 'src/callbacks/c'
                include '** / *.c'
            }
            binaries.all {
                def host = rootProject.project(":kotlin-native").ext.hostName

                def hostLibffiDir = rootProject.project(":kotlin-native").ext.get("${host}LibffiDir")

                cCompiler.args hostPlatform.clang.hostCompilerArgsForJni
                cCompiler.args "-I$hostLibffiDir/include"

                linker.args "$hostLibffiDir/lib/libffi.a"
            }
        }
   }

   toolChains {
     clang(Clang) {
         path UtilsKt.getClangPath(project)
         eachPlatform {
             cCompiler.withArguments(ClangArgs.&filterGradleNativeSoftwareFlags)
         }
     }
   }
}*/

dependencies {
    implementation(project(":kotlin-native:utilities:basic-utils"))
    implementation(project(":kotlin-stdlib"))
    implementation(project(":kotlin-reflect"))
}

sourceSets.main.get().java.srcDir("src/jvm/kotlin")

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xuse-experimental=kotlin.ExperimentalUnsignedTypes",
                         "-Xuse-experimental=kotlin.Experimental",
                         "-Xopt-in=kotlin.RequiresOptIn",
                         "-XXLanguage:+InlineClasses")
        allWarningsAsErrors = true
    }
}


val nativelibs = project.tasks.create<Copy>("nativelibs") {
    dependsOn("libcallbacks.$solib")

    from("$buildDir/")
    into("$buildDir/nativelibs/")
}

//classes.dependsOn(nativelibs)
