package io.github.command1264.invitecode.platform.bukkit

import io.github.command1264.invitecode.core.QualificationConfiguration
import io.github.command1264.invitecode.core.QualificationConfigurationParser
import io.github.command1264.invitecode.core.RewardActionType
import io.github.command1264.invitecode.core.RewardConfiguration
import io.github.command1264.invitecode.core.RewardConfigurationParser
import io.github.command1264.invitecode.core.StorageConfiguration
import io.github.command1264.invitecode.core.StorageConfigurationParser

data class InviteCodeRuntimeConfiguration(
    val general: BukkitPluginConfiguration,
    val storage: StorageConfiguration,
    val qualifications: QualificationConfiguration,
    val rewards: RewardConfiguration,
    val messages: LocalizedMessages,
    val availableLocales: Set<String>,
)

data class RuntimeReloadCandidate(
    val configuration: InviteCodeRuntimeConfiguration,
    val storageRestartRequired: Boolean,
)

object RuntimeConfigurationReload {
    fun reconcile(
        current: InviteCodeRuntimeConfiguration,
        candidate: InviteCodeRuntimeConfiguration,
    ): RuntimeReloadCandidate {
        val storageChanged = current.storage != candidate.storage
        return RuntimeReloadCandidate(
            if (storageChanged) candidate.copy(storage = current.storage) else candidate,
            storageChanged,
        )
    }
}

object RuntimeConfigurationAssembler {
    fun parse(
        general: Map<String, Any?>,
        storage: Map<String, Any?>,
        qualifications: Map<String, Any?>,
        rewards: Map<String, Any?>,
        messageCatalogs: Map<String, Map<String, Any?>>,
        onMissingMessage: (String) -> Unit = {},
        bundledMessageCatalogs: Map<String, Map<String, Any?>> = emptyMap(),
    ): InviteCodeRuntimeConfiguration {
        val parsedGeneral = BukkitPluginConfigurationParser.parse(general)
        val parsedStorage = StorageConfigurationParser.parse(storage)
        val parsedQualifications = QualificationConfigurationParser.parse(qualifications)
        val parsedRewards = RewardConfigurationParser.parse(rewards)
        require(messageCatalogs.isNotEmpty()) { "At least one message catalog is required." }
        val parsedCatalogs = messageCatalogs.mapValues { (locale, document) ->
            MessageCatalogParser.parse(locale, document)
        }
        val parsedBundledCatalogs = bundledMessageCatalogs.mapValues { (locale, document) ->
            MessageCatalogParser.parse(locale, document)
        }
        val defaultCatalog = parsedCatalogs[parsedGeneral.language.defaultLocale]
            ?: throw IllegalArgumentException(
                "Default locale ${parsedGeneral.language.defaultLocale} does not have a message catalog.",
            )
        val rewardMessageKeys = buildList {
            parsedRewards.qualificationRewards.values.forEach { reward ->
                addAll(reward.inviterActions.filter { it.type == RewardActionType.MESSAGE }.map { it.payload })
                addAll(reward.inviteeActions.filter { it.type == RewardActionType.MESSAGE }.map { it.payload })
            }
            parsedRewards.milestoneRewards.values.forEach { reward ->
                addAll(reward.inviterActions.filter { it.type == RewardActionType.MESSAGE }.map { it.payload })
            }
        }.toSet()
        val missingRewardMessages = rewardMessageKeys - defaultCatalog.keys -
            parsedBundledCatalogs[parsedGeneral.language.defaultLocale].orEmpty().keys
        require(missingRewardMessages.isEmpty()) {
            "Default locale is missing Reward message keys: ${missingRewardMessages.sorted().joinToString()}."
        }
        return InviteCodeRuntimeConfiguration(
            general = parsedGeneral,
            storage = parsedStorage,
            qualifications = parsedQualifications,
            rewards = parsedRewards,
            messages = LocalizedMessages(
                parsedGeneral.language.defaultLocale,
                parsedGeneral.language.useClientLocale,
                parsedCatalogs,
                onMissingMessage,
                bundledCatalogs = parsedBundledCatalogs,
            ),
            availableLocales = parsedCatalogs.keys,
        )
    }
}

object MessageCatalogParser {
    const val CURRENT_VERSION = 1

    fun parse(locale: String, document: Map<String, Any?>): Map<String, String> {
        require(locale.isNotBlank()) { "Message catalog locale must not be blank." }
        val version = (document["messages-version"] as? Number)?.toString()?.toIntOrNull()
            ?: throw IllegalArgumentException("Message catalog $locale requires integer messages-version.")
        require(version == CURRENT_VERSION) {
            "Message catalog $locale version $version is unsupported; expected $CURRENT_VERSION."
        }
        require(document.keys.any { it != "messages-version" }) {
            "Message catalog $locale must contain at least one message."
        }
        val messages = linkedMapOf<String, String>()
        document.filterKeys { it != "messages-version" }.forEach { (key, value) ->
            flatten(locale, key, value, messages)
        }
        return messages
    }

    private fun flatten(
        locale: String,
        key: String,
        value: Any?,
        destination: MutableMap<String, String>,
    ) {
        require(key.isNotBlank()) { "Message catalog $locale contains a blank key." }
        when (value) {
            is String -> {
                if (key == "presentation.prefix") {
                    require(value == value.trim() && '\n' !in value && '\r' !in value &&
                        !Regex("\\{[a-z][a-z0-9_]*}").containsMatchIn(value)) {
                        "Message catalog $locale prefix must be one unpadded line without variables."
                    }
                } else {
                    require(value.isNotBlank()) { "Message catalog $locale key $key must not be blank." }
                }
                destination[key] = value
            }
            is Map<*, *> -> {
                require(value.isNotEmpty() && value.keys.all { it is String }) {
                    "Message catalog $locale key $key must be a non-empty map with string keys."
                }
                value.forEach { (childKey, childValue) ->
                    flatten(locale, "$key.${childKey as String}", childValue, destination)
                }
            }
            else -> throw IllegalArgumentException(
                "Message catalog $locale key $key must be a string or nested map.",
            )
        }
    }
}
