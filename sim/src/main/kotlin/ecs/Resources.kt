package starkraft.sim.ecs

class ResourceSystem(private val world: World) {
    fun stockpile(faction: Int): ResourceStockpile =
        world.stockpiles.getOrPut(faction) { ResourceStockpile() }

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
        return true
    }

    fun refund(faction: Int, minerals: Int, gas: Int = 0) {
        val stockpile = stockpile(faction)
        stockpile.minerals += minerals
        stockpile.gas += gas
    }
}
