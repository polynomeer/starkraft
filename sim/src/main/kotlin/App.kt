package starkraft.sim

import starkraft.sim.data.DataRepo
import starkraft.sim.ecs.*
import starkraft.sim.net.Command
import starkraft.sim.replay.NullRecorder

object Time {
    const val TICK_MS = 20
}

fun main() {
    // Load data resources
    val unitsResource = object {}.javaClass.getResource("/data/units.json")
        ?: error("Resource '/data/units.json' not found. Ensure it exists in the resources directory.")
    val weaponsResource = object {}.javaClass.getResource("/data/weapons.json")
        ?: error("Resource '/data/weapons.json' not found. Ensure it exists in the resources directory.")

    val unitsJson = unitsResource.readText()
    val weaponsJson = weaponsResource.readText()
    val data = DataRepo(unitsJson, weaponsJson)

    val world = World()
    val movement = MovementSystem(world)
    val combat = CombatSystem(world, data)

    // Spawn two tiny squads
    repeat(5) {
        world.spawn(Transform(2f + it * 0.2f, 2f), UnitTag(1, "Marine"), Health(45, 45), WeaponRef("Gauss"))
        world.spawn(Transform(10f - it * 0.2f, 10f), UnitTag(2, "Zergling"), Health(35, 35), WeaponRef("Claw"))
    }

    var tick = 0
    val recorder = NullRecorder()

    // Demo orders: make team 1 move toward center
    val team1 = world.tags.filter { it.value.faction == 1 }.keys.toIntArray()
    issue(Command.Move(0, team1, 6f, 6f), world, recorder)

    val start = System.currentTimeMillis()
    while (tick < 1500) { // ~30 seconds
        // Process queued orders already stuffed into world.orders by issue()
        movement.tick()
        combat.tick()

        if (tick % 25 == 0) {
            val m1 = world.tags.filter { it.value.faction == 1 }.keys.size
            val m2 = world.tags.filter { it.value.faction == 2 }.keys.size
            println("tick=$tick  alive: team1=$m1 team2=$m2")
        }

        tick++
        val targetNext = start + tick * Time.TICK_MS
        val sleep = targetNext - System.currentTimeMillis()
        if (sleep > 0) Thread.sleep(sleep)
    }
}

fun issue(cmd: Command, world: World, recorder: NullRecorder) {
    recorder.onCommand(cmd)
    when (cmd) {
        is Command.Move -> {
            cmd.units.forEach { id -> world.orders[id]?.items?.addLast(Order.Move(cmd.x, cmd.y)) }
        }

        is Command.Attack -> {
            cmd.units.forEach { id -> world.orders[id]?.items?.addLast(Order.Attack(cmd.target)) }
        }
    }
}
