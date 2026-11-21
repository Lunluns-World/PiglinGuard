package zip.tw527e.piglinguard

import org.bukkit.configuration.ConfigurationSection

data class Settings(
    val worlds: Set<String> = setOf("world"),
    val autoDetectEnabled: Boolean = true,
    val radius: Int = 24,
    val threshold: Int = 1000,
    val requirePortalSpawn: Boolean = true,
    val cacheTtlMinutes: Int = 10,
    val logRemovals: Boolean = false,
) {
    val autoDetect: AutoDetect get() = AutoDetect(autoDetectEnabled, requirePortalSpawn, threshold)

    companion object {
        val DEFAULT = Settings()

        /** Largest vanilla portal: 21 x 21 blocks of portal. */
        const val MAX_VANILLA_PORTAL = 441
        const val MAX_RADIUS = 256

        /** Reads [cfg]; any missing or invalid value falls back to [DEFAULT] (invalid ones are reported via [warn]). */
        fun parse(cfg: ConfigurationSection, warn: (String) -> Unit): Settings {
            val d = DEFAULT
            val worlds = when {
                !cfg.contains("worlds") -> d.worlds
                cfg.isList("worlds") -> cfg.getList("worlds")!!.filterIsInstance<String>().filter { it.isNotBlank() }.toSet()
                else -> {
                    warn("worlds 必須是清單，改用預設值 ${d.worlds}")
                    d.worlds
                }
            }
            val radius = int(cfg, "auto-detect.radius", d.radius, 0..MAX_RADIUS, warn)
            val threshold = int(cfg, "auto-detect.threshold", d.threshold, 1..Int.MAX_VALUE, warn)
            if (threshold <= MAX_VANILLA_PORTAL) {
                warn("auto-detect.threshold = $threshold ≤ $MAX_VANILLA_PORTAL：單一個大型原版傳送門就可能被當成金磚場")
            }
            return Settings(
                worlds = worlds,
                autoDetectEnabled = bool(cfg, "auto-detect.enabled", d.autoDetectEnabled, warn),
                radius = radius,
                threshold = threshold,
                requirePortalSpawn = bool(cfg, "auto-detect.require-portal-spawn", d.requirePortalSpawn, warn),
                cacheTtlMinutes = int(cfg, "auto-detect.cache-ttl-minutes", d.cacheTtlMinutes, 1..1440, warn),
                logRemovals = bool(cfg, "log-removals", d.logRemovals, warn),
            )
        }

        private fun bool(cfg: ConfigurationSection, path: String, def: Boolean, warn: (String) -> Unit): Boolean {
            if (!cfg.contains(path)) return def
            if (cfg.isBoolean(path)) return cfg.getBoolean(path)
            warn("$path 必須是 true/false，改用預設值 $def")
            return def
        }

        private fun int(cfg: ConfigurationSection, path: String, def: Int, range: IntRange, warn: (String) -> Unit): Int {
            if (!cfg.contains(path)) return def
            val value = if (cfg.isInt(path)) cfg.getInt(path) else null
            if (value == null || value !in range) {
                warn("$path 必須是 ${range.first}..${range.last} 之間的整數（目前：${cfg.get(path)}），改用預設值 $def")
                return def
            }
            return value
        }
    }
}
