package com.amplitude.experiment

/**
 * The user to fetch experiment/flag variants for. This is an immutable object
 * that can be created using an [ExperimentUser.Builder]. Example usage:
 *
 * ```
 * ExperimentUser.builder().userId("user@company.com").build()
 * ```
 *
 * You can copy and modify a user using [copyToBuilder].
 *
 * ```
 * val user = ExperimentUser.builder()
 *     .userId("user@company.com")
 *     .build()
 * val newUser = user.copyToBuilder()
 *     .userProperty("username", "bumblebee")
 *     .build()
 * ```
 */
data class ExperimentUser internal constructor(
    @JvmField val userId: String? = null,
    @JvmField val deviceId: String? = null,
    @JvmField val country: String? = null,
    @JvmField val region: String? = null,
    @JvmField val dma: String? = null,
    @JvmField val city: String? = null,
    @JvmField val language: String? = null,
    @JvmField val platform: String? = null,
    @JvmField val version: String? = null,
    @JvmField val os: String? = null,
    @JvmField val deviceManufacturer: String? = null,
    @JvmField val deviceBrand: String? = null,
    @JvmField val deviceModel: String? = null,
    @JvmField val carrier: String? = null,
    @JvmField val library: String? = null,
    @JvmField val userProperties: Map<String, Any?>? = null,
    @JvmField val cohortIds: Set<String>? = null,
    @JvmField val groups: Map<String, Set<String>>? = null,
    @JvmField val groupProperties: Map<String, Map<String, Map<String, Any?>>>? = null,
    @JvmField val groupCohortIds: Map<String, Map<String, Set<String>>>? = null,
) {

    /**
     * Construct an empty [ExperimentUser].
     */
    constructor() : this(userId = null)

    fun copyToBuilder(): Builder {
        return builder()
            .userId(this.userId)
            .deviceId(this.deviceId)
            .country(this.country)
            .region(this.region)
            .dma(this.dma)
            .city(this.city)
            .language(this.language)
            .platform(this.platform)
            .version(this.version)
            .os(this.os)
            .deviceManufacturer(this.deviceManufacturer)
            .deviceBrand(this.deviceBrand)
            .deviceModel(this.deviceModel)
            .carrier(this.carrier)
            .library(this.library)
            .userProperties(this.userProperties)
            .cohortIds(this.cohortIds)
            .groups(this.groups)
            .groupProperties(this.groupProperties)
            .groupCohortIds(this.groupCohortIds)
    }

    companion object {
        @JvmStatic
        fun builder(): Builder {
            return Builder()
        }
    }

    class Builder {
        private var userId: String? = null
        private var deviceId: String? = null
        private var country: String? = null
        private var region: String? = null
        private var dma: String? = null
        private var city: String? = null
        private var language: String? = null
        private var platform: String? = null
        private var version: String? = null
        private var os: String? = null
        private var deviceManufacturer: String? = null
        private var deviceBrand: String? = null
        private var deviceModel: String? = null
        private var carrier: String? = null
        private var library: String? = null
        private var userProperties: MutableMap<String, Any?>? = null
        private var cohortIds: Set<String>? = null
        private var groups: MutableMap<String, Set<String>>? = null
        private var groupProperties: MutableMap<String, MutableMap<String, MutableMap<String, Any?>>>? = null
        private var groupCohortIds: MutableMap<String, MutableMap<String, Set<String>>>? = null

        fun userId(userId: String?) = apply { this.userId = userId }
        fun deviceId(deviceId: String?) = apply { this.deviceId = deviceId }
        fun country(country: String?) = apply { this.country = country }
        fun region(region: String?) = apply { this.region = region }
        fun dma(dma: String?) = apply { this.dma = dma }
        fun city(city: String?) = apply { this.city = city }
        fun language(language: String?) = apply { this.language = language }
        fun platform(platform: String?) = apply { this.platform = platform }
        fun version(version: String?) = apply { this.version = version }
        fun os(os: String?) = apply { this.os = os }
        fun deviceManufacturer(deviceManufacturer: String?) = apply {
            this.deviceManufacturer = deviceManufacturer
        }
        fun deviceBrand(deviceBrand: String?) = apply { this.deviceBrand = deviceBrand }
        fun deviceModel(deviceModel: String?) = apply { this.deviceModel = deviceModel }
        fun carrier(carrier: String?) = apply { this.carrier = carrier }
        fun library(library: String?) = apply { this.library = library }
        fun userProperties(userProperties: Map<String, Any?>?) = apply {
            this.userProperties = userProperties?.toMutableMap()
        }
        fun userProperty(key: String, value: Any?) = apply {
            userProperties = (userProperties ?: mutableMapOf()).apply {
                this[key] = value
            }
        }
        fun cohortIds(cohortIds: Set<String>?) = apply {
            this.cohortIds = cohortIds
        }

        fun groups(groups: Map<String, Set<String>>?) = apply {
            this.groups = groups?.toMutableMap()
        }

        fun group(groupType: String, groupName: String) = apply {
            this.groups = (this.groups ?: mutableMapOf()).apply { put(groupType, setOf(groupName)) }
        }

        fun groupProperties(groupProperties: Map<String, Map<String, Map<String, Any?>>>?) = apply {
            this.groupProperties = groupProperties?.mapValues { groupTypes ->
                groupTypes.value.toMutableMap().mapValues { groupNames ->
                    groupNames.value.toMutableMap()
                }.toMutableMap()
            }?.toMutableMap()
        }

        fun groupProperty(groupType: String, groupName: String, key: String, value: Any?) = apply {
            this.groupProperties = (this.groupProperties ?: mutableMapOf()).apply {
                getOrPut(groupType) { mutableMapOf(groupName to mutableMapOf()) }
                    .getOrPut(groupName) { mutableMapOf(key to value) }[key] = value
            }
        }

        internal fun groupCohortIds(groupCohortIds: Map<String, Map<String, Set<String>>>?) = apply {
            this.groupCohortIds = groupCohortIds?.mapValues { groupTypes ->
                groupTypes.value.toMutableMap()
            }?.toMutableMap()
        }

        fun groupCohortIds(groupType: String, groupName: String, cohortIds: Set<String>) = apply {
            this.groupCohortIds = (this.groupCohortIds ?: mutableMapOf()).apply {
                val groupNames = getOrPut(groupType) { mutableMapOf() }
                groupNames[groupName] = cohortIds
            }
        }

        fun build(): ExperimentUser {
            return ExperimentUser(
                userId = userId,
                deviceId = deviceId,
                country = country,
                region = region,
                dma = dma,
                city = city,
                language = language,
                platform = platform,
                version = version,
                os = os,
                deviceManufacturer = deviceManufacturer,
                deviceBrand = deviceBrand,
                deviceModel = deviceModel,
                carrier = carrier,
                library = library,
                userProperties = userProperties,
                cohortIds = cohortIds,
                groups = groups,
                groupProperties = groupProperties,
                groupCohortIds = groupCohortIds,
            )
        }
    }
}
