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
import java.util.HashMap
import java.util.LinkedList

/**
 * An instance of an MCML2 parser.
 *
 * Handles the conversion between a string and an array of [BaseComponent]s.
 */
class McmlParser(vararg serializers: JsonSerializer) {

    private companion object {

        const val CLICK_COMMAND = '!'
        const val CLICK_SUGGEST_COMMAND = '?'
        const val CLICK_URL = '>'
        const val CLICK_FILE = '/'
        const val CLICK_CHANGE_PAGE = '#'

        const val HOVER_TEXT = 'T'
        const val HOVER_ACHIEVEMENT = 'A'
        const val HOVER_ITEM = 'I'
        const val HOVER_ENTITY = 'E'

    }

    private val serializers: MutableMap<Class<*>, JsonSerializer>?

    var replacementIndexOffset = 1

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
     * Array [Hello, World] will become map [{1} => Hello, {2} => World], so a string like "{1} {2}" is expected.
     *
     * The beginning index can be changed via a parser instance's [replacementIndexOffset] property.
     */
    fun parse(raw: String, replacements: Array<out Any?>): Array<out BaseComponent> {
        return parse(raw, HashMap<String, Any?>().apply {
            for ((i, obj) in replacements.withIndex()) {
                put("{${i + replacementIndexOffset}}", obj)
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
        val builder = StringBuilder(raw)

        val components = LinkedList<BaseComponent>()

        // Set replacements and keep track of indices
        val replacementsToApply = ArrayList<Pair<IntRange, String>>() // Range of key -> replacement
        replacements?.forEach { k, v ->
            var lastIndex = 0
            // Lazy for expensive serializers (which shouldn't really be expensive, making this lazy call
            // unnecessary?)
            val replacement by lazy {
                v as? String ?: (v?.let { serializers?.get(v::class.java)?.serialize(v) } ?: "null")
            }

            /*
             * While we're performing replacements, we store the replaced indices (adjusted) so we don't process
             * them specially later on. This prevents injection of colors, hover/click events, etc.
             */

            // Get replacements
            while (true) {
                val start = builder.indexOf(k, lastIndex)
                if (start == -1) return@forEach

                val range = start .. start + k.length - 1
                replacementsToApply.add(range to replacement)
                lastIndex = range.last
            }
        }

        // If there are replacements to apply, do it
        val replacedIndices = LinkedList<IntRange>()
        if (replacementsToApply.isNotEmpty()) {
            replacementsToApply.sortBy { it.first.first }

            var offset = 0
            for ((range, replacement) in replacementsToApply) {
                val fixedRange = range.first + offset .. range.endInclusive + offset
                builder.replace(fixedRange.first, fixedRange.endInclusive + 1, replacement)
                replacedIndices.add(fixedRange.first .. fixedRange.endInclusive + replacement.length)
                offset += replacement.length - (range.last - range.first + 1)
            }
        }

        // Parse colors and events
        var curComponent = TextComponent()
        var partBuilder = StringBuilder()
        var isEscaped = false
        var isColorCode = false
        var isReplacement = false

        var isGroup = false
        var isExpectingSecondGroupPart = false
        var isGroupSecondPart = false
        var isGroupQuote = false
        var activeEvent: Char? = null
        var eventComponentIndex = -1

        /**
         * Advances the current component.
         * This should only occur when:
         *   - A color code is reached
         *   - A reset code is reached
         *   - An event shows up
         *
         * @param reset If true, resets the colors and formatting.
         *              If false, colors and formatting will be carried over to the next component.
         */
        fun advanceComponent(reset: Boolean, createNew: Boolean = true) {
            // Only add component if it has text or there is text to set
            if (curComponent.text.isNotEmpty()) {
                components.add(curComponent)
            } else if (partBuilder.isNotEmpty()) {
                components.add(curComponent.apply { text = partBuilder.toString() })
                partBuilder = StringBuilder()
            }

            if (!createNew) return

            curComponent = TextComponent().apply {
                if (!reset) {
                    color = curComponent.colorRaw

                    // Can't use property access syntax since isBold, isItalic, etc. expect non-nullable value
                    // new.isBold = curComponent.isBoldRaw
                    setBold(curComponent.isBoldRaw)
                    setItalic(curComponent.isItalicRaw)
                    setObfuscated(curComponent.isObfuscatedRaw)
                    setUnderlined(curComponent.isUnderlinedRaw)
                    setStrikethrough(curComponent.isStrikethroughRaw)
                }
            }
        }

        /**
         * Processes the current event and adds it to the display text element(s) of the group.
         */
        fun processEvent() {
            if (activeEvent == null) return

            fun executeOnIntermediateComponents(f: BaseComponent.() -> Unit) {
                if (eventComponentIndex != components.size) {
                    // Some components before curComponent were added, set the event on them as well
                    for (i in eventComponentIndex until components.size) {
                        components[i].f()
                    }
                }
                curComponent.f()
                partBuilder = StringBuilder()
                activeEvent = null
                isGroupQuote = false
            }

            // Click event
            when (activeEvent) {
                CLICK_COMMAND -> ClickEvent.Action.RUN_COMMAND
                CLICK_SUGGEST_COMMAND -> ClickEvent.Action.SUGGEST_COMMAND
                CLICK_URL -> ClickEvent.Action.OPEN_URL
                CLICK_FILE -> ClickEvent.Action.OPEN_FILE
                CLICK_CHANGE_PAGE -> ClickEvent.Action.CHANGE_PAGE
                else -> null
            }?.apply {
                val event = ClickEvent(this, partBuilder.toString())
                return executeOnIntermediateComponents {
                    clickEvent = event
                }
            }

            // Hover event
            when (activeEvent) {
                HOVER_ACHIEVEMENT -> HoverEvent.Action.SHOW_ACHIEVEMENT
                HOVER_ITEM -> HoverEvent.Action.SHOW_ITEM
                HOVER_ENTITY -> HoverEvent.Action.SHOW_ENTITY
                HOVER_TEXT -> HoverEvent.Action.SHOW_TEXT
                else -> null
            }?.apply {
                val event = if (this == HoverEvent.Action.SHOW_TEXT) {
                    // This is where colors are processed for the hover event
                    HoverEvent(this, TextComponent.fromLegacyText(partBuilder.toString()))
                } else {
                    HoverEvent(this, arrayOf(TextComponent(partBuilder.toString())))
                }
                return executeOnIntermediateComponents {
                    hoverEvent = event
                }
            }
        }

        charLoop@for ((i, char) in builder.withIndex()) {
            if (replacedIndices.isNotEmpty()) {
                val cur = replacedIndices.first()
                if (cur.contains(i)) {
                    // The current character was from a replacement
                    // Simply add it to the current text and avoid any special processing

                    if (i == cur.last) {
                        // We've reached the final character in the replacement text, dispose of this replacement range
                        replacedIndices.removeFirst()
                        isReplacement = false
                    } else {
                        isReplacement = true
                    }
                }
            }

            if (!isEscaped) {
                // Last character wasn't an escape, handle special characters here
                when (char) {
                    // Set escape flag and skip character
                    '\\' -> {
                        isEscaped = true
                        continue@charLoop
                    }

                    // Color code
                    ChatColor.COLOR_CHAR -> {
                        if (!isGroupSecondPart) {
                            // We don't actually process colors here for the second part of a group
                            isColorCode = true
                            continue@charLoop
                        }
                    }

                    // Beginning of an event, only if we're not in a replacement or other event right now
                    '[' -> {
                        if (!isReplacement && !isGroup) {
                            var innerEscape = false
                            var isBalanced = false
                            closeFinder@for (j in i + 1 until builder.length - 1) {
                                if (innerEscape) {
                                    innerEscape = false
                                    continue
                                }

                                when (builder[j]) {
                                    '\\' -> {
                                        innerEscape = true
                                    }

                                    ']' -> {
                                        if (builder[j + 1] == '(') {
                                            isBalanced = true
                                        }
                                        break@closeFinder
                                    }
                                }
                            }

                            if (isBalanced) {
                                isGroup = true
                                advanceComponent(false) // Advance component, but don't reset colors or formatting
                                eventComponentIndex = components.size
                                continue@charLoop
                            }
                        }
                    }

                    // End of the text part of an event, if we're in an event and not in a replacement
                    ']' -> {
                        if (!isReplacement && isGroup) {
                            isExpectingSecondGroupPart = true
                            continue@charLoop
                        }
                    }

                    // Beginning of the inner part of an event, if we're expecting it and not in a replacement
                    '(' -> {
                        if (!isReplacement && isExpectingSecondGroupPart) {
                            isExpectingSecondGroupPart = false
                            isGroupSecondPart = true

                            // Move text to component, since we're done adding to it
                            curComponent.text = partBuilder.toString()
                            partBuilder = StringBuilder()
                            continue@charLoop
                        }
                    }

                    // End of the inner part of an event, if we're currently in one
                    ')' -> {
                        if (!isReplacement && isGroupSecondPart) {
                            isGroup = false
                            isGroupSecondPart = false
                            processEvent() // There may be a pending event waiting to be added
                            advanceComponent(true) // Event just finished, advance component
                            continue@charLoop
                        }
                    }
                }

                if (isGroupSecondPart) {
                    // If we're in the second part of a group, handle special characters here
                    when (char) {
                        // Beginning (or end) of quoted section
                        '"' -> {
                            isGroupQuote = !isGroupQuote

                            if (!isGroupQuote) {
                                // We were just in a quote, handle text appropriately
                                processEvent()
                            } else if (activeEvent == null) {
                                // If we're entering a quoted part and no activeEvent is set, then assume HOVER_TEXT
                                activeEvent = HOVER_TEXT
                            }
                            continue@charLoop
                        }

                        // Any event - set activeEvent if not already set
                        CLICK_COMMAND,
                        CLICK_SUGGEST_COMMAND,
                        CLICK_URL,
                        CLICK_FILE,
                        CLICK_CHANGE_PAGE,
                        HOVER_ACHIEVEMENT,
                        HOVER_ITEM,
                        HOVER_ENTITY,
                        HOVER_TEXT -> {
                            if (activeEvent == null) {
                                activeEvent = char
                                continue@charLoop
                            }
                        }

                        // Anything else should be skipped unless quoted
                        else -> {
                            if (!isGroupQuote) {
                                continue@charLoop
                            }
                        }
                    }
                }
            }

            // Escape was consumed
            isEscaped = false

            // If the beginning of an event just took place, but the next immediate character wasn't a (,
            // then turn off the expectation flag.
            isExpectingSecondGroupPart = false

            if (isColorCode) {
                // Consume color code
                isColorCode = false
                val chatColor = ChatColor.getByChar(char)

                if (partBuilder.isNotEmpty() && chatColor >= ChatColor.MAGIC && chatColor <= ChatColor.ITALIC) {
                    // If the chatColor is a format and there's already text, advance to the next component
                    advanceComponent(false)
                }

                if (chatColor != null) {
                    when (chatColor) {
                        // Color or reset, advance part and update color where applicable
                        ChatColor.RESET,
                        in ChatColor.BLACK..ChatColor.WHITE -> {
                            advanceComponent(true)
                            if (chatColor != ChatColor.RESET) curComponent.color = chatColor
                        }

                        // Formatting codes
                        ChatColor.MAGIC -> curComponent.isObfuscated = true
                        ChatColor.BOLD -> curComponent.isBold = true
                        ChatColor.STRIKETHROUGH -> curComponent.isStrikethrough = true
                        ChatColor.UNDERLINE -> curComponent.isUnderlined = true
                        ChatColor.ITALIC -> curComponent.isItalic = true

                        else -> { }
                    }

                    // The color/formatting code was successfully processed, lets move on
                    continue@charLoop
                }
            }

            // Append character to builder
            partBuilder.append(char)
        }

        // Last advance
        advanceComponent(true, false)
        return components.toTypedArray()
    }

}
