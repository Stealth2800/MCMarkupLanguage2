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

import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import java.lang.IllegalArgumentException
import java.util.ArrayList
import java.util.HashMap

/**
 * An instance of an MCML2 parser.
 *
 * Handles the conversion between a string and an array of [BaseComponent]s.
 */
class McmlParser(vararg serializers: JsonSerializer) {

    private companion object {

        const val CLICK_COMMAND = "!"
        const val CLICK_SUGGEST_COMMAND = "?"
        const val CLICK_URL = ">"
        const val CLICK_FILE = "/"
        const val CLICK_CHANGE_PAGE = "#"

        const val HOVER_TEXT = "T"
        const val HOVER_ACHIEVEMENT = "A"
        const val HOVER_ITEM = "I"
        const val HOVER_ENTITY = "E"

        val COLOR_PATTERN = Regex("${ChatColor.COLOR_CHAR}[a-f0-9klmnor]")
        val EVENT_PATTERN = Regex("""\[((?:\\.|[^]\\])+?)\]\((?:([!?>\/#])"((?:\\.|[^"\\])*)")? ?(?:([AEIT])?"((?:\\.|[^"\\])*)")?\)""")

    }

    private val serializers: MutableMap<Class<*>, JsonSerializer>?

    init {
        this.serializers = if (serializers.isEmpty()) {
            null
        } else {
            HashMap<Class<*>, JsonSerializer>(serializers.sumBy { it.classes.size }).apply {
                serializers.forEach { s -> s.classes.forEach { c -> put(c, s) } }
            }
        }
    }

    /**
     * Convenience method. Converts the input replacements array into a map of index -> object (starting at 1).
     */
    fun parse(raw: String, replacements: Array<out Any?>): Array<out BaseComponent> {
        return parse(raw, HashMap<String, Any?>().apply {
            for ((i, obj) in replacements.withIndex()) {
                put("{${i + 1}}", obj)
            }
        })
    }

    /**
     * Parses a string into an array of [BaseComponent]s.
     *
     * @param raw The string to parse.
     * @param replacements Replacements for the string (text, items, etc.).
     *                     Items should be in JSON, which can be done easily for Bukkit via [BukkitJsonSerializer].
     */
    fun parse(raw: String, replacements: Map<String, Any?>? = null): Array<out BaseComponent> {
        @Suppress("NAME_SHADOWING")
        var raw = raw
        val rawComponents = ArrayList<BaseComponent>()

        /* Set replacements */
        if (replacements != null) {
            for ((k, v) in replacements) {
                raw = raw.replace(k, if (v == null) {
                    "null"
                } else {
                    (serializers?.get(v.javaClass)?.serialize(v) ?: v.toString()).replace("\"", "\\\"")
                })
            }
        }

        /* Parse Events */
        var prevRange = IntRange(-1, -1)
        val matches = EVENT_PATTERN.findAll(raw)
        for (match in matches) {
            /*
             * Groups
             * 1: Text
             * 2: Event type (empty if no event is specified)
             * 3: Event value (empty if no event is specified)
             * 4: Hover type (empty defaults to text)
             * 5: Hover text
             */

            // Check for unhandled text
            if (match.range.first != prevRange.endInclusive + 1) {
                parseColors(raw.substring(prevRange.endInclusive + 1, match.range.first), rawComponents)
            }

            // Handle text
            val prevSize = rawComponents.size
            parseColors(match.groupValues[1], rawComponents)

            // Handle click event
            val clickType = match.groupValues[2]
            val clickValue = match.groupValues[3].replace("\\\"", "\"")
            val click = if (clickType.isNotEmpty()) {
                ClickEvent(when (clickType) {
                    CLICK_COMMAND -> ClickEvent.Action.RUN_COMMAND
                    CLICK_SUGGEST_COMMAND -> ClickEvent.Action.SUGGEST_COMMAND
                    CLICK_URL -> ClickEvent.Action.OPEN_URL
                    CLICK_FILE -> ClickEvent.Action.OPEN_FILE
                    CLICK_CHANGE_PAGE -> ClickEvent.Action.CHANGE_PAGE
                    else -> throw IllegalArgumentException("Invalid click event type '$clickType'")
                }, clickValue)
            } else {
                null
            }

            // Handle hover event
            val hoverType = match.groupValues[4]
            val hoverValue = match.groupValues[5].replace("\\\"", "\"") // Should be JSON
            val hover = if (hoverValue.isNotEmpty() && (hoverType.isEmpty() || hoverType == HOVER_TEXT)) {
                // Text
                HoverEvent(HoverEvent.Action.SHOW_TEXT, ArrayList<BaseComponent>(1).apply {
                    parseColors(hoverValue, this)
                }.toTypedArray())
            } else if (hoverValue.isNotEmpty()) {
                HoverEvent(when (hoverType) {
                    HOVER_ITEM -> HoverEvent.Action.SHOW_ITEM
                    HOVER_ACHIEVEMENT -> HoverEvent.Action.SHOW_ACHIEVEMENT
                    HOVER_ENTITY -> HoverEvent.Action.SHOW_ENTITY
                    else -> throw IllegalArgumentException("Invalid hover event type '$hoverType'")
                }, arrayOf(TextComponent(hoverValue)))
            } else {
                null
            }

            // Set events
            if (prevSize != rawComponents.size) {
                for (i in prevSize until rawComponents.size) {
                    rawComponents[i].clickEvent = click
                    rawComponents[i].hoverEvent = hover
                }
            }

            prevRange = match.range
        } // for (match in matches)

        /* Handle final colors */
        if (prevRange.endInclusive + 1 != raw.length) {
            parseColors(raw.substring(prevRange.endInclusive + 1), rawComponents)
        }

        return rawComponents.toTypedArray()
    }

