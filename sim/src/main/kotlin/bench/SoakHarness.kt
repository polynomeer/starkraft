package starkraft.sim.bench

import java.lang.management.ManagementFactory
import java.util.Arrays
import java.util.Random
import kotlin.math.floor
import starkraft.sim.data.DataRepo
import starkraft.sim.ecs.AliveSystem
import starkraft.sim.ecs.CombatSystem
import starkraft.sim.ecs.Health
import starkraft.sim.ecs.MapGrid
import starkraft.sim.ecs.MovementSystem
import starkraft.sim.ecs.OccupancyGrid
import starkraft.sim.ecs.OccupancySystem
import starkraft.sim.ecs.Order
import starkraft.sim.ecs.Transform
import starkraft.sim.ecs.UnitTag
import starkraft.sim.ecs.Vision
import starkraft.sim.ecs.VisionSystem
import starkraft.sim.ecs.WeaponRef
import starkraft.sim.ecs.World
import starkraft.sim.ecs.path.PathPool
import starkraft.sim.ecs.path.PathRequestQueue
import starkraft.sim.ecs.path.Pathfinder
import starkraft.sim.ecs.path.PathfindingSystem
import starkraft.sim.ecs.services.FogGrid

object SoakHarness {
    @JvmStatic
    fun main(args: Array<String>) {
        val minutes = parseIntArg(args, "--minutes") ?: 30
        val units = parseIntArg(args, "--units") ?: 120
        val seed = parseLongArg(args, "--seed") ?: 1234L
        val reportEverySeconds = parseIntArg(args, "--reportEverySec") ?: 10
        val tickRate = 50
        val totalTicks = (minutes * 60 * tickRate).coerceAtLeast(1)
        val reportEveryTicks = (reportEverySeconds * tickRate).coerceAtLeast(1)
        val rng = Random(seed)

        val unitsResource = object {}.javaClass.getResource("/data/units.json")
            ?: error("Resource '/data/units.json' not found.")
        val weaponsResource = object {}.javaClass.getResource("/data/weapons.json")
            ?: error("Resource '/data/weapons.json' not found.")
        val data = DataRepo(unitsResource.readText(), weaponsResource.readText())

        val world = World()
        val map = MapGrid(64, 64)
        val occ = OccupancyGrid(64, 64)
        val pathfinder = Pathfinder(map, occ, allowCornerCut = false)
        val pathPool = PathPool(map.width * map.height)
        val pathQueue = PathRequestQueue(512, 100)
        val pathing = PathfindingSystem(world, pathfinder, pathPool, pathQueue, nodesBudgetPerTick = 5000)
        val movement = MovementSystem(world, map, occ, pathPool, pathQueue)
        val occupancy = OccupancySystem(world, occ)
        val alive = AliveSystem(world)
        val combat = CombatSystem(world, data)
        val fog1 = FogGrid(128, 128, 0.5f)
        val fog2 = FogGrid(128, 128, 0.5f)
        val vision = VisionSystem(world, fog1, fog2)

        for (x in 10..50) map.setBlocked(x, 30, true)
        for (y in 5..35) map.setBlocked(20, y, true)
        for (x in 35..45) for (y in 35..45) map.setCost(x, y, 3f)

        repeat(units) { i ->
            val faction = if (i % 2 == 0) 1 else 2
            val type = if (faction == 1) "Marine" else "Zergling"
            val weapon = if (faction == 1) "Gauss" else "Claw"
            val id = world.spawn(
                Transform(2f + rng.nextInt(60) + rng.nextFloat(), 2f + rng.nextInt(60) + rng.nextFloat()),
                UnitTag(faction, type),
                Health(45, 45),
                WeaponRef(weapon)
            )
            world.visions[id] = Vision(if (faction == 1) 7f else 6f)
        }

        val ids = world.transforms.keys.toIntArray()
        val tickSamples = LongArray(totalTicks)
        val nodeSamples = IntArray(totalTicks)
        val carrySamples = IntArray(totalTicks)
        val queueSamples = IntArray(totalTicks)
        var maxUsedHeap = 0L
        val runtime = Runtime.getRuntime()
        val gcBeforeCount = gcCount()
        val gcBeforeTime = gcTimeMs()

        for (tick in 0 until totalTicks) {
            if (tick % 150 == 0) {
                for (id in ids) {
                    world.orders[id]?.items?.addLast(Order.Move(1f + rng.nextInt(62), 1f + rng.nextInt(62)))
                }
            }

            val start = System.nanoTime()
            alive.tick()
            occupancy.tick()
            pathing.tick()
            movement.tick()
            combat.tick()
            vision.tick()
            val end = System.nanoTime()

            tickSamples[tick] = end - start
            nodeSamples[tick] = pathing.lastTickNodesUsed
            carrySamples[tick] = pathing.lastTickCarryOver
            queueSamples[tick] = pathQueue.size
            val usedHeap = runtime.totalMemory() - runtime.freeMemory()
            if (usedHeap > maxUsedHeap) maxUsedHeap = usedHeap

            if ((tick + 1) % reportEveryTicks == 0 || tick == totalTicks - 1) {
                val done = tick + 1
                val pct = (done * 100) / totalTicks
                val sampleSlice = tickSamples.copyOf(done)
                Arrays.sort(sampleSlice)
                val p95SoFar = percentile(sampleSlice, sampleSlice.size, 95.0)
                println(
                    "soak progress=$done/$totalTicks ${pct}% " +
                        "tick_p95=${nsToMicros(p95SoFar)}us " +
                        "heapUsed=${toMiB(usedHeap)}MiB maxHeap=${toMiB(maxUsedHeap)}MiB"
                )
            }
        }

        val sorted = tickSamples.copyOf()
        Arrays.sort(sorted)
        val p50 = percentile(sorted, sorted.size, 50.0)
        val p95 = percentile(sorted, sorted.size, 95.0)
        val p99 = percentile(sorted, sorted.size, 99.0)
        val max = sorted[sorted.size - 1]
        val nodesP95 = percentileInt(nodeSamples, 95.0)
        val nodesP99 = percentileInt(nodeSamples, 99.0)
        val carryP95 = percentileInt(carrySamples, 95.0)
        val queueP95 = percentileInt(queueSamples, 95.0)
        val gcDeltaCount = gcCount() - gcBeforeCount
        val gcDeltaTime = gcTimeMs() - gcBeforeTime

        println("Soak complete minutes=$minutes ticks=$totalTicks units=$units seed=$seed")
        println(
            "tick_us p50=${nsToMicros(p50)} p95=${nsToMicros(p95)} p99=${nsToMicros(p99)} max=${nsToMicros(max)} " +
                "pathNodes_p95=$nodesP95 pathNodes_p99=$nodesP99 nodeBudget=${pathing.nodeBudgetPerTick} " +
                "queue_p95=$queueP95 carry_p95=$carryP95"
        )
        println("memory maxUsedMiB=${toMiB(maxUsedHeap)} gcCountDelta=$gcDeltaCount gcTimeDeltaMs=$gcDeltaTime")
        val pid = ProcessHandle.current().pid()
        val jfrDurationMinutes = minutes.coerceAtLeast(1)
        println(
            "jfr hint: jcmd $pid JFR.start name=starkraft-soak settings=profile " +
                "filename=/tmp/starkraft-soak.jfr duration=${jfrDurationMinutes}m"
        )
    }
}

