package starkraft.sim.bench

import java.util.Arrays
import java.util.Random
import kotlin.math.floor
import starkraft.sim.ecs.MapGrid
import starkraft.sim.ecs.OccupancyGrid
import starkraft.sim.ecs.path.Pathfinder

object PathfindingMicroBenchmark {
    @JvmStatic
    fun main(args: Array<String>) {
        val rng = Random(1234L)
        val map = MapGrid(64, 64)
        val occ = OccupancyGrid(64, 64)
        val pathfinder = Pathfinder(map, occ, allowCornerCut = false)

        // Scatter obstacles
        repeat(400) {
            val x = rng.nextInt(map.width)
            val y = rng.nextInt(map.height)
            map.setBlocked(x, y, true)
        }

        val cases = 2000
        val starts = IntArray(cases * 2)
        val goals = IntArray(cases * 2)
        repeat(cases) { i ->
            starts[i * 2] = rng.nextInt(map.width)
            starts[i * 2 + 1] = rng.nextInt(map.height)
            goals[i * 2] = rng.nextInt(map.width)
            goals[i * 2 + 1] = rng.nextInt(map.height)
        }

        val out = IntArray(map.width * map.height)
        val budgets = intArrayOf(100, 300, 1000)
        for (budget in budgets) {
            val samples = LongArray(cases)
            var solved = 0
            for (i in 0 until cases) {
                val sx = starts[i * 2]
                val sy = starts[i * 2 + 1]
                val gx = goals[i * 2]
                val gy = goals[i * 2 + 1]
                val t0 = System.nanoTime()
                val len = pathfinder.findPath(sx, sy, gx, gy, budget, out)
                val t1 = System.nanoTime()
                samples[i] = t1 - t0
                if (len > 0) solved++
            }
            val sorted = samples.copyOf()
            Arrays.sort(sorted)
            val p50 = percentile(sorted, 50.0)
            val p95 = percentile(sorted, 95.0)
            println("budget=$budget cases=$cases solved=$solved p50=${nsToMicros(p50)}us p95=${nsToMicros(p95)}us")
        }
    }
}

private fun percentile(sorted: LongArray, p: Double): Long {
    if (sorted.isEmpty()) return 0L
    val rank = p / 100.0 * (sorted.size - 1)
    val idx = floor(rank).toInt().coerceIn(0, sorted.size - 1)
    return sorted[idx]
}

private fun nsToMicros(ns: Long): Long = ns / 1_000L
