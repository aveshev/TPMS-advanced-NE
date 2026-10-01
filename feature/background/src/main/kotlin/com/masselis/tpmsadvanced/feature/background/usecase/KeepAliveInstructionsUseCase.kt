package com.masselis.tpmsadvanced.feature.background.usecase

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Fetches, from dontkillmyapp.com, what the user has to change on their phone's brand so that it
 * stops killing the monitoring service. The content is licensed CC-BY: whatever shows it must
 * credit dontkillmyapp.com.
 */
internal class KeepAliveInstructionsUseCase(
    manufacturer: String = Build.MANUFACTURER,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val androidVersion: String = Build.VERSION.RELEASE,
) {
    /** The page for [manufacturer], or the general one when the site has none for it */
    val vendor: String = manufacturer
        // The slug rule from https://dontkillmyapp.com/apidoc
        .lowercase(Locale.ROOT)
        .replace(" ", "-")
        .takeIf { it in vendors }
        ?: "general"

    val pageUrl: String = "$BASE_URL$vendor"

    /** Throws when offline or when the site does not answer as expected */
    suspend fun instructions(): Instructions = withContext(Dispatchers.IO) {
        (URL("${BASE_URL}api/v2/$vendor.json").openConnection() as HttpURLConnection)
            .apply { connectTimeout = TIMEOUT_MILLIS }
            .apply { readTimeout = TIMEOUT_MILLIS }
            .let { connection ->
                try {
                    check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                        "dontkillmyapp.com answered ${connection.responseCode} for $vendor"
                    }
                    connection.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    connection.disconnect()
                }
            }
            .let(::JSONObject)
            .let { json ->
                json
                    .getString("user_solution")
                    .let { solution ->
                        // The whole page when the site renamed its sections
                        sectionIds()
                            ?.let { solution.sections(it) }
                            ?.let { it to "Android $androidVersion" }
                            ?: (solution to null)
                    }
                    .let { (solution, shownFor) ->
                        Instructions(
                            vendorName = json.getString("name"),
                            explanation = json.optString("explanation"),
                            userSolution = solution,
                            shownFor = shownFor,
                        )
                    }
            }
    }

    /**
     * The sections of the page that apply to this phone, for the pages split by Android version.
     * Samsung's Android 14 section only adds its own apps to the Android 13 settings, both apply.
     */
    @Suppress("MagicNumber")
    private fun sectionIds(): List<String>? = when (vendor) {
        "samsung" -> when {
            sdkInt >= 34 -> listOf("android-14", "android-13")
            sdkInt == 33 -> listOf("android-13")
            sdkInt >= 30 -> listOf("android-11")
            sdkInt >= 28 -> listOf("android-pie-and-10")
            else -> listOf("android-oreo-and-nougat-8--7")
        }

        else -> null
    }

    /**
     * @property explanation and [userSolution] are HTML
     * @property shownFor the Android version [userSolution] was trimmed to, null for the whole page
     */
    data class Instructions(
        val vendorName: String,
        val explanation: String,
        val userSolution: String,
        val shownFor: String?,
    )

    internal companion object {

        /**
         * Keeps the `<h2>` sections of this HTML whose id is in [ids], in the page's order. Null
         * when none of them is found.
         */
        fun String.sections(ids: List<String>): String? = split(Regex("(?=<h2 )"))
            .filter { section ->
                Regex("^<h2 id=\"([^\"]*)\"")
                    .find(section)
                    ?.groupValues
                    ?.get(1)
                    ?.let { it in ids } == true
            }
            .takeIf { it.isNotEmpty() }
            ?.joinToString("")

        const val BASE_URL = "https://dontkillmyapp.com/"
        private const val TIMEOUT_MILLIS = 15_000

        /** The pages of https://dontkillmyapp.com/api/v1/output.json, "general" aside */
        private val vendors = setOf(
            "xiaomi", "samsung", "oneplus", "huawei", "ulefone", "oppo", "meizu", "asus", "wiko",
            "vivo", "tecno", "realme", "motorola", "lenovo", "blackview", "unihertz", "sony",
            "stock_android", "nokia", "htc", "hmd-global", "google",
        )
    }
}
