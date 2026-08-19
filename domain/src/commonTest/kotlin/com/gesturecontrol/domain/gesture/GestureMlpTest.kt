package com.gesturecontrol.domain.gesture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private val REFERENCE_INPUT = floatArrayOf(
    -0.25091976f, 0.90142864f, 0.46398789f, 0.19731697f, -0.68796271f, -0.68801093f, -0.88383275f,
    0.73235232f, 0.20223002f, 0.41614515f, -0.95883101f, 0.93981969f, 0.66488528f, -0.57532179f,
    -0.63635010f, -0.63319099f, -0.39151552f, 0.04951286f, -0.13610996f, -0.41754171f, 0.22370578f,
    -0.72101229f, -0.41571072f, -0.26727632f, -0.08786003f, 0.57035190f, -0.60065246f, 0.02846888f,
    0.18482913f, -0.90709919f, 0.21508971f, -0.65895176f, -0.86989683f, 0.89777106f, 0.93126404f,
    0.61679471f, -0.39077246f, -0.80465579f, 0.36846605f, -0.11969502f, -0.75592351f, -0.00964618f,
    -0.93122298f, 0.81864083f, -0.48244002f, 0.32504457f, -0.37657785f, 0.04013604f, 0.09342056f,
    -0.63029110f, 0.93916923f, 0.55026567f, 0.87899786f, 0.78965467f, 0.19579996f, 0.84374845f,
    -0.82301497f, -0.60803425f, -0.90954542f, -0.34933934f, -0.22264542f, -0.45730194f, 0.65747499f,
)
private val REFERENCE_OUTPUT = floatArrayOf(0.00025874f, 0.39299506f, 0.60353559f, 0.00320582f, 0.00000485f)

class GestureMlpTest {
    private val tolerance = 0.0001f

    @Test
    fun `matches the real LiteRT-run output for a fixed reference input`() {
        val result = GestureMlp.run(REFERENCE_INPUT)

        assertEquals(REFERENCE_OUTPUT.size, result.size)
        for (i in REFERENCE_OUTPUT.indices) {
            assertEquals(REFERENCE_OUTPUT[i], result[i], tolerance, "probability[$i] mismatch")
        }
    }

    @Test
    fun `output is a valid probability distribution`() {
        val result = GestureMlp.run(REFERENCE_INPUT)

        assertEquals(1.0f, result.sum(), 0.001f)
        result.forEach { probability -> assertTrue(probability in 0f..1f, "probability $probability out of [0,1]") }
    }

    @Test
    fun `rejects a feature vector of the wrong size`() {
        assertFailsWith<IllegalArgumentException> {
            GestureMlp.run(FloatArray(10))
        }
    }
}