    private fun parseColors(raw: String, dest: MutableList<BaseComponent>) {
        var curComponent: TextComponent = TextComponent()
        curComponent.color = ChatColor.WHITE

        // Helper function
        fun addAndNext(resetColor: Boolean) {
            val new = TextComponent()

            // Only add component if it actually contains text
            if (!curComponent.text.isNullOrEmpty()) {
                dest.add(curComponent)
            }

            if (!resetColor) {
                new.color = curComponent.colorRaw

                // Can't use property access syntax since isBold, isItalic, etc. expect non-nullable value
                // new.isBold = curComponent.isBoldRaw
                new.setBold(curComponent.isBoldRaw)
                new.setItalic(curComponent.isItalicRaw)
                new.setObfuscated(curComponent.isObfuscatedRaw)
                new.setUnderlined(curComponent.isUnderlinedRaw)
                new.setStrikethrough(curComponent.isStrikethroughRaw)
            }

            curComponent = new
        }

        var prevRange = IntRange(-1, -1)

        val matches = COLOR_PATTERN.findAll(raw)
        for (match in matches) {
            if (match.range.start != prevRange.endInclusive + 1) {
                // Text found
                curComponent.text = raw.substring(prevRange.endInclusive + 1, match.range.start)
                addAndNext(false)
            }

            // No text found between previous and current, add format or color to curComponent
            val found = ChatColor.getByChar(match.groupValues[0][1])

            when (found) {
                // RESET -> update text component and don't carry over color
                ChatColor.RESET -> addAndNext(true)

                // Formats
                ChatColor.BOLD -> curComponent.isBold = true
                ChatColor.ITALIC -> curComponent.isItalic = true
                ChatColor.UNDERLINE -> curComponent.isUnderlined = true
                ChatColor.MAGIC -> curComponent.isObfuscated = true
                ChatColor.STRIKETHROUGH -> curComponent.isStrikethrough = true

                // Color
                else -> {
                    if (curComponent.hasFormatting()) {
                        // Reset formatting
                        addAndNext(true)
                    }
                    curComponent.color = found
                }
            }

            // Update previous range
            prevRange = match.range
        }

        // Handle final text
        if (prevRange.endInclusive + 1 != raw.length) {
            if (!curComponent.text.isNullOrEmpty()) {
                addAndNext(false)
            }
            curComponent.text = raw.substring(prevRange.endInclusive + 1)
        }

        if (!curComponent.text.isNullOrEmpty()) {
            dest.add(curComponent)
        }
    }

}
