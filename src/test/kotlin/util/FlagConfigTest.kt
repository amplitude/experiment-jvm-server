package com.amplitude.experiment.util

import com.amplitude.experiment.evaluation.EvaluationFlag
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import kotlin.test.Test

class FlagConfigTest {

    val testFlagsJson = """[{"key":"flag1","segments":[{"conditions":[[{"op":"set contains any","selector":["context","user","cohort_ids"],"values":["hahahaha1"]}]]},{"metadata":{"segmentName":"All Other Users"},"variant":"off"}],"variants":{}},{"key":"flag2","segments":[{"conditions":[[{"op":"set contains any","selector":["context","user","cohort_ids"],"values":["hahahaha2"]}]],"metadata":{"segmentName":"Segment 1"},"variant":"off"},{"metadata":{"segmentName":"All Other Users"},"variant":"off"}],"variants":{}},{"key":"flag3","metadata":{"deployed":true,"evaluationMode":"local","experimentKey":"exp-1","flagType":"experiment","flagVersion":6},"segments":[{"conditions":[[{"op":"set contains any","selector":["context","user","cohort_ids"],"values":["hahahaha3"]}]],"variant":"off"},{"conditions":[[{"op":"set contains any","selector":["context","user","cocoids"],"values":["nohaha"]}]],"variant":"off"},{"metadata":{"segmentName":"All Other Users"},"variant":"off"}],"variants":{}},{"key":"flag5","segments":[{"conditions":[[{"op":"set contains any","selector":["context","user","cohort_ids"],"values":["hahahaha3","hahahaha4"]}]]},{"conditions":[[{"op":"set contains any","selector":["context","groups","org name","cohort_ids"],"values":["hahaorgname1"]}]],"metadata":{"segmentName":"Segment 1"}},{"conditions":[[{"op":"set contains any","selector":["context","gg","org name","cohort_ids"],"values":["nohahaorgname"]}]],"metadata":{"segmentName":"Segment 1"}}],"variants":{}}]"""
    val testFlags = json.decodeFromString<List<EvaluationFlag>>(testFlagsJson)

    @Test
    fun `test get grouped cohort ids from flags`() {
        val result = testFlags.getGroupedCohortIds()
        val expected = mapOf(
            "User" to setOf("hahahaha1", "hahahaha2", "hahahaha3", "hahahaha4"),
            "org name" to setOf("hahaorgname1"),
        )
        assertEquals(expected, result)
    }
}
