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
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import org.junit.Test

class McmlParserTest {

    private val parser = McmlParser()

    private fun translate(str: String): String {
        return ChatColor.translateAlternateColorCodes('&', str)
    }

    private fun assertMcmlEquals(raw: String, expected: Array<out BaseComponent>, replacements: Map<String, Any>? = null) {
        val res = parser.parse(raw, replacements)
        for ((i, component) in res.withIndex()) {
            val str1 = expected[i].toString()
            val str2 = component.toString()
            assert(str1 == str2) {
                "Input string '$raw' failed test:\nParsed: $str2\nExpected: $str1"
            }
        }
    }

    @Test
    fun colorsTest() {
        // Single word
        assertMcmlEquals(translate("Hello!"), ComponentBuilder("Hello!").create())
        assertMcmlEquals(translate("&aHello!"), ComponentBuilder("Hello!").color(ChatColor.GREEN).create())
        assertMcmlEquals(translate("&a&lHello!"), ComponentBuilder("Hello!").color(ChatColor.GREEN).bold(true).create())
        assertMcmlEquals(translate("&lHello!"), ComponentBuilder("Hello!").bold(true).create())
        assertMcmlEquals(translate("&aHello!&l"), ComponentBuilder("Hello!").color(ChatColor.GREEN).create())

        // Two words
        assertMcmlEquals(translate("Hello World!"), ComponentBuilder("Hello World!").create())
        assertMcmlEquals(translate("&aHello World!"), ComponentBuilder("Hello World!").color(ChatColor.GREEN).create())
        assertMcmlEquals(translate("Hello &bWorld!"), ComponentBuilder("Hello ").append("World!").color(ChatColor.AQUA).create())
        assertMcmlEquals(translate("&aHello &bWorld!"), ComponentBuilder("Hello ").color(ChatColor.GREEN).append("World!").color(ChatColor.AQUA).create())

        assertMcmlEquals(translate("&a&lHello World!"), ComponentBuilder("Hello World!").color(ChatColor.GREEN).bold(true).create())
        assertMcmlEquals(translate("&a&lHello &bWorld!"), ComponentBuilder("Hello ").color(ChatColor.GREEN).bold(true)
                .append("World!", ComponentBuilder.FormatRetention.NONE).color(ChatColor.AQUA).create())

        assertMcmlEquals(translate("&a&l&oHello World!"), ComponentBuilder("Hello World!").color(ChatColor.GREEN).bold(true).italic(true).create())
        assertMcmlEquals(translate("&a&l&oHello &bWorld!"), ComponentBuilder("Hello ").color(ChatColor.GREEN).bold(true).italic(true)
                .append("World!", ComponentBuilder.FormatRetention.NONE).color(ChatColor.AQUA).create())
        assertMcmlEquals(translate("&a&l&oHello &mWorld!"), ComponentBuilder("Hello ").color(ChatColor.GREEN).bold(true).italic(true)
                .append("World!").strikethrough(true).create())

        assertMcmlEquals(translate("&aHello &b&lWorld!"), ComponentBuilder("Hello ").color(ChatColor.GREEN)
                .append("World!").color(ChatColor.AQUA).bold(true).create())

        assertMcmlEquals(translate("Hello &aWorld &lthis &b&a&lis &rsupposed to be &4complicate&9d"), ComponentBuilder("Hello ")
                .append("World ").color(ChatColor.GREEN)
                .append("this ").bold(true)
                .append("is ").color(ChatColor.GREEN).bold(true)
                .append("supposed to be ", ComponentBuilder.FormatRetention.NONE)
                .append("complicate").color(ChatColor.DARK_RED)
                .append("d").color(ChatColor.BLUE).create())
    }

    @Test
    fun eventsTest() {
        assertMcmlEquals(translate("""This "is" a [quote](!"/say \"hello\" world!" "One \"more\" quote!")"""),
                ComponentBuilder("""This "is" a """).append("quote")
                        .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, """/say "hello" world!"""))
                        .event(HoverEvent(HoverEvent.Action.SHOW_TEXT, ComponentBuilder("""One "more" quote!""").create()))
                        .create())

        assertMcmlEquals(translate("""[Click!](!"/say hello")"""), ComponentBuilder("Click!")
                .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/say hello")).create())

        assertMcmlEquals(translate("""[Click!](!"/say hello" "Message")"""), ComponentBuilder("Click!")
                .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/say hello"))
                .event(HoverEvent(HoverEvent.Action.SHOW_TEXT, ComponentBuilder("Message").create()))
                .create())

        assertMcmlEquals(translate("""Text[Click!](!"/say hello" "Message")"""), ComponentBuilder("Text")
                .append("Click!")
                .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/say hello"))
                .event(HoverEvent(HoverEvent.Action.SHOW_TEXT, ComponentBuilder("Message").create()))
                .create())

        assertMcmlEquals(translate("""&aText[Click!](!"/say hello" "Message")"""), ComponentBuilder("Text").color(ChatColor.GREEN)
                .append("Click!", ComponentBuilder.FormatRetention.NONE)
                .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/say hello"))
                .event(HoverEvent(HoverEvent.Action.SHOW_TEXT, ComponentBuilder("Message").create()))
                .create())

        assertMcmlEquals(translate("""Text [&aClick!](!"/say hello" "Message")"""), ComponentBuilder("Text ")
                .append("Click!").color(ChatColor.GREEN)
                .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/say hello"))
                .event(HoverEvent(HoverEvent.Action.SHOW_TEXT, ComponentBuilder("Message").create())).create())

        assertMcmlEquals(translate("""&aText [&bClick!](!"/say hello" "Message")"""), ComponentBuilder("Text ").color(ChatColor.GREEN)
                .append("Click!").color(ChatColor.AQUA)
                .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/say hello"))
                .event(HoverEvent(HoverEvent.Action.SHOW_TEXT, ComponentBuilder("Message").create()))
                .create())

        assertMcmlEquals(translate("""&bText [Click &ahere!](!"/say hello" "&aCol&4ored &b&lMessage&o!")"""),
                ComponentBuilder("Text ").color(ChatColor.AQUA)
                        .append("Click ", ComponentBuilder.FormatRetention.NONE)
                        .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/say hello"))
                        .event(HoverEvent(HoverEvent.Action.SHOW_TEXT, ComponentBuilder("Col").color(ChatColor.GREEN)
                                .append("ored ").color(ChatColor.DARK_RED)
                                .append("Message").color(ChatColor.AQUA).bold(true)
                                .append("!").italic(true).create()))
                        .append("here!").color(ChatColor.GREEN)
                        .create())
    }

}