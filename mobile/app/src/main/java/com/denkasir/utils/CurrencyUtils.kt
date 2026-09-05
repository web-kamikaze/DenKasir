package com.denkasir.utils

import java.text.NumberFormat
import java.util.Locale

object CurrencyUtils {
    private val localeID = Locale("in", "ID")
    private val numberFormat = NumberFormat.getNumberInstance(localeID)

    fun formatRupiah(amount: Double): String {
        return "Rp " + numberFormat.format(amount)
    }

    fun formatNumberOnly(amount: Double): String {
        return numberFormat.format(amount)
    }
}
