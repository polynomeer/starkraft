package starkraft.sim.bench

import java.util.Arrays
import java.util.Random
import starkraft.sim.data.DataRepo
import starkraft.sim.ecs.*
import starkraft.sim.ecs.path.PathPool
import starkraft.sim.ecs.path.PathRequestQueue
import starkraft.sim.ecs.path.Pathfinder
import starkraft.sim.ecs.path.PathfindingSystem
import starkraft.sim.ecs.services.FogGrid
import kotlin.math.floor

object Benchmark {
    @JvmStatic
    fun main(args: Array<String>) {
        val ticksToMeasure = 10_000
        val warmupTicks = 1_000
        val rng = Random(1234L)

        val unitsResource = object {}.javaClass.getResource("/data/units.json")
            ?: error("Resource '/data/units.json' not found. Ensure it exists in the resources directory.")
        val weaponsResource = object {}.javaClass.getResource("/data/weapons.json")
            ?: error("Resource '/data/weapons.json' not found. Ensure it exists in the resources directory.")
        val data = DataRepo(unitsResource.readText(), weaponsResource.readText())

        val world = World()
        val map = MapGrid(32, 32)
        val occ = OccupancyGrid(32, 32)
        val pathfinder = Pathfinder(map, occ, allowCornerCut = false)
        val pathPool = PathPool(map.width * map.height)
        val pathQueue = PathRequestQueue(256, 50)
        val pathing = PathfindingSystem(world, pathfinder, pathPool, pathQueue, nodesBudgetPerTick = 2000)
        val movement = MovementSystem(world, map, occ, pathPool, pathQueue)
        val occupancy = OccupancySystem(world, occ)
        val alive = AliveSystem(world)
        val combat = CombatSystem(world, data)
        val fog1 = FogGrid(64, 64, 0.25f)
        val fog2 = FogGrid(64, 64, 0.25f)
        val vision = VisionSystem(world, fog1, fog2)

        // Obstacles and rough terrain
        for (x in 6..24) map.setBlocked(x, 14, true)
        for (y in 6..14) map.setBlocked(12, y, true)
        for (x in 18..22) for (y in 18..22) map.setCost(x, y, 3f)

        // Spawn units
        repeat(20) { i ->
            val fx = 2f + rng.nextInt(28) + rng.nextFloat()
            val fy = 2f + rng.nextInt(28) + rng.nextFloat()
            val faction = if (i % 2 == 0) 1 else 2
            val type = if (faction == 1) "Marine" else "Zergling"
            val weapon = if (faction == 1) "Gauss" else "Claw"
            val id = world.spawn(Transform(fx, fy), UnitTag(faction, type), Health(40, 40), WeaponRef(weapon))
            world.visions[id] = Vision(if (faction == 1) 7f else 6f)
        }

        // Issue periodic move orders to stress pathing
        val ids = world.transforms.keys.toIntArray()
        val targets = Array(5) { Pair(FloatArray(ids.size), FloatArray(ids.size)) }
        for (p in targets.indices) {
            for (i in ids.indices) {
                targets[p].first[i] = 1f + rng.nextInt(30) + rng.nextFloat()
                targets[p].second[i] = 1f + rng.nextInt(30) + rng.nextFloat()
            }
        }

        var tick = 0
        val samples = LongArray(ticksToMeasure)
        val replans = IntArray(ticksToMeasure)
        val replansBlocked = IntArray(ticksToMeasure)
        val replansStuck = IntArray(ticksToMeasure)
        val avgPathLens = FloatArray(ticksToMeasure)
        val queueSizes = IntArray(ticksToMeasure)
        val nodesUsed = IntArray(ticksToMeasure)
        val budgetExhausted = IntArray(ticksToMeasure)
        val carryOver = IntArray(ticksToMeasure)
        while (tick < warmupTicks + ticksToMeasure) {
            if (tick % 500 == 0) {
                val phase = (tick / 500) % targets.size
                for (i in ids.indices) {
                    world.orders[ids[i]]?.items?.addLast(Order.Move(targets[phase].first[i], targets[phase].second[i]))
                }
            }

            val t0 = System.nanoTime()
            alive.tick()
            occupancy.tick()
            pathing.tick()
            movement.tick()
            combat.tick()
            vision.tick()
            val t1 = System.nanoTime()

            if (tick >= warmupTicks) {
                samples[tick - warmupTicks] = t1 - t0
                replans[tick - warmupTicks] = movement.lastTickReplans
                replansBlocked[tick - warmupTicks] = movement.lastTickReplansBlocked
                replansStuck[tick - warmupTicks] = movement.lastTickReplansStuck
                avgPathLens[tick - warmupTicks] = pathing.lastTickAvgPathLen
                queueSizes[tick - warmupTicks] = pathQueue.size
                nodesUsed[tick - warmupTicks] = pathing.lastTickNodesUsed
                budgetExhausted[tick - warmupTicks] = pathing.lastTickBudgetExhausted
                carryOver[tick - warmupTicks] = pathing.lastTickCarryOver
            }
            tick++
        }

        val sorted = samples.copyOf()
        Arrays.sort(sorted)
        val p50 = percentile(sorted, 50.0)
        val p95 = percentile(sorted, 95.0)
        val p99 = percentile(sorted, 99.0)
        val max = sorted[sorted.size - 1]
        val replanP50 = percentileInt(replans, 50.0)
        val replanP95 = percentileInt(replans, 95.0)
        val blockedP50 = percentileInt(replansBlocked, 50.0)
        val blockedP95 = percentileInt(replansBlocked, 95.0)
        val stuckP50 = percentileInt(replansStuck, 50.0)
        val stuckP95 = percentileInt(replansStuck, 95.0)
        val avgLenP50 = percentileFloat(avgPathLens, 50.0)
        val avgLenP95 = percentileFloat(avgPathLens, 95.0)
        val queueP50 = percentileInt(queueSizes, 50.0)
        val queueP95 = percentileInt(queueSizes, 95.0)
        val nodesP50 = percentileInt(nodesUsed, 50.0)
        val nodesP95 = percentileInt(nodesUsed, 95.0)
        val nodesP99 = percentileInt(nodesUsed, 99.0)
        val exhaustedP95 = percentileInt(budgetExhausted, 95.0)
        val carryP95 = percentileInt(carryOver, 95.0)

        println("Benchmark ticks=$ticksToMeasure warmup=$warmupTicks")
        println(
            "p50=${nsToMicros(p50)}us p95=${nsToMicros(p95)}us p99=${nsToMicros(p99)}us max=${nsToMicros(max)}us " +
                "replan_p50=$replanP50 replan_p95=$replanP95 " +
                "blocked_p50=$blockedP50 blocked_p95=$blockedP95 " +
                "stuck_p50=$stuckP50 stuck_p95=$stuckP95 " +
                "avglen_p50=${"%.2f".format(avgLenP50)} avglen_p95=${"%.2f".format(avgLenP95)} " +
                "queue_p50=$queueP50 queue_p95=$queueP95 " +
                "nodes_p50=$nodesP50 nodes_p95=$nodesP95 nodes_p99=$nodesP99 nodeBudget=${pathing.nodeBudgetPerTick} " +
                "budgetExhausted_p95=$exhaustedP95 carryOver_p95=$carryP95"
        )
    }
}

private fun percentile(sorted: LongArray, p: Double): Long {
    if (sorted.isEmpty()) return 0L
    val rank = p / 100.0 * (sorted.size - 1)
    val idx = floor(rank).toInt().coerceIn(0, sorted.size - 1)
    return sorted[idx]
}

private fun nsToMicros(ns: Long): Long = ns / 1_000L

private fun percentileInt(values: IntArray, p: Double): Int {
    if (values.isEmpty()) return 0
    val copy = values.copyOf()
    Arrays.sort(copy)
    val rank = p / 100.0 * (copy.size - 1)
    val idx = floor(rank).toInt().coerceIn(0, copy.size - 1)
    return copy[idx]
}

private fun percentileFloat(values: FloatArray, p: Double): Float {
    if (values.isEmpty()) return 0f
    val copy = values.copyOf()
    java.util.Arrays.sort(copy)
    val rank = p / 100.0 * (copy.size - 1)
    val idx = kotlin.math.floor(rank).toInt().coerceIn(0, copy.size - 1)
    return copy[idx]
}
