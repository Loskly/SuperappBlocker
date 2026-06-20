package com.ecosentinel.appblocker.alarm

import kotlin.random.Random

data class MathChallenge(
    val question: String,
    val answer: Int
)

object MathChallengeGenerator {

    fun generate(difficulty: AlarmChallengeDifficulty): MathChallenge {
        return when (difficulty) {
            AlarmChallengeDifficulty.EASY -> generateEasy()
            AlarmChallengeDifficulty.MEDIUM -> generateMedium()
            AlarmChallengeDifficulty.HARD -> generateHard()
        }
    }

    private fun generateEasy(): MathChallenge {
        val a = Random.nextInt(2, 12)
        val b = Random.nextInt(2, 12)
        val useAddition = Random.nextBoolean()
        return if (useAddition) {
            MathChallenge("$a + $b = ?", a + b)
        } else {
            val left = maxOf(a, b)
            val right = minOf(a, b)
            MathChallenge("$left − $right = ?", left - right)
        }
    }

    private fun generateMedium(): MathChallenge {
        return when (Random.nextInt(3)) {
            0 -> {
                val a = Random.nextInt(10, 30)
                val b = Random.nextInt(2, 10)
                MathChallenge("$a × $b = ?", a * b)
            }
            1 -> {
                val a = Random.nextInt(15, 50)
                val b = Random.nextInt(5, 30)
                val c = Random.nextInt(2, 15)
                MathChallenge("$a + $b − $c = ?", a + b - c)
            }
            else -> {
                val a = Random.nextInt(20, 60)
                val b = Random.nextInt(10, 40)
                MathChallenge("$a − $b = ?", a - b)
            }
        }
    }

    private fun generateHard(): MathChallenge {
        return when (Random.nextInt(3)) {
            0 -> {
                val a = Random.nextInt(12, 25)
                val b = Random.nextInt(6, 15)
                val c = Random.nextInt(3, 12)
                MathChallenge("($a + $b) × $c = ?", (a + b) * c)
            }
            1 -> {
                val a = Random.nextInt(30, 80)
                val b = Random.nextInt(10, 40)
                val c = Random.nextInt(5, 25)
                MathChallenge("$a + $b − $c = ?", a + b - c)
            }
            else -> {
                val a = Random.nextInt(15, 35)
                val b = Random.nextInt(4, 12)
                val product = a * b
                val c = Random.nextInt(10, product - 1)
                MathChallenge("$product − $c = ?", product - c)
            }
        }
    }
}
