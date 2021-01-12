/*
 * Copyright 2010-2019 JetBrains s.r.o.
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
import org.jetbrains.kotlin.*
import org.jetbrains.kotlin.konan.target.ClangArgs
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
  `native`
}

val llvmDir = project.findProperty("llvmDir")

native {
  val obj = if (HostManager.hostIsMingw) "obj" else "o"
  val lib = if (HostManager.hostIsMingw) "lib" else "a"
  val host = rootProject.project(":kotlin-native").extra["hostName"]
  val hostLibffiDir = rootProject.project(":kotlin-native").extra["${host}LibffiDir"]
  suffixes {
    (".cpp" to ".$obj") {
      tool(*platformManager.hostPlatform.clang.clangCXX("").toTypedArray())
      when (org.jetbrains.kotlin.konan.target.HostManager.host.family) {
        org.jetbrains.kotlin.konan.target.Family.MINGW -> {
          flags("-std=c++14", "-I${llvmDir}/include", "-I${projectDir}/src/main/include",  "-c", "-o", ruleOut(), ruleInFirst())
        }
        org.jetbrains.kotlin.konan.target.Family.LINUX -> {
          flags("-std=c++14", "-I${llvmDir}/include", "-I${projectDir}/src/main/include", "-fPIC", "-c", "-o", ruleOut(), ruleInFirst())
        }
        org.jetbrains.kotlin.konan.target.Family.OSX -> {
          flags("-std=c++14", "-I${llvmDir}/include", "-I${projectDir}/src/main/include",
                "-fPIC", "-c", "-o", ruleOut(), ruleInFirst())
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

  target("libdebugInfo.$lib", objSet) {
    tool(*platformManager.hostPlatform.clang.llvmAr("").toTypedArray())
    flags("-qv", ruleOut(), *ruleInAll())
  }
}
