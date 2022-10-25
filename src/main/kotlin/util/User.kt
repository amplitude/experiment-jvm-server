package com.amplitude.experiment.util

import com.amplitude.experiment.ExperimentUser
import com.amplitude.experiment.evaluation.serialization.SerialExperimentUser
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

internal fun ExperimentUser?.toSerialExperimentUser(): SerialExperimentUser {
    return SerialExperimentUser(
        userId = this?.userId,
        deviceId = this?.deviceId,
        region = this?.region,
        dma = this?.dma,
        country = this?.country,
        city = this?.city,
        language = this?.language,
        platform = this?.platform,
        version = this?.version,
        os = this?.os,
        deviceManufacturer = this?.deviceManufacturer,
        deviceBrand = this?.deviceBrand,
        deviceModel = this?.deviceModel,
        carrier = this?.carrier,
        library = this?.library,
        userProperties = this?.userProperties?.mapValues {
            when (val value = it.value) {
                is String -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                else -> JsonNull
            }
        },
        cohortIds = this?.cohortIds,
    )
}
