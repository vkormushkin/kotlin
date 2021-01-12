/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.incremental

import org.jetbrains.kotlin.incremental.ChangesCollector.Companion.getNonPrivateMemberNames
import org.jetbrains.kotlin.incremental.ChangesCollector.Companion.getNonPrivateNames
import org.jetbrains.kotlin.metadata.ProtoBuf
import org.jetbrains.kotlin.metadata.ProtoBuf.Visibility
import org.jetbrains.kotlin.metadata.ProtoBuf.Visibility.PRIVATE
import org.jetbrains.kotlin.metadata.deserialization.Flags
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.sam.SAM_LOOKUP_NAME
import org.jetbrains.kotlin.serialization.deserialization.getClassId

//TODO Should be in gradle daemon so move it. Only for test here
class JarSnapshotDiffService() {

    companion object {
        //Store list of changed lookups
        private val diffCache: MutableMap<Pair<JarSnapshot, JarSnapshot>, DirtyData> = mutableMapOf()

        //TODO for test only
        fun compareJarsInternal(
            snapshotJar: JarSnapshot, newJar: JarSnapshot
        ) = diffCache.computeIfAbsent(Pair(snapshotJar, newJar)) { (snapshot, actual) ->
            val fqNames = mutableListOf<FqName>()
            val symbols = mutableListOf<LookupSymbol>()

            for ((fqName, protoData) in snapshot.protos) {
                val newProtoData = actual.protos[fqName]
                if (newProtoData == null) {
                    addProtoInfo(protoData, fqNames, fqName, symbols)
                } else {
                    if (protoData is ClassProtoData && newProtoData is ClassProtoData) {
                        ProtoCompareGenerated(
                            protoData.nameResolver, newProtoData.nameResolver,
                            protoData.proto.typeTable, newProtoData.proto.typeTable
                        )
                        val diff = DifferenceCalculatorForClass(protoData, newProtoData).difference()

                        if (diff.isClassAffected) {
                            //TODO get cache to mark dirty all subtypes if subclass affected
//                            val fqNames = if (!diff.areSubclassesAffected) listOf(fqName) else withSubtypes(fqName, caches)
                            fqNames.add(fqName)
                            assert(!fqName.isRoot) { "$fqName is root" }

                            val scope = fqName.parent().asString()
                            val name = fqName.shortName().identifier
                            symbols.add(LookupSymbol(name, scope))
                        }
                        for (member in diff.changedMembersNames) {
                            //TODO mark dirty symbols for subclasses
                                symbols.add(LookupSymbol(member, fqName.asString()))
                            symbols.add(LookupSymbol(SAM_LOOKUP_NAME.asString(), fqName.asString()))
                        }

                    } else if (protoData is PackagePartProtoData && newProtoData is PackagePartProtoData) {
                        //TODO update it
                    } else {
                        //TODO is it a valid case
                        throw IllegalStateException("package proto and class proto have the same fqName: $fqName")
                    }
                }
            }
//                fqNames.addAll(snapshot.protos.keys.removeAll(actual.protos.keys))

            DirtyData(symbols, fqNames)
        }

        //TODO change to return type
        private fun addProtoInfo(
            protoData: ProtoData,
            fqNames: MutableList<FqName>,
            fqName: FqName,
            symbols: MutableList<LookupSymbol>
        ) {
            when (protoData) {
                is ClassProtoData -> {
                    fqNames.add(fqName)
                    symbols.addAll(protoData.getNonPrivateMemberNames().map { LookupSymbol(it, fqName.asString()) })
                }
                is PackagePartProtoData -> {
                    symbols.addAll(
                        protoData.proto.functionOrBuilderList.filterNot { Flags.VISIBILITY.get(it.flags) == PRIVATE }
                            .map { LookupSymbol(protoData.nameResolver.getString(it.name), fqName.asString()) }.toSet()
                    )

                }
            }
        }

    }
}