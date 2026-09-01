package com.sanka1610.reprodroid.data.repository

import com.sanka1610.reprodroid.data.local.BuildConfigurationValidationState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import org.erdtman.jcs.JsonCanonicalizer
import java.security.MessageDigest

data class BuildConfigurationInput(
    val buildRoot: String? = null,
    val modulePath: String? = null,
    val variant: String? = null,
    val tasks: List<String> = emptyList(),
    val javaMajor: Int? = null,
    val gradleVersion: String? = null,
    val compileSdk: Int? = null,
    val buildToolsVersion: String? = null,
    val ndkVersion: String? = null,
    val cmakeVersion: String? = null,
)

data class ValidatedBuildConfiguration(
    val canonicalJson: String,
    val contentSha256: String,
    val validationState: BuildConfigurationValidationState,
)

object BuildConfigurationValidator {
    fun decodeCanonical(canonicalJson: String, expectedContentSha256: String): BuildConfigurationInput {
        val objectValue = Json.parseToJsonElement(canonicalJson).jsonObject
        fun text(name: String): String? = objectValue.getValue(name).jsonPrimitive.contentOrNull
        fun number(name: String): Int? = objectValue.getValue(name).jsonPrimitive.intOrNull
        val input = BuildConfigurationInput(
            buildRoot = text("buildRoot"),
            modulePath = text("modulePath"),
            variant = text("variant"),
            tasks = objectValue.getValue("tasks").jsonArray.map { it.jsonPrimitive.content },
            javaMajor = number("javaMajor"),
            gradleVersion = text("gradleVersion"),
            compileSdk = number("compileSdk"),
            buildToolsVersion = text("buildToolsVersion"),
            ndkVersion = text("ndkVersion"),
            cmakeVersion = text("cmakeVersion"),
        )
        val validated = validate(input)
        require(validated.canonicalJson == canonicalJson) { "Stored build configuration is not canonical JCS." }
        require(validated.contentSha256 == expectedContentSha256) { "Stored build configuration hash is invalid." }
        return input
    }

    fun validate(input: BuildConfigurationInput): ValidatedBuildConfiguration {
        input.buildRoot?.let(::validateRelativePath)
        input.modulePath?.let {
            require(it.toByteArray(Charsets.UTF_8).size <= MAX_TEXT_BYTES && MODULE_PATH.matches(it)) {
                "Module path is invalid."
            }
        }
        input.variant?.let { require(VARIANT.matches(it)) { "Variant is invalid." } }
        require(input.tasks.size <= MAX_TASKS) { "At most $MAX_TASKS Gradle tasks may be saved." }
        require(input.tasks.distinct().size == input.tasks.size) { "Gradle tasks must not contain duplicates." }
        input.tasks.forEach { task ->
            require(task.toByteArray(Charsets.UTF_8).size <= MAX_TEXT_BYTES && TASK.matches(task)) {
                "Gradle task is invalid: $task"
            }
        }
        input.javaMajor?.let { require(it in 1..255) { "Java major version must be between 1 and 255." } }
        input.gradleVersion?.let { validateAsciiVersion(it, GRADLE_VERSION, "Gradle") }
        input.compileSdk?.let { require(it in 1..10_000) { "compileSdk must be between 1 and 10000." } }
        input.buildToolsVersion?.let { validateAsciiVersion(it, TOOL_VERSION, "Build Tools") }
        input.ndkVersion?.let { validateAsciiVersion(it, TOOL_VERSION, "NDK") }
        input.cmakeVersion?.let { validateAsciiVersion(it, TOOL_VERSION, "CMake") }

        val configured = input.buildRoot != null && input.modulePath != null && input.variant != null &&
            input.tasks.isNotEmpty() && input.javaMajor != null && input.gradleVersion != null &&
            input.compileSdk != null && input.buildToolsVersion != null
        val rawJson = buildJsonObject {
            put("schemaVersion", JsonPrimitive(SCHEMA_VERSION))
            put("buildRoot", input.buildRoot.jsonValue())
            put("modulePath", input.modulePath.jsonValue())
            put("variant", input.variant.jsonValue())
            put("tasks", JsonArray(input.tasks.map(::JsonPrimitive)))
            put("javaMajor", input.javaMajor.jsonValue())
            put("gradleVersion", input.gradleVersion.jsonValue())
            put("compileSdk", input.compileSdk.jsonValue())
            put("buildToolsVersion", input.buildToolsVersion.jsonValue())
            put("ndkVersion", input.ndkVersion.jsonValue())
            put("cmakeVersion", input.cmakeVersion.jsonValue())
        }.toString()
        val canonicalBytes = JsonCanonicalizer(rawJson).encodedUTF8
        return ValidatedBuildConfiguration(
            canonicalJson = canonicalBytes.toString(Charsets.UTF_8),
            contentSha256 = MessageDigest.getInstance("SHA-256")
                .digest(canonicalBytes)
                .joinToString("") { byte -> "%02x".format(byte) },
            validationState = if (configured) {
                BuildConfigurationValidationState.CONFIGURED
            } else {
                BuildConfigurationValidationState.DRAFT
            },
        )
    }

    private fun validateRelativePath(path: String) {
        require(path.toByteArray(Charsets.UTF_8).size <= MAX_TEXT_BYTES) { "Build root is too long." }
        if (path == ".") return
        require(
            path.isNotBlank() && !path.startsWith('/') && !path.endsWith('/') && '\\' !in path &&
                path.split('/').all { segment ->
                    segment.isNotBlank() && segment != "." && segment != ".." &&
                        segment.none { it == '\u0000' || it.isISOControl() }
                },
        ) { "Build root must be a confined relative path." }
    }

    private fun validateAsciiVersion(value: String, pattern: Regex, label: String) {
        require(value.length <= MAX_VERSION_BYTES && value.all { it.code in 0x21..0x7e } && pattern.matches(value)) {
            "$label version is invalid."
        }
    }

    private fun String?.jsonValue() = this?.let(::JsonPrimitive) ?: JsonNull
    private fun Int?.jsonValue() = this?.let(::JsonPrimitive) ?: JsonNull

    private const val SCHEMA_VERSION = 1
    private const val MAX_TASKS = 32
    private const val MAX_TEXT_BYTES = 1024
    private const val MAX_VERSION_BYTES = 128
    private const val SEGMENT = "[A-Za-z_][A-Za-z0-9_.-]{0,127}"
    private val MODULE_PATH = Regex(":(?:$SEGMENT(?::$SEGMENT)*)?")
    private val TASK = Regex(":$SEGMENT(?::$SEGMENT)*")
    private val VARIANT = Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")
    private val GRADLE_VERSION = Regex("[0-9]+(?:[.][0-9]+){1,2}(?:-rc-[0-9]+|-milestone-[0-9]+)?")
    private val TOOL_VERSION = Regex("[0-9]+(?:[.][0-9]+){1,3}(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?")
}
