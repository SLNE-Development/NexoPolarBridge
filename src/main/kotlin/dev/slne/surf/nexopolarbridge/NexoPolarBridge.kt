package dev.slne.surf.nexopolarbridge

import com.nexomc.nexo.api.NexoFurniture
import com.nexomc.nexo.api.events.NexoItemsLoadedEvent
import com.nexomc.nexo.mechanics.furniture.FurnitureFactory
import com.nexomc.nexo.mechanics.furniture.FurnitureMechanic
import com.nexomc.nexo.mechanics.furniture.IFurniturePacketManager
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.ItemDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class NexoPolarBridge : JavaPlugin(), Listener {

    private val chunkRadius = 1
    private val periodMiliseconds = 1800L

    private var furnitureUpdateTask: ScheduledTask? = null

    private data class ChunkKey(val worldId: UUID, val x: Int, val z: Int)

    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)
        logger.info("NexoPolarBridge has been enabled")
    }

    override fun onDisable() {
        furnitureUpdateTask?.cancel()
        furnitureUpdateTask = null
        logger.info("NexoPolarBridge has been disabled")
    }

    @EventHandler
    fun onNexoItemsLoaded(event: NexoItemsLoadedEvent) {
        furnitureUpdateTask?.cancel()
        furnitureUpdateTask = null

        val furnitureFactory = FurnitureFactory.instance()
        if (furnitureFactory == null) {
            logger.warning("FurnitureFactory.instance() returned null even after NexoItemsLoadedEvent - not starting task.")
            return
        }

        val packetManager: IFurniturePacketManager = furnitureFactory.packetManager()

        furnitureUpdateTask = Bukkit.getAsyncScheduler().runAtFixedRate(this, { _ ->

            val chunkToTrackedPlayerUuids = ConcurrentHashMap<ChunkKey, MutableSet<UUID>>()
            val scheduledChunks = ConcurrentHashMap.newKeySet<ChunkKey>()

            for (player in Bukkit.getOnlinePlayers()) {

                // run player logic on the players region thread
                player.scheduler.run(this, { _ ->
                    val world = player.world
                    val playerChunkX = player.location.blockX shr 4
                    val playerChunkZ = player.location.blockZ shr 4

                    // scan only relevant chunks
                    for (chunkOffsetX in -chunkRadius..chunkRadius) {
                        for (chunkOffsetZ in -chunkRadius..chunkRadius) {
                            val chunkX = playerChunkX + chunkOffsetX
                            val chunkZ = playerChunkZ + chunkOffsetZ

                            val chunkKey = ChunkKey(world.uid, chunkX, chunkZ)

                            val trackedPlayerUuids = chunkToTrackedPlayerUuids.computeIfAbsent(chunkKey) { ConcurrentHashMap.newKeySet() }
                            trackedPlayerUuids.add(player.uniqueId)

                            if (scheduledChunks.add(chunkKey)) {
                                scheduleChunkProcess(
                                    world = world,
                                    chunkX = chunkX,
                                    chunkZ = chunkZ,
                                    chunkKey = chunkKey,
                                    chunkToTrackedPlayerUuids = chunkToTrackedPlayerUuids,
                                    packetManager = packetManager
                                )
                            }
                        }
                    }
                }, null)
            }

        }, 20L, periodMiliseconds, TimeUnit.MILLISECONDS)

        logger.info("Started hitbox resend task (radius=$chunkRadius chunks, every ${periodMiliseconds}s)")
    }

    private fun scheduleChunkProcess(
        world: World,
        chunkX: Int,
        chunkZ: Int,
        chunkKey: ChunkKey,
        chunkToTrackedPlayerUuids: ConcurrentHashMap<ChunkKey, MutableSet<UUID>>,
        packetManager: IFurniturePacketManager
    ) {
        Bukkit.getRegionScheduler().run(this, world, chunkX, chunkZ) {
            // skip unloaded chunks, just to be sure
            if (!world.isChunkLoaded(chunkX, chunkZ)) return@run
            val chunk = world.getChunkAt(chunkX, chunkZ)

            // get tracked players for that chunk
            val trackedPlayerUuids = chunkToTrackedPlayerUuids[chunkKey] ?: return@run
            if (trackedPlayerUuids.isEmpty()) return@run

            val trackedPlayers = trackedPlayerUuids.mapNotNull { trackedPlayerUuid ->
                val trackedPlayer = Bukkit.getPlayer(trackedPlayerUuid)
                if (trackedPlayer != null && trackedPlayer.isOnline && trackedPlayer.world.uid == world.uid) {
                    trackedPlayer
                } else {
                    null
                }
            }
            if (trackedPlayers.isEmpty()) return@run

            // process furniture entities in chunk
            for (entity in chunk.entities) {
                val itemDisplay = entity as? ItemDisplay ?: continue
                val furnitureMechanic: FurnitureMechanic = NexoFurniture.furnitureMechanic(itemDisplay) ?: continue

                for (trackedPlayer in trackedPlayers) {
                    packetManager.sendBarrierHitboxPacket(itemDisplay, furnitureMechanic, trackedPlayer)
                }
            }
        }
    }
}