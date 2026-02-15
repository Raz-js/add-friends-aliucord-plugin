package com.github.yournamehere

import android.content.Context
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.entities.MessageEmbedBuilder
import com.aliucord.entities.Plugin
import com.aliucord.patcher.*
import com.aliucord.wrappers.embeds.MessageEmbedWrapper.Companion.title
import com.discord.api.commands.ApplicationCommandType
import com.discord.models.user.CoreUser
import com.discord.stores.StoreUserTyping
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemMessage
import com.discord.widgets.chat.list.entries.ChatListEntry
import com.discord.widgets.chat.list.entries.MessageEntry
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

// Aliucord Plugin annotation. Must be present on the main class of your plugin
// Plugin class. Must extend Plugin and override start and stop
// Learn more: https://github.com/Aliucord/documentation/blob/main/plugin-dev/1_introduction.md#basic-plugin-structure
@AliucordPlugin(
    requiresRestart = false, // Whether your plugin requires a restart after being installed/updated
)
@Suppress("unused")
class MyFirstKotlinPlugin : Plugin() {
    private fun fetchToken(): String? {
        try {
            // Try known Aliucord Utils static methods/fields
            val utils = Utils::class.java
            utils.methods.firstOrNull { it.name.equals("getToken", ignoreCase = true) && it.parameterCount == 0 && it.returnType == String::class.java }
                ?.let { return (it.invoke(null) as? String) }

            utils.methods.firstOrNull { it.parameterCount == 0 && it.returnType == String::class.java }
                ?.let { val r = it.invoke(null) as? String; if (!r.isNullOrEmpty()) return r }

            utils.declaredFields.firstOrNull { it.type == String::class.java && it.name.contains("token", ignoreCase = true) }
                ?.let { it.isAccessible = true; val r = it.get(null) as? String; if (!r.isNullOrEmpty()) return r }
        } catch (e: Exception) { }

        // Fallback: try several likely Discord store classes and singletons
        val candidates = arrayOf(
            "com.discord.stores.LoginStore",
            "com.discord.stores.OauthStore",
            "com.discord.stores.AuthStore",
            "com.discord.stores.AuthTokenStore",
            "com.discord.stores.StoreOauth2"
        )
        for (c in candidates) {
            try {
                val cls = Class.forName(c)
                cls.methods.firstOrNull { it.name.contains("getToken", true) && it.parameterCount == 0 }
                    ?.let { val r = it.invoke(null) as? String; if (!r.isNullOrEmpty()) return r }

                // try static instance field then call instance method
                val instField = cls.declaredFields.firstOrNull { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == cls }
                if (instField != null) {
                    instField.isAccessible = true
                    val inst = instField.get(null)
                    if (inst != null) {
                        cls.methods.firstOrNull { it.name.contains("getToken", true) && it.parameterCount == 0 }
                            ?.let { val r = it.invoke(inst) as? String; if (!r.isNullOrEmpty()) return r }
                    }
                }
            } catch (_: Exception) { }
        }

        return null
    }

    private fun fetchFingerprint(): String? {
        try {
            // Try to get from StoreExperiments or similar
            val storeExp = Class.forName("com.discord.stores.StoreExperiments")
            val instField = storeExp.declaredFields.firstOrNull { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == storeExp }
            if (instField != null) {
                instField.isAccessible = true
                val inst = instField.get(null)
                if (inst != null) {
                    val fingerprintField = storeExp.declaredFields.firstOrNull { it.name.contains("fingerprint", true) }
                    if (fingerprintField != null) {
                        fingerprintField.isAccessible = true
                        return fingerprintField.get(inst) as? String
                    }
                }
            }
        } catch (e: Exception) { }
        return null
    }

