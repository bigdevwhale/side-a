package app.sidea

import app.sidea.domain.Allowance
import app.sidea.domain.Match
import app.sidea.domain.Needle
import app.sidea.domain.Shop
import app.sidea.domain.Sides
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ShopTest {
    private val sat = DayOfWeek.SATURDAY

    // Monday 19 October 2026; the deliveries are on Saturdays.
    private val monday = LocalDate.of(2026, 10, 19)

    @Test fun weekStartsOnTheLastDelivery() {
        assertEquals(LocalDate.of(2026, 10, 17), Shop.weekStart(monday, sat))
        assertEquals(LocalDate.of(2026, 10, 17), Shop.weekStart(LocalDate.of(2026, 10, 17), sat))
        assertEquals(LocalDate.of(2026, 10, 24), Shop.nextDelivery(monday, sat))
    }

    @Test fun oneRecordAWeek() {
        assertTrue(Shop.open(null, monday, sat))
        assertTrue(Shop.open(LocalDate.of(2026, 10, 16), monday, sat))
        assertFalse(Shop.open(LocalDate.of(2026, 10, 17), monday, sat))
        assertFalse(Shop.open(monday, monday, sat))
        // The next delivery opens it again.
        assertTrue(Shop.open(monday, LocalDate.of(2026, 10, 24), sat))
    }

    @Test fun anUnusedWeekDoesNotCarryOver() {
        // Nothing bought for a month still gives just one.
        assertEquals(Allowance.Open, Shop.allowance(0, LocalDate.of(2026, 9, 1), monday, sat))
    }

    @Test fun giftsComeFirst() {
        assertEquals(Allowance.Gift(2), Shop.allowance(2, monday, monday, sat))
        assertEquals(Allowance.Closed(LocalDate.of(2026, 10, 24)), Shop.allowance(0, monday, monday, sat))
        assertFalse(Allowance.Closed(monday).canBuy)
    }
}

class SidesTest {
    @Test fun splitsTheRunningTimeEvenly() {
        assertEquals(2, Sides.split(listOf(300, 300, 300, 300).map { it * 1000L }))
        assertEquals(1, Sides.split(listOf(1200, 300, 300, 300).map { it * 1000L }))
        assertEquals(3, Sides.split(listOf(100, 100, 100, 400).map { it * 1000L }))
    }

    @Test fun everySideGetsASong() {
        assertEquals(1, Sides.split(listOf(10_000L, 999_000L)))
        assertEquals(1, Sides.split(listOf(999_000L, 10_000L)))
        assertEquals(1, Sides.split(listOf(5_000L)))
        assertEquals(0, Sides.split(emptyList()))
    }

    @Test fun ranges() {
        assertEquals(0 until 3, Sides.range(0, 7, 3))
        assertEquals(3 until 7, Sides.range(1, 7, 3))
    }

    @Test fun wearGrowsWithPlaysAndStaysInBounds() {
        assertEquals(0f, Sides.wear(0f), 0.0001f)
        assertTrue(Sides.wear(20f) in 0.6f..0.65f)
        assertTrue(Sides.wear(5f) < Sides.wear(10f))
        assertTrue(Sides.wear(10_000f) <= 1f)
        assertEquals(2f, Sides.plays(80 * 60_000L, 40 * 60_000L), 0.0001f)
        // Unknown length: a typical LP.
        assertEquals(1f, Sides.plays(Sides.TYPICAL_MS, 0), 0.0001f)
    }
}

class MatchTest {
    @Test fun foldsDecorations() {
        assertEquals("abbey road", Match.norm("Abbey Road (Remastered 2019)"))
        assertEquals("abbey road", Match.norm("Abbey Road - 2019 Remaster"))
        assertEquals("kind of blue", Match.norm("Kind of Blue [Legacy Edition]"))
        assertEquals("ежик в тумане", Match.norm("Ёжик в тумане"))
        assertEquals("beyonce", Match.norm("Beyoncé"))
    }

    @Test fun albums() {
        assertTrue(Match.album("True Believers", "Darius Rucker", "True Believers", "Darius Rucker"))
        assertTrue(Match.album("Abbey Road", "The Beatles", "Abbey Road (Super Deluxe Edition)", "The Beatles"))
        assertTrue(Match.album("Abbey Road", "", "Abbey Road", "The Beatles"))
        assertTrue(Match.album("Random Access Memories", "Daft Punk", "Random Access Memories", null))
        assertFalse(Match.album("True Believers", "Darius Rucker", "The Surface", "Beartooth"))
        assertFalse(Match.album("Abbey Road", "The Beatles", "Abbey Road", "Someone Else"))
        // One word isn't the start of another.
        assertFalse(Match.album("Blue", "Joni Mitchell", "Blueprint", "Joni Mitchell"))
        assertFalse(Match.album("Blue", "Joni Mitchell", null, "Joni Mitchell"))
    }

    @Test fun tracks() {
        assertTrue(Match.track("Wagon Wheel", "Wagon Wheel"))
        assertTrue(Match.track("Something (Remastered 2009)", "Something"))
        assertFalse(Match.track("", ""))
        assertFalse(Match.track("Riptide", "Wagon Wheel"))
    }
}

class NeedleTest {
    @Test fun skipped() {
        assertTrue(Needle.skipped(30_000, 240_000))
        assertFalse(Needle.skipped(235_000, 240_000))
        assertFalse(Needle.skipped(30_000, 0))
    }

    @Test fun jumped() {
        assertFalse(Needle.jumped(60_000, 62_000))
        assertTrue(Needle.jumped(60_000, 90_000))
        assertTrue(Needle.jumped(60_000, 10_000))
    }

    @Test fun radius() {
        assertEquals(0f, Needle.radius(0, 1_000_000), 0f)
        assertEquals(0.5f, Needle.radius(500_000, 1_000_000), 0f)
        assertEquals(1f, Needle.radius(2_000_000, 1_000_000), 0f)
        assertEquals(0f, Needle.radius(5, 0), 0f)
    }
}
