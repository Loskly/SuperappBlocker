package com.ecosentinel.appblocker.tracker

import android.content.Context
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.data.entity.AppGroupMemberEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object AppGroupHelper {

    private data class Cache(
        val groupNames: Map<String, String>,
        val membersByGroup: Map<String, Set<String>>,
        val groupsByPackage: Map<String, Set<String>>
    )

    @Volatile
    private var cache: Cache? = null

    private val cacheMutex = Mutex()

    suspend fun refreshCache(context: Context) = cacheMutex.withLock {
        val dao = AppDatabase.getInstance(context.applicationContext).appGroupDao()
        val groups = dao.getAll()
        val groupNames = groups.associate { it.id to it.name }
        val membersByGroup = linkedMapOf<String, Set<String>>()
        val groupsByPackage = linkedMapOf<String, MutableSet<String>>()

        for (group in groups) {
            val members = dao.getMemberPackages(group.id).toSet()
            membersByGroup[group.id] = members
            for (packageName in members) {
                groupsByPackage.getOrPut(packageName) { mutableSetOf() }.add(group.id)
            }
        }

        cache = Cache(
            groupNames = groupNames,
            membersByGroup = membersByGroup,
            groupsByPackage = groupsByPackage.mapValues { it.value.toSet() }
        )
    }

    suspend fun ensureCacheLoaded(context: Context) {
        if (cache == null) {
            refreshCache(context)
        }
    }

    fun invalidateCache() {
        cache = null
    }

    fun displayNameForGroupId(groupId: String): String {
        return cache?.groupNames?.get(groupId) ?: groupId
    }

    fun memberPackages(groupId: String): Set<String> {
        return cache?.membersByGroup?.get(groupId).orEmpty()
    }

    fun belongsToGroup(packageName: String, groupId: String): Boolean {
        return cache?.membersByGroup?.get(groupId)?.contains(packageName) == true
    }

    fun groupIdsForPackage(packageName: String): Set<String> {
        return cache?.groupsByPackage?.get(packageName).orEmpty()
    }

    fun aggregateUsageForGroup(
        memberPackages: Set<String>,
        usageByPackage: Map<String, Long>,
        excludePackage: String? = null
    ): Long {
        return memberPackages.sumOf { packageName ->
            if (packageName == excludePackage) {
                0L
            } else {
                usageByPackage[packageName] ?: 0L
            }
        }
    }

    fun aggregateUsageForGroup(
        usageByPackage: Map<String, Long>,
        groupId: String,
        excludePackage: String? = null
    ): Long {
        return aggregateUsageForGroup(memberPackages(groupId), usageByPackage, excludePackage)
    }

    suspend fun saveGroupMembers(context: Context, groupId: String, packages: Set<String>) {
        withContext(Dispatchers.IO) {
            val dao = AppDatabase.getInstance(context.applicationContext).appGroupDao()
            dao.deleteMembersForGroup(groupId)
            if (packages.isNotEmpty()) {
                dao.upsertMembers(
                    packages.map { packageName ->
                        AppGroupMemberEntity(groupId = groupId, packageName = packageName)
                    }
                )
            }
        }
        invalidateCache()
        refreshCache(context)
    }

    suspend fun onGroupsChanged(context: Context) {
        invalidateCache()
        refreshCache(context)
    }
}
