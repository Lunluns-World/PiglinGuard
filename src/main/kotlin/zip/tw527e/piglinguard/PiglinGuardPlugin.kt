package zip.tw527e.piglinguard

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.concurrent.TimeUnit

class PiglinGuardPlugin : JavaPlugin() {
    @Volatile
    var settings: Settings = Settings.DEFAULT
        private set

    lateinit var areas: AreaStore
        private set
    lateinit var densityCache: PortalDensityCache
        private set
    val stats = Stats()

    override fun onEnable() {
        areas = AreaStore(File(dataFolder, "areas.yml")) { logger.warning(it) }
        densityCache = PortalDensityCache(this)
        reloadAll()

        val commands = PiglinGuardCommands(this)
        server.pluginManager.registerEvents(PortalListener(this), this)
        server.pluginManager.registerEvents(CacheListener(this), this)
        server.pluginManager.registerEvents(object : Listener {
            @EventHandler
            fun onQuit(e: PlayerQuitEvent) = commands.forget(e.player.uniqueId)
        }, this)

        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(commands.build(), "PiglinGuard 管理指令", listOf("pg"))
        }
    }

    override fun onDisable() {
        if (::densityCache.isInitialized) densityCache.clear()
    }

    /** Reloads config.yml and areas.yml. Returns the warnings (also logged). */
    fun reloadAll(): List<String> {
        val warnings = mutableListOf<String>()
        val warn: (String) -> Unit = { warnings += it; logger.warning(it) }

        saveDefaultConfig()
        if (!File(dataFolder, "areas.yml").exists()) saveResource("areas.yml", false)

        reloadConfig()
        val cfg = config
        val defaults = cfg.defaults
        if (defaults != null) {
            val missing = defaults.getKeys(true).filter { !defaults.isConfigurationSection(it) && !cfg.contains(it, true) }
            if (missing.isNotEmpty()) {
                cfg.options().copyDefaults(true)
                saveConfig()
                logger.info("config.yml: filled missing settings with defaults: ${missing.joinToString()}")
            }
        }
        val s = Settings.parse(cfg, warn)
        settings = s
        densityCache.ttlNanos = TimeUnit.MINUTES.toNanos(s.cacheTtlMinutes.toLong())

        try {
            areas.load()
        } catch (e: Exception) {
            warn("areas.yml 讀取失敗：${e.message}")
        }
        return warnings
    }
}
