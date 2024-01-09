package com.amplitude.experiment.util

import com.amplitude.experiment.ExperimentUser
import com.amplitude.experiment.evaluation.EvaluationContext

internal fun ExperimentUser.toEvaluationContext(): EvaluationContext {
    val context = EvaluationContext()
    val groups = mutableMapOf<String, Map<String, Any>>()
    if (!this.groups.isNullOrEmpty()) {
        for (entry in this.groups) {
            val groupType = entry.key
            val groupNames = entry.value
            if (groupNames.isNotEmpty()) {
                val groupName = groupNames.first()
                val groupNameMap = mutableMapOf<String, Any>().apply { put("group_name", groupName) }
                val groupProperties = this.groupProperties?.get(groupType)?.get(groupName)
                if (!groupProperties.isNullOrEmpty()) {
                    groupNameMap["group_properties"] = groupProperties
                }
                groups[groupType] = groupNameMap
            }
        }
        context["groups"] = groups
    }
    val userMap = this.toMap().toMutableMap()
    userMap.remove("groups")
    userMap.remove("group_properties")
    context["user"] = userMap
    return context
}

internal fun ExperimentUser.toMap(): Map<String, Any?> {
    return mapOf(
        "user_id" to userId,
        "device_id" to deviceId,
        "country" to country,
        "region" to region,
        "dma" to dma,
        "city" to city,
        "language" to language,
        "platform" to platform,
        "version" to version,
        "os" to os,
        "device_manufacturer" to deviceManufacturer,
        "device_brand" to deviceBrand,
        "device_model" to deviceModel,
        "carrier" to carrier,
        "library" to library,
        "user_properties" to userProperties,
        "cohort_ids" to cohortIds,
        "groups" to groups,
        "group_properties" to groupProperties
    ).filterValues { it != null }
}

internal fun ExperimentUser.toJson(): String = json.encodeToString(AnySerializer, toMap())
