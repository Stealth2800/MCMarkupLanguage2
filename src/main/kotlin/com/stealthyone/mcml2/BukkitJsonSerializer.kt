/**
 * Copyright 2017 Stealth2800 <http://stealthyone.com/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.stealthyone.mcml2

import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import java.lang.Exception
import java.lang.reflect.Method

/**
 * Handles the conversion of a Bukkit ItemStack to JSON via reflection.
 */
object BukkitItemJsonSerializer : JsonSerializer(ItemStack::class.java) {

    private const val FAIL_MESSAGE = "(failed to convert item to JSON)"

    /**
     * Serializes an ItemStack to JSON via reflection.
     *
     * This method will not throw an exception on failure. Rather, it will return [FAIL_MESSAGE].
     */
    override fun serialize(obj: Any): String {
        val item = obj as ItemStack

        // NMS ItemStack
        val nmsItem = cb_CraftItemStack_asNMSCopy?.invoke(null, item)
            ?: return FAIL_MESSAGE

        // Empty NBTTagCompound
        val compound = nms_NBTTagCompound?.newInstance()
            ?: return FAIL_MESSAGE

        // Save ItemStack to compound and call toString to convert to JSON
        return nms_ItemStack_save?.invoke(nmsItem, compound)?.toString()
            ?: FAIL_MESSAGE
    }

    //region Reflection Utilities

    private fun getBukkitVersion(): String {
        return Bukkit.getServer().javaClass.canonicalName.split(".")[3]
    }

    private fun getNMSClassName(name: String): String {
        return "net.minecraft.server.${getBukkitVersion()}.$name"
    }

    private fun getNMSClass(name: String): Class<*> = Class.forName(getNMSClassName(name))

    private fun getCBClassName(name: String): String {
        return "org.bukkit.craftbukkit.${getBukkitVersion()}.$name"
    }

    private fun getCBClass(name: String): Class<*> = Class.forName(getCBClassName(name))

    //endregion

    //region Reflection Classes and Methods

    private val cb_CraftItemStack_asNMSCopy: Method? by lazy {
        try {
            getCBClass("inventory.CraftItemStack").getMethod("asNMSCopy", ItemStack::class.java)
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }
    }

    private val nms_NBTTagCompound: Class<*>? by lazy {
        try {
            getNMSClass("NBTTagCompound")
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }
    }

    private val nms_ItemStack: Class<*>? by lazy {
        try {
            getNMSClass("ItemStack")
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }
    }

    private val nms_ItemStack_save: Method? by lazy {
        try {
            nms_ItemStack?.getMethod("save", nms_NBTTagCompound)
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }
    }

    //endregion

}
