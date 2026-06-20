package com.ecosentinel.appblocker.engine

object PolicyRuleIds {

    fun forApp(packageName: String, blockMode: BlockMode): String {
        return "app:$packageName:${blockMode.name}"
    }

    fun forCategory(categoryId: String, blockMode: BlockMode): String {
        return "category:$categoryId:${blockMode.name}"
    }

    fun forCustomGroup(groupId: String, blockMode: BlockMode): String {
        return "group:$groupId:${blockMode.name}"
    }

    fun forWebsite(domain: String, blockMode: BlockMode): String {
        return "site:$domain:${blockMode.name}"
    }
}
