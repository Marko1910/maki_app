package pe.ruma.app

import pe.ruma.app.ui.screens.generator.NextStep.Kind
import pe.ruma.app.ui.screens.generator.nextStep
import org.junit.Assert.assertEquals
import org.junit.Test

/** The home's next step: same cases as pwa/scripts/check-next.ts. */
class NextStepTest {
    @Test fun liveWinsOverEverything() =
        assertEquals(Kind.LIVE, nextStep("Carlos va en camino", unpickedScan = true, balance = 9000, cashMin = 1000, weekDays = 0).kind)

    @Test fun halfDoneScanBeforeMoney() =
        assertEquals(Kind.PICKUP, nextStep(null, unpickedScan = true, balance = 9000, cashMin = 1000).kind)

    @Test fun cashWhenTheMinimumIsReached() =
        assertEquals(Kind.CASH, nextStep(null, balance = 1000, cashMin = 1000, weekDays = 0).kind)

    @Test fun emptyWeekAsksForTheFirstPhoto() =
        assertEquals(Kind.START_WEEK, nextStep(null, balance = 0, cashMin = 1000, weekDays = 0, weekLeft = 3).kind)

    @Test fun oneDayLeftIsTheNearGoal() =
        assertEquals(Kind.NEAR_GOAL, nextStep(null, weekDays = 2, weekLeft = 1).kind)

    @Test fun halfwayToCash() {
        val s = nextStep(null, balance = 600, cashMin = 1000, weekDays = 1, weekLeft = 2)
        assertEquals(Kind.NEAR_CASH, s.kind)
        assertEquals("Te faltan 400 pts para cobrar S/1.00.", s.body)
    }

    @Test fun unknownWeekFallsBackToRegister() =
        assertEquals(Kind.REGISTER, nextStep(null, balance = 0, cashMin = null, weekDays = null).kind)
}
