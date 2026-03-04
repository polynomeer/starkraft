package starkraft.sim.client

import starkraft.sim.data.DataRepo

internal data class ClientBuildCatalogEntry(
    val typeId: String,
    val label: String,
    val width: Int,
    val height: Int,
    val clearance: Int,
    val mineralCost: Int,
    val gasCost: Int
)

internal data class ClientQueueCatalogEntry(
    val typeId: String,
    val label: String
)

internal data class ClientCatalog(
    val buildOptions: List<ClientBuildCatalogEntry>,
    val trainOptions: List<ClientQueueCatalogEntry>,
    val researchOptions: List<ClientQueueCatalogEntry>
)

private val defaultClientCatalogValue: ClientCatalog by lazy {
    val unitsResource =
        object {}.javaClass.getResource("/data/units.json")
            ?: error("Resource '/data/units.json' not found")
    val weaponsResource =
        object {}.javaClass.getResource("/data/weapons.json")
            ?: error("Resource '/data/weapons.json' not found")
    val buildingsResource =
        object {}.javaClass.getResource("/data/buildings.json")
            ?: error("Resource '/data/buildings.json' not found")
    val techsResource =
        object {}.javaClass.getResource("/data/techs.json")
            ?: error("Resource '/data/techs.json' not found")
    buildClientCatalog(
        DataRepo(
            unitsResource.readText(),
            weaponsResource.readText(),
            buildingsResource.readText(),
            techsResource.readText()
        )
    )
}

internal fun defaultClientCatalog(): ClientCatalog = defaultClientCatalogValue

internal fun buildClientCatalog(data: DataRepo): ClientCatalog =
    ClientCatalog(
        buildOptions =
            sequenceOf("Depot", "ResourceDepot", "GasDepot")
                .mapNotNull { id ->
                    data.buildSpec(id)?.let { spec ->
                        ClientBuildCatalogEntry(
                            typeId = spec.typeId,
                            label = spec.typeId,
                            width = spec.footprintWidth,
                            height = spec.footprintHeight,
                            clearance = spec.placementClearance,
                            mineralCost = spec.mineralCost,
                            gasCost = spec.gasCost
                        )
                    }
                }
                .toList(),
        trainOptions =
            sequenceOf("Worker", "Marine", "Zergling")
                .mapNotNull { id ->
                    data.trainSpec(id)?.let { ClientQueueCatalogEntry(typeId = it.typeId, label = it.typeId) }
                }
                .toList(),
        researchOptions =
            sequenceOf("AdvancedTraining")
                .mapNotNull { id ->
                    data.researchSpec(id)?.let { ClientQueueCatalogEntry(typeId = it.techId, label = it.techId) }
                }
                .toList()
    )