    private fun getSuperProperties(): String {
        // Updated base64 for recent Discord Android build
        return "eyJvcyI6IkFuZHJvaWQiLCJicm93c2VyIjoiRGlzY29yZCBBbmRyb2lkIiwiZGV2aWNlIjoiU00tRzk3NUYiLCJzeXN0ZW1fbG9jYWxlIjoiZW4tVVMiLCJicm93c2VyX3VzZXJfYWdlbnQiOiJEYWx2aWsvMi4xLjAgKExpbnV4OyBVOyBBbmRyb2lkIDEwOyBTTT1HOTc1RiBCdWlsZC9RUDJBLjE5MDgxMS4wMjApIiwiYnJvd3Nlcl92ZXJzaW9uIjoiIiwib3NfdmVyc2lvbiI6IjMwIiwicmVmZXJyZXIiOiJodHRwczovL2Rpc2NvcmQuY29tL2NoYW5uZWxzL0BtZSIsInJlZmVycmluZ19kb21haW4iOiJkaXNjb3JkLmNvbSIsInJlZmVycmVyX2N1cnJlbnQiOiIiLCJyZWZlcnJpbmdfZG9tYWluX2N1cnJlbnQiOiIiLCJyZWxlYXNlX2NoYW5uZWwiOiJzdGFibGUiLCJjbGllbnRfYnVpbGRfbnVtYmVyIjoxMjYwMjEsImNsaWVudF9ldmVudF9zb3VyY2UiOm51bGx9"
    }
    override fun start(context: Context) {
        // Register a command with the name hello and description "My first command!" and no arguments.
        // Learn more: https://github.com/Aliucord/documentation/blob/main/plugin-dev/2_commands.md
        commands.registerCommand("hello", "My first command!") {
            // Just return a command result with hello world as the content
            CommandsAPI.CommandResult(
                "Hello World!",
                null, // List of embeds
                false, // Whether to send visible for everyone
            )
        }

        // A bit more advanced command with arguments
        commands.registerCommand(
            "hellowitharguments",
            "Hello World but with arguments!",
            listOf(
                Utils.createCommandOption(
                    ApplicationCommandType.STRING,
                    "name",
                    "Person to say hello to",
                ),
                Utils.createCommandOption(
                    ApplicationCommandType.USER,
                    "user",
                    "User to say hello to",
                ),
            ),
        ) { ctx ->
            // Check if a user argument was passed
            val username = if (ctx.containsArg("user")) {
                ctx.getRequiredUser("user").username
            } else {
                // Returns either the argument value if present, or the defaultValue ("World" in this case)
                ctx.getStringOrDefault("name", "World")
            }

            // Return the final result that will be displayed in chat as a response to the command
            CommandsAPI.CommandResult("Hello $username!")
        }

        // Slash command to send a friend request using your account token.
        commands.registerCommand(
            "add-friend",
            "Send a friend request (supports new global usernames or legacy Username#1234)",
            listOf(
                Utils.createCommandOption(
                    ApplicationCommandType.STRING,
                    "username",
                    "Username (global or legacy with #)",
                ),
            ),
        ) { ctx ->
            val input = ctx.getStringOrDefault("username", "").trim()
            if (input.isEmpty()) {
                return@registerCommand CommandsAPI.CommandResult("Please provide a username")
            }
            val uname: String
            val disc: String?
            if (input.contains("#")) {
                val parts = input.split("#")
                if (parts.size != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                    return@registerCommand CommandsAPI.CommandResult("Invalid format. Use Username#1234")
                }
                uname = parts[0]
                disc = parts[1]
            } else {
                uname = input
                disc = null
            }

            // Automatically fetch token from Aliucord/Discord internals
            val token = fetchToken()
            if (token.isNullOrEmpty()) {
                return@registerCommand CommandsAPI.CommandResult("Account token not available.")
            }

            val client = OkHttpClient()
            val fingerprint = fetchFingerprint()
            val superProps = getSuperProperties()
            val query = if (disc != null) "$uname#$disc" else uname

            // Step 1: Search for user ID
            val searchReq = Request.Builder()
                .url("https://discord.com/api/v9/users/search?query=$query")
                .get()
                .addHeader("Authorization", token)
                .addHeader("User-Agent", "Discord-Android/191019;RNA")
                .addHeader("X-Super-Properties", superProps)
                .addHeader("X-Discord-Locale", "en-US")
                .addHeader("Accept", "application/json")
                .apply { if (fingerprint != null) addHeader("X-Fingerprint", fingerprint) }
                .build()

            val userId = try {
                client.newCall(searchReq).execute().use { res ->
                    if (!res.isSuccessful) {
                        return@registerCommand CommandsAPI.CommandResult("Failed to search user: ${res.code}")
                    }
                    val body = res.body?.string() ?: return@registerCommand CommandsAPI.CommandResult("Empty search response")
                    val json = JSONArray(body)
                    if (json.length() == 0) return@registerCommand CommandsAPI.CommandResult("User not found")
                    val user = json.getJSONObject(0)
                    user.getString("id")
                }
            } catch (e: Exception) {
                return@registerCommand CommandsAPI.CommandResult("Error searching user: ${e.message}")
            }

            // Add delay to avoid rate limiting
            Thread.sleep(1000)

            // Step 2: Send friend request via PUT
            val putReq = Request.Builder()
                .url("https://discord.com/api/v9/users/@me/relationships/$userId")
                .put(RequestBody.create(null, ""))
                .addHeader("Authorization", token)
                .addHeader("User-Agent", "Discord-Android/191019;RNA")
                .addHeader("X-Super-Properties", superProps)
                .addHeader("X-Discord-Locale", "en-US")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "*/*")
                .apply { if (fingerprint != null) addHeader("X-Fingerprint", fingerprint) }
                .build()

            try {
                client.newCall(putReq).execute().use { res ->
                    if (res.isSuccessful) {
                        CommandsAPI.CommandResult("Friend request sent to $input")
                    } else {
                        CommandsAPI.CommandResult("Failed: ${res.code} ${res.message}")
                    }
                }
            } catch (e: Exception) {
                CommandsAPI.CommandResult("Error: ${e.message}")
            }
        }

