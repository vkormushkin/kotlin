/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.incremental

import org.jetbrains.kotlin.incremental.JarSnapshot.Companion.readJarSnapshot
import org.jetbrains.kotlin.incremental.JavaClassProtoMapValueExternalizer.readBytesWithSize
import org.jetbrains.kotlin.incremental.JavaClassProtoMapValueExternalizer.writeBytesWithSize
import org.jetbrains.kotlin.metadata.ProtoBuf
import org.jetbrains.kotlin.metadata.deserialization.NameResolverImpl
import org.jetbrains.kotlin.metadata.jvm.JvmProtoBuf
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmNameResolver
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.protobuf.CodedOutputStream
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.serialization.DescriptorSerializer
import org.jetbrains.kotlin.serialization.StringTableImpl
import java.io.*

class JarSnapshot(val protos: MutableMap<FqName, ProtoData>) {

    companion object {
        fun ObjectInputStream.readJarSnapshot(): JarSnapshot {
            // Format:
            // numRecords: Int
            // record {
            //   fqName
            //   isClassProtoData
            //   proto via JavaClassProtoMapValueExternalizer
            // }
            val size = readInt()
            val mutableMap = hashMapOf<FqName, ProtoData>()
            repeat(size) {
                val fqNameString = readUTF()
                val isClassProtoData = readBoolean()
                if (isClassProtoData) {
                    val fqName = FqName(fqNameString)
                    val serializableData = JavaClassProtoMapValueExternalizer.read(this)
                    mutableMap[fqName] = serializableData.toProtoData()
                } else {
                    //TODO support PackageProtoData
                }
            }
            return JarSnapshot(mutableMap)
        }

        fun ObjectOutputStream.writeJarSnapshot(jarSnapshot: JarSnapshot) {
            //TODO temp solution while packageProto is not fully support
            writeInt(jarSnapshot.protos.size)
            for (entry in jarSnapshot.protos) {
                writeUTF(entry.key.asString())
                val protoData = entry.value
                when (protoData) {
                    is ClassProtoData -> {
                        writeBoolean(true) //TODO until PackageProto doesn't work
                        val nameResolver = protoData.nameResolver

                            when (nameResolver) {
                                is NameResolverImpl -> {
                                    JavaClassProtoMapValueExternalizer.save(
                                        this,
                                        SerializedJavaClass(protoData.proto, nameResolver.strings, nameResolver.qualifiedNames)
                                    )
                                }
                                is JvmNameResolver -> {
                                    val stringTable = StringTableImpl()
                                    //TODO dirty hack
                                    repeat(nameResolver.strings.size) {
                                        val string = nameResolver.getString(it)
                                        stringTable.getStringIndex(string)
//                                        stringTable.getQualifiedClassNameIndex(string, nameResolver.isLocalClassName(it))
                                    }
//                                    repeat(nameResolver.strings.size) {
//                                        val string = nameResolver.getString(it)
////                                        intern qualified class name caused intern into strings and ruin the order
//                                        stringTable.getQualifiedClassNameIndex(string, nameResolver.isLocalClassName(it))
//                                    }

                                    val fqNameIndex = protoData.proto.fqName
                                    val string = nameResolver.getString(fqNameIndex)
                                    val newFqName = stringTable.getQualifiedClassNameIndex(string, nameResolver.isLocalClassName(fqNameIndex))
                                    val (stringsTable, qualifiedNameTable) = stringTable.buildProto()

                                    //TODO cause qualified indexes was updated protoData should be updated as well
                                    val updatedProtoData = protoData.proto.toBuilder().setFqName(newFqName).build()
                                    JavaClassProtoMapValueExternalizer.save(
                                        this,
                                        SerializedJavaClass(updatedProtoData, stringsTable, qualifiedNameTable)
                                    )
                                }
                                else -> throw IllegalStateException("Can't store name resolver")
                            }
                    }
                    is PackagePartProtoData -> {
                        writeBoolean(false)
                        //TODO serialize packageProtoData
//                         protoData.proto.writeTo(codedOutputStream)
                    }
                }
            }
        }

        fun write(buildInfo: JarSnapshot, file: File) {
            ObjectOutputStream(FileOutputStream(file)).use {
                it.writeJarSnapshot(buildInfo)
            }
        }

        fun read(file: File): JarSnapshot {
            ObjectInputStream(FileInputStream(file)).use {
                return it.readJarSnapshot()
            }
        }
    }
}