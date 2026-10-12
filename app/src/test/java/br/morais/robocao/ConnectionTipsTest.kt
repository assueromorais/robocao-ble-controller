package br.morais.robocao

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionTipsTest {

    @Test
    fun firstFailure_doesNotShowTip() {
        val tips = ConnectionTips()

        assertFalse(tips.recordFailure())
    }

    @Test
    fun secondConsecutiveFailure_showsRestartTip() {
        val tips = ConnectionTips()

        tips.recordFailure()

        assertTrue(tips.recordFailure())
    }

    @Test
    fun success_resetsCount() {
        val tips = ConnectionTips()
        tips.recordFailure()

        tips.recordSuccess()

        assertFalse(tips.recordFailure())
    }

    @Test
    fun keepsShowingTipWhileFailing() {
        val tips = ConnectionTips()
        tips.recordFailure()
        tips.recordFailure()

        assertTrue(tips.recordFailure())
    }
}