        // Patch that adds an embed with message statistics to each message
        // Patched method is WidgetChatListAdapterItemMessage.onConfigure(int type, ChatListEntry entry)
        patcher.after<WidgetChatListAdapterItemMessage>(
            "onConfigure", // Method name
            // Refer to https://kotlinlang.org/docs/reflection.html#class-references
            // and https://docs.oracle.com/javase/tutorial/reflect/class/classNew.html
            Int::class.java, // int type
            ChatListEntry::class.java, // ChatListEntry entry
        ) { param ->
            // see https://api.xposed.info/reference/de/robv/android/xposed/XC_MethodHook.MethodHookParam.html
            // Obtain the second argument passed to the method, so the ChatListEntry
            // Because this is a Message item, it will always be a MessageEntry, so cast it to that
            val entry = param.args[1] as MessageEntry
            val message = entry.message

            // You need to be careful when messing with messages, because they may be loading
            // (user sent a message, and it is currently sending)
            if (message.isLoading) return@after

            // Now add an embed with the statistics

            // This method may be called multiple times per message, e.g. if it is edited,
            // so first remove existing embeds
            message.embeds.removeAll {
                // MessageEmbed.getTitle() is actually obfuscated, but Aliucord provides extensions for commonly used
                // obfuscated Discord classes, so just import the MessageEmbed.title extension and boom goodbye obfuscation!
                it.title == "Message Statistics"
            }

            // Creating embeds is a pain, so Aliucord provides a convenient builder
            MessageEmbedBuilder().run {
                setTitle("Message Statistics")
                addField("Length", "${message.content?.length ?: 0}", false)
                addField("ID", message.id.toString(), false)

                message.embeds.add(build())
            }
        }

        // Patch that renames Juby to JoobJoob
        patcher.before<CoreUser>("getUsername") { param ->
            // see https://api.xposed.info/reference/de/robv/android/xposed/XC_MethodHook.MethodHookParam.html
            // in before, after and instead patches, `this` refers to the instance of the class
            // the patched method is on, so the CoreUser instance here
            if (id == 925141667688878090) {
                // setResult() in before patches skips original method invocation
                param.result = "JoobJoob"
            }
        }

        // Patch that hides your typing status by replacing the method and simply doing nothing
        patcher.instead<StoreUserTyping>(
            "setUserTyping",
            Long::class.java, // java.lang.Long channelId
        ) {
            // Return null
            null
        }
    }

    override fun stop(context: Context) {
        // Remove all patches
        patcher.unpatchAll()
    }
}
