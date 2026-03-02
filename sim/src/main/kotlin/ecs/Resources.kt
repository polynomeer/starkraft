package starkraft.sim.ecs

class ResourceSystem(private val world: World) {
    private var eventFactions = IntArray(8)
    private var eventKinds = ByteArray(8)
    private var eventMinerals = IntArray(8)
    private var eventGas = IntArray(8)
    var lastTickEventCount: Int = 0
        private set

    fun stockpile(faction: Int): ResourceStockpile =
        world.stockpiles.getOrPut(faction) { ResourceStockpile() }

    fun clearTickEvents() {
        lastTickEventCount = 0
    }

    fun set(faction: Int, minerals: Int, gas: Int = 0) {
        val stockpile = stockpile(faction)
        stockpile.minerals = minerals
        stockpile.gas = gas
    }

    fun canAfford(faction: Int, minerals: Int, gas: Int = 0): Boolean {
        val stockpile = stockpile(faction)
        return stockpile.minerals >= minerals && stockpile.gas >= gas
    }

    fun spend(faction: Int, minerals: Int, gas: Int = 0): Boolean {
        if (!canAfford(faction, minerals, gas)) return false
        val stockpile = stockpile(faction)
        stockpile.minerals -= minerals
        stockpile.gas -= gas
        recordEvent(EVENT_SPEND, faction, minerals, gas)
        return true
    }

    fun refund(faction: Int, minerals: Int, gas: Int = 0) {
        val stockpile = stockpile(faction)
        stockpile.minerals += minerals
        stockpile.gas += gas
        recordEvent(EVENT_REFUND, faction, minerals, gas)
    }

    fun eventFaction(index: Int): Int = eventFactions[index]

    fun eventKind(index: Int): Byte = eventKinds[index]

    fun eventMinerals(index: Int): Int = eventMinerals[index]

    fun eventGas(index: Int): Int = eventGas[index]

    private fun recordEvent(kind: Byte, faction: Int, minerals: Int, gas: Int) {
        val index = lastTickEventCount
        if (index >= eventKinds.size) {
            val nextSize = eventKinds.size * 2
            eventFactions = eventFactions.copyOf(nextSize)
            eventKinds = eventKinds.copyOf(nextSize)
            eventMinerals = eventMinerals.copyOf(nextSize)
            eventGas = eventGas.copyOf(nextSize)
        }
        eventFactions[index] = faction
        eventKinds[index] = kind
        eventMinerals[index] = minerals
        eventGas[index] = gas
        lastTickEventCount = index + 1
    }

    companion object {
        const val EVENT_SPEND: Byte = 1
        const val EVENT_REFUND: Byte = 2
    }
}