private fun percentile(sortedOrSlice: LongArray, size: Int, p: Double): Long {
    if (size <= 0) return 0L
    val rank = p / 100.0 * (size - 1)
    val idx = floor(rank).toInt().coerceIn(0, size - 1)
    return sortedOrSlice[idx]
}

private fun percentileInt(values: IntArray, p: Double): Int {
    if (values.isEmpty()) return 0
    val copy = values.copyOf()
    Arrays.sort(copy)
    val rank = p / 100.0 * (copy.size - 1)
    val idx = floor(rank).toInt().coerceIn(0, copy.size - 1)
    return copy[idx]
}

private fun nsToMicros(ns: Long): Long = ns / 1_000L

private fun toMiB(bytes: Long): Long = bytes / (1024L * 1024L)

private fun gcCount(): Long =
    ManagementFactory.getGarbageCollectorMXBeans().sumOf { bean ->
        if (bean.collectionCount >= 0) bean.collectionCount else 0L
    }

private fun gcTimeMs(): Long =
    ManagementFactory.getGarbageCollectorMXBeans().sumOf { bean ->
        if (bean.collectionTime >= 0) bean.collectionTime else 0L
    }

private fun parseIntArg(args: Array<String>, name: String): Int? {
    val idx = args.indexOf(name)
    if (idx < 0) return null
    val raw = args.getOrNull(idx + 1) ?: error("Missing value for $name")
    return raw.toIntOrNull() ?: error("Invalid integer for $name: '$raw'")
}

private fun parseLongArg(args: Array<String>, name: String): Long? {
    val idx = args.indexOf(name)
    if (idx < 0) return null
    val raw = args.getOrNull(idx + 1) ?: error("Missing value for $name")
    return raw.toLongOrNull() ?: error("Invalid long for $name: '$raw'")
}
