package jp.cjbc.kakeibo.util

import jp.cjbc.kakeibo.currentDate
import kotlin.random.Random

/** Generates an ID such as `20250101-123456` from today's date and a random number. */
fun generateId(): String = "${currentDate().replace("-", "")}-${Random.nextInt(100000, 999999)}"
