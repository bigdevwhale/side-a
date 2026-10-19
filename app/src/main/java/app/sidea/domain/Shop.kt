package app.sidea.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * The record shop gets one delivery a week. Between deliveries you can buy one record, and an unused
 * week doesn't carry over: scarcity is the point. The first few records are a gift, for the ones you
 * already own and love.
 */
object Shop {
    /** How many records the shelf holds. To bring a new one home when it's full, one goes to the second-hand shop. */
    const val SHELF = 24

    /** How many records can wait on the counter. */
    const val HOLDS = 6

    /** The first crate: records you already own, free. */
    const val GIFTS = 3

    /** The delivery day this week falls on or before [today]: the start of the current shop week. */
    fun weekStart(today: LocalDate, day: DayOfWeek): LocalDate = today.with(TemporalAdjusters.previousOrSame(day))

    /** The next delivery after [today]. */
    fun nextDelivery(today: LocalDate, day: DayOfWeek): LocalDate = today.with(TemporalAdjusters.next(day))

    /** Can a record be bought today, given the day of the last purchase (null if never)? */
    fun open(lastBuy: LocalDate?, today: LocalDate, day: DayOfWeek): Boolean = lastBuy == null || lastBuy < weekStart(today, day)

    /** What the shop can do for you today. */
    fun allowance(gifts: Int, lastBuy: LocalDate?, today: LocalDate, day: DayOfWeek): Allowance = when {
        gifts > 0 -> Allowance.Gift(gifts)
        open(lastBuy, today, day) -> Allowance.Open
        else -> Allowance.Closed(nextDelivery(today, day))
    }
}

sealed interface Allowance {
    val canBuy: Boolean get() = this !is Closed

    data class Gift(val left: Int) : Allowance
    data object Open : Allowance
    data class Closed(val next: LocalDate) : Allowance
}
