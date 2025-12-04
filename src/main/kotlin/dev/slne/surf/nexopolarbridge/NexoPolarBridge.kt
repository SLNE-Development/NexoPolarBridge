package dev.slne.surf.nexopolarbridge

import com.nexomc.nexo.api.NexoFurniture
import com.nexomc.nexo.api.events.NexoItemsLoadedEvent
import com.nexomc.nexo.mechanics.furniture.FurnitureFactory
import com.nexomc.nexo.mechanics.furniture.FurnitureMechanic
import com.nexomc.nexo.mechanics.furniture.IFurniturePacketManager
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.entity.ItemDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.TimeUnit

class NexoPolarBridge : JavaPlugin(), Listener {

    private val chunkRadius = 1
    private val periodSeconds = 2L

    private var furnitureUpdateTask: ScheduledTask? = null

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

        val factory = FurnitureFactory.instance()
        if (factory == null) {
            logger.warning("FurnitureFactory.instance() returned null even after NexoItemsLoadedEvent - not starting task.")
            return
        }

        val packetManager: IFurniturePacketManager = factory.packetManager()

        furnitureUpdateTask = Bukkit.getAsyncScheduler().runAtFixedRate(this, { _ ->

            for (player in Bukkit.getOnlinePlayers()) {

                // run player logic on the players region thread
                player.scheduler.run(this, { _ ->
                    val world = player.world
                    val pcx = player.location.blockX shr 4
                    val pcz = player.location.blockZ shr 4

                    // scan only relevant chunks
                    for (dx in -chunkRadius..chunkRadius) {
                        for (dz in -chunkRadius..chunkRadius) {
                            val cx = pcx + dx
                            val cz = pcz + dz

                            // skip unloaded chunks, just to be sure
                            if (!world.isChunkLoaded(cx, cz)) continue
                            val chunk = world.getChunkAt(cx, cz)

                            // process furniture entities in chunk
                            for (e in chunk.entities) {
                                val display = e as? ItemDisplay ?: continue
                                val mechanic: FurnitureMechanic = NexoFurniture.furnitureMechanic(display) ?: continue

                                packetManager.sendBarrierHitboxPacket(display, mechanic, player)
                            }
                        }
                    }
                }, null)
            }

        }, 20L, periodSeconds, TimeUnit.SECONDS)

        logger.info("Started hitbox resend task (radius=$chunkRadius chunks, every ${periodSeconds}s)")
    }
}