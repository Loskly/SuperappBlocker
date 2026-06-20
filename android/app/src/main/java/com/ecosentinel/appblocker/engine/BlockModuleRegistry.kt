package com.ecosentinel.appblocker.engine

import android.content.Context
import com.ecosentinel.appblocker.modules.adult.AdultContentModule
import com.ecosentinel.appblocker.modules.applimit.AppLimitModule
import com.ecosentinel.appblocker.modules.website.WebsiteBlockModule
import com.ecosentinel.appblocker.modules.focus.FocusModule
import com.ecosentinel.appblocker.modules.friendpwd.FriendPasswordModule
import com.ecosentinel.appblocker.modules.inapp.InAppFeatureModule
import com.ecosentinel.appblocker.modules.taskgate.TaskGateModule

class BlockModuleRegistry(context: Context) {

    private val modules: List<BlockModule> = listOf(
        FocusModule(context),
        AppLimitModule(context),
        WebsiteBlockModule(context),
        InAppFeatureModule(context),
        AdultContentModule(context),
        TaskGateModule(),
        FriendPasswordModule()
    )

    fun enabledModules(): List<BlockModule> = modules.filter { it.enabled }

    fun module(type: BlockModuleType): BlockModule? = modules.firstOrNull { it.type == type }
}
