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
import org.jetbrains.kotlin.konan.target.Family.*
import java.io.ByteArrayOutputStream

//buildscript {
//    extra["rootBuildDirectory"] = file("..")
//
//    apply (from = "${project.extra["rootBuildDirectory"]}/gradle/kotlinGradlePlugin.gradle")
//
//    dependencies {
//        classpath("org.jetbrains.kotlin:kotlin-stdlib:${project.bootstrapKotlinVersion}")
//    }
//}
plugins{
    `native`
}
import org.jetbrains.kotlin.konan.target.ClangArgs
import org.jetbrains.kotlin.PlatformInfo
project.extra["isEnabled"] = PlatformInfo.isMac()

native {
    val isWindows = PlatformInfo.isWindows()
    val obj = if (isWindows) "obj" else "o"
    val lib = if (isWindows) "lib" else "a"
    suffixes {
        (".cpp" to ".$obj") {
            tool(*platformManager.hostPlatform.clang.clangCXX("").toTypedArray())
            when (org.jetbrains.kotlin.konan.target.HostManager.host.family) {
                LINUX -> {
                    flags("--std=c++11", "-g", "-Isrc/main/include",
                          "-I${project.findProperty("llvmDir")}/include", "-fPIC",
                          "-DLLVM_DISABLE_ABI_BREAKING_CHECKS_ENFORCING=1",
                           "-c", "-o", ruleOut(), ruleInFirst())
                }
                MINGW -> {
                    flags("--std=c++11", "-g", "-Isrc/main/include",
                          "-I${project.findProperty("llvmDir")}/include",
                           "-c", "-o", ruleOut(), ruleInFirst())
                }
                OSX -> {
                    flags("--std=c++11", "-g",
                          "-Isrc/main/include",
                          "-DLLVM_DISABLE_ABI_BREAKING_CHECKS_ENFORCING=1",
                          "-I${project.findProperty("llvmDir")}/include", "-fPIC", "-c", "-o", ruleOut(), ruleInFirst())
                }
            }
        }
    }
    sourceSet {
        "main" {
            dir("src/main/cpp")
        }
    }
    val objSet = sourceSets["main"]!!.transform(".cpp" to ".$obj")
    target("libclangext.$lib", objSet) {
        tool(*platformManager.hostPlatform.clang.llvmAr("").toTypedArray())
        flags("-qv", ruleOut(), *ruleInAll())
    }
}
/*
model {
    tasks.assemble {
        UtilsKt.configureNativePluginTask(project, delegate)
    }
    tasks.compileClangextStaticLibraryClangextCpp {
        println(">compileClangextStaticLibraryClangextCpp: this:${this.class}, delegate: ${delegate.class} owner: ${owner.class}")
        UtilsKt.configureNativePluginTask(project, delegate)
    }
    //"name" {
    //    println(">model: this:${this.class}, delegate: ${delegate.class} owner: ${owner.class}")
    //}
    components {
        clangext(NativeLibrarySpec) {
            println(">components: this:${this.class}, delegate: ${delegate.class} owner: ${owner.class}")
            sources {
                cpp {
                    source.srcDirs "src/main/cpp"
                    exportedHeaders.srcDirs "src/main/include"
                }
            }
            binaries.withType(StaticLibraryBinarySpec) { binary ->
                if (!project.parent.convention.plugins.platformInfo.isWindows())
                    cppCompiler.args "-fPIC"
                cppCompiler.args "--std=c++11", "-g", "-I${llvmDir}/include"
                if (isEnabled) {
                    cppCompiler.args '-DLIBCLANGEXT_ENABLE=1'
                }
            }
            binaries.withType(SharedLibraryBinarySpec) { binary ->
                buildable = false
            }
        }
    }

  toolChains {
      println(">toolchain: this:${this.class}, delegate: ${delegate.class} owner: ${owner.class}")
      //UtilsKt.configureToolchain(project, delegate)
      konanClang(Clang) {
          println(">Clang: this:${this.class}, delegate: ${delegate.class} owner: ${owner.class}")
          path UtilsKt.getClangPath(project)
          eachPlatform {
              cppCompiler.withArguments(ClangArgs.&filterGradleNativeSoftwareFlags)
          }
      }
  }
}
 */
