package dev.fourthbreakfast.planner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomReminderTest {
    @Test
    fun convertsEachUnitToStoredMinutes() {
        assertEquals(45, customReminderMinutes("45", ReminderUnit.MINUTES))
        assertEquals(180, customReminderMinutes("3", ReminderUnit.HOURS))
        assertEquals(4320, customReminderMinutes("3", ReminderUnit.DAYS))
        assertEquals(5, customReminderMinutes("005", ReminderUnit.MINUTES))
    }

    @Test
    fun rejectsEmptyNonWholeAndNonPositiveAmounts() {
        listOf("", " ", "0", "000", "-1", "+1", "1.5", "1,5", " 5", "5 ", "1e2", "١").forEach { input ->
            ReminderUnit.entries.forEach { unit -> assertNull("$input $unit", customReminderMinutes(input, unit)) }
        }
    }

    @Test
    fun acceptsMaximumInEachUnitWithoutClippingAndRejectsTheNextAmount() {
        ReminderUnit.entries.forEach { unit ->
            val maximum = Int.MAX_VALUE / unit.minutes
            assertEquals(maximum * unit.minutes, customReminderMinutes(maximum.toString(), unit))
            assertNull(customReminderMinutes((maximum.toLong() + 1).toString(), unit))
            assertNull(customReminderMinutes(Long.MAX_VALUE.toString(), unit))
            assertNull(customReminderMinutes("999999999999999999999999999999999999", unit))
        }
    }

    @Test
    fun existingOffsetsRoundTripWithoutRounding() {
        listOf(1, 17, 60, 61, 90, 120, 1440, 2880, 10080, Int.MAX_VALUE).forEach { minutes ->
            val (amount, unit) = reminderAmount(minutes)
            assertEquals(minutes, customReminderMinutes(amount.toString(), unit))
        }
        assertEquals(2 to ReminderUnit.DAYS, reminderAmount(2880))
        assertEquals(2 to ReminderUnit.HOURS, reminderAmount(120))
        assertEquals(61 to ReminderUnit.MINUTES, reminderAmount(61))
    }
}
