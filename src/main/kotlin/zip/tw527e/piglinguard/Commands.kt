package zip.tw527e.piglinguard

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PiglinGuardCommands(private val plugin: PiglinGuardPlugin) {
    private class Corners(val pos1: Location? = null, val pos2: Location? = null)

    /** pos1 / pos2 per player, memory only. Shared between wl and bl. */
    private val corners = ConcurrentHashMap<UUID, Corners>()

    fun forget(player: UUID) {
        corners.remove(player)
    }

    fun build(): LiteralCommandNode<CommandSourceStack> {
        val root = Commands.literal("piglinguard")
            .requires { it.sender.hasPermission(PERMISSION) }
        for (kind in AreaKind.entries) root.then(areaBranch(kind))
        root.then(Commands.literal("list").executes(::list))
        root.then(Commands.literal("scan").executes(::scan))
        root.then(Commands.literal("stats").executes(::stats))
        root.then(Commands.literal("reload").executes(::reload))
        root.executes(::help)
        return root.build()
    }

    private fun areaBranch(kind: AreaKind): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(kind.command)
            .executes(::help)
            .then(Commands.literal("pos1").executes { setCorner(it, 1) })
            .then(Commands.literal("pos2").executes { setCorner(it, 2) })
            .then(
                Commands.literal("add").then(
                    Commands.argument("name", StringArgumentType.word())
                        .executes { addFromCorners(it, kind) }
                        .then(
                            Commands.argument("radius", IntegerArgumentType.integer(1, MAX_AREA_RADIUS))
                                .suggests { _, b -> listOf(16, 24, 32, 48, 64).forEach { r -> b.suggest(r) }; b.buildFuture() }
                                .executes { addAround(it, kind) },
                        ),
                ),
            )
            .then(
                Commands.literal("remove").then(
                    Commands.argument("name", StringArgumentType.word())
                        .suggests { _, b ->
                            val typed = b.remainingLowerCase
                            plugin.areas.current.of(kind).keys.filter { it.lowercase().startsWith(typed) }.sorted().forEach(b::suggest)
                            b.buildFuture()
                        }
                        .executes { remove(it, kind) },
                ),
            )

    private fun player(ctx: CommandContext<CommandSourceStack>): Player? {
        val p = ctx.source.executor as? Player ?: ctx.source.sender as? Player
        if (p == null) Msg.send(ctx.source.sender, "<red>(；´д｀) 這個指令只能由玩家在遊戲內使用喔")
        return p
    }

    private fun help(ctx: CommandContext<CommandSourceStack>): Int {
        val s = ctx.source.sender
        Msg.send(s, "<light_purple>(｡･ω･｡)ﾉ 指令列表")
        listOf(
            "/pg wl|bl add <名稱> <半徑>" to "以腳下為中心建立區域（全高度）",
            "/pg wl|bl pos1|pos2" to "記錄角落",
            "/pg wl|bl add <名稱>" to "用 pos1、pos2 建立區域",
            "/pg wl|bl remove <名稱>" to "刪除區域",
            "/pg list" to "列出所有區域",
            "/pg scan" to "檢查目前位置",
            "/pg stats" to "啟動以來的統計",
            "/pg reload" to "重新載入設定",
        ).forEach { (cmd, desc) -> Msg.line(s, " <gold><cmd></gold> <gray>- <desc>", "cmd" to cmd, "desc" to desc) }
        return Command.SINGLE_SUCCESS
    }

    private fun setCorner(ctx: CommandContext<CommandSourceStack>, which: Int): Int {
        val p = player(ctx) ?: return 0
        val loc = p.location.toBlockLocation()
        corners.compute(p.uniqueId) { _, old ->
            if (which == 1) Corners(loc, old?.pos2) else Corners(old?.pos1, loc)
        }
        Msg.send(
            p, "<green>(๑•̀ㅂ•́)و✧ 已記錄 pos<n> <gold><world> (<x>, <y>, <z>)",
            "n" to which, "world" to loc.world.name, "x" to loc.blockX, "y" to loc.blockY, "z" to loc.blockZ,
        )
        return Command.SINGLE_SUCCESS
    }

    private fun checkName(p: Player, name: String): Boolean {
        if (Area.isValidName(name)) return true
        Msg.send(p, "<red>(っ˘̩╭╮˘̩)っ 名稱只能用英文、數字、底線、減號（最多 32 字）")
        return false
    }

    private fun addFromCorners(ctx: CommandContext<CommandSourceStack>, kind: AreaKind): Int {
        val p = player(ctx) ?: return 0
        val name = StringArgumentType.getString(ctx, "name")
        if (!checkName(p, name)) return 0
        val c = corners[p.uniqueId]
        val a = c?.pos1
        val b = c?.pos2
        if (a == null || b == null) {
            Msg.send(p, "<yellow>(・_・;) 請先用 <gold>/pg ${kind.command} pos1</gold> 和 <gold>pos2</gold> 記錄兩個角落，或在名稱後面加上半徑")
            return 0
        }
        if (a.world != b.world) {
            Msg.send(p, "<red>(；´д｀) pos1 和 pos2 不在同一個世界")
            return 0
        }
        val area = Area.fromCorners(name, a.world.name, a.blockX, a.blockY, a.blockZ, b.blockX, b.blockY, b.blockZ)
        return saveArea(p, kind, area)
    }

    private fun addAround(ctx: CommandContext<CommandSourceStack>, kind: AreaKind): Int {
        val p = player(ctx) ?: return 0
        val name = StringArgumentType.getString(ctx, "name")
        if (!checkName(p, name)) return 0
        val radius = IntegerArgumentType.getInteger(ctx, "radius")
        val loc = p.location
        val w = loc.world
        val area = Area.around(name, w.name, loc.blockX, loc.blockZ, radius, w.minHeight, w.maxHeight)
        return saveArea(p, kind, area)
    }

    private fun saveArea(p: Player, kind: AreaKind, area: Area): Int {
        val replaced = area.name in plugin.areas.current.of(kind)
        try {
            plugin.areas.add(kind, area)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to save areas.yml: ${e.message}")
            Msg.send(p, "<red>(╥﹏╥) 寫入 areas.yml 失敗，請查看後台")
            return 0
        }
        Msg.send(
            p, "<green>(๑•̀ㅂ•́)و✧ 已<verb>${kind.display} <gold><name></gold> <gray><range>",
            "verb" to if (replaced) "更新" else "新增", "name" to area.name, "range" to area.describe(),
        )
        return Command.SINGLE_SUCCESS
    }

    private fun remove(ctx: CommandContext<CommandSourceStack>, kind: AreaKind): Int {
        val s = ctx.source.sender
        val name = StringArgumentType.getString(ctx, "name")
        val removed = try {
            plugin.areas.remove(kind, name)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to save areas.yml: ${e.message}")
            Msg.send(s, "<red>(╥﹏╥) 寫入 areas.yml 失敗，請查看後台")
            return 0
        }
        if (removed) {
            Msg.send(s, "<green>(ﾉ>ω<)ﾉ 已刪除${kind.display} <gold><name>", "name" to name)
        } else {
            Msg.send(s, "<yellow>(・_・?) 找不到${kind.display} <gold><name>", "name" to name)
        }
        return if (removed) Command.SINGLE_SUCCESS else 0
    }

    private fun list(ctx: CommandContext<CommandSourceStack>): Int {
        val s = ctx.source.sender
        val set = plugin.areas.current
        Msg.send(s, "<light_purple>(｡･ω･｡) 區域列表")
        for (kind in AreaKind.entries) {
            val areas = set.of(kind).values.sortedBy { it.name }
            val color = if (kind == AreaKind.WHITELIST) "green" else "red"
            Msg.line(s, " <$color>${kind.display}</$color> <gray>(<n>)", "n" to areas.size)
            if (areas.isEmpty()) Msg.line(s, "   <gray>（空空的～）")
            areas.forEach { Msg.line(s, "   <gold><name></gold> <gray><range>", "name" to it.name, "range" to it.describe()) }
        }
        return Command.SINGLE_SUCCESS
    }

    private fun scan(ctx: CommandContext<CommandSourceStack>): Int {
        val p = player(ctx) ?: return 0
        val loc = p.location
        val world = loc.world
        val settings = plugin.settings
        val set = plugin.areas.current
        val x = loc.blockX
        val y = loc.blockY
        val z = loc.blockZ
        val density = plugin.densityCache.density(world, x, z, settings.radius)
        val wl = set.firstHit(AreaKind.WHITELIST, world.name, x, y, z)
        val bl = set.firstHit(AreaKind.BLACKLIST, world.name, x, y, z)

        // What a portal-spawned zombified piglin would get at this spot.
        val facts = PortalFacts(
            isZombifiedPiglin = true,
            fromEnvironment = world.environment,
            toEnvironment = World.Environment.NETHER,
            worldEnabled = world.name in settings.worlds,
            hasCustomName = false,
            isLeashed = false,
            hasVehicleOrPassengers = false,
            spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NETHER_PORTAL,
            inWhitelist = wl != null,
            inBlacklist = bl != null,
        )
        val decision = decide(facts, settings.autoDetect) { density.total }

        Msg.send(p, "<aqua>(｀・ω・´) 掃描 <gold><world> (<x>, <y>, <z>)", "world" to world.name, "x" to x, "y" to y, "z" to z)
        val densityColor = if (density.total >= settings.threshold) "red" else "green"
        Msg.line(
            p, " 傳送門密度：<$densityColor><total></$densityColor> <gray>/ 門檻 <threshold>（<chunks> 個區塊，半徑 <radius>）",
            "total" to density.total, "threshold" to settings.threshold, "chunks" to density.chunks, "radius" to settings.radius,
        )
        if (density.missing > 0) {
            Msg.line(p, "   <yellow>(・_・;) 有 <n> 個區塊還沒算好（先當 0），已排程計算，稍後再掃一次會更準", "n" to density.missing)
        }
        Msg.line(p, " 白名單：<v>", "v" to (wl?.name ?: "無"))
        Msg.line(p, " 黑名單：<v>", "v" to (bl?.name ?: "無"))
        val verdict = if (decision.remove) "<red>會被移除 (｡•́︿•̀｡)" else "<green>不會被處理 (๑˃ᴗ˂)ﻭ"
        Msg.line(
            p, " 傳送門生成的殭屍化豬布林在這裡：$verdict <gray>[<code>：<reason>]",
            "code" to decision.name, "reason" to decision.reason,
        )
        return Command.SINGLE_SUCCESS
    }

    private fun stats(ctx: CommandContext<CommandSourceStack>): Int {
        val s = ctx.source.sender
        val st = plugin.stats
        Msg.send(s, "<light_purple>(〃∀〃) 啟動以來的統計")
        Msg.line(s, " <red>黑名單移除</red>：<gold><n>", "n" to st.count(Decision.REMOVE_BLACKLIST))
        Msg.line(s, " <red>自動偵測移除</red>：<gold><n>", "n" to st.count(Decision.REMOVE_AUTO))
        Msg.line(s, " <green>白名單放行</green>：<gold><n>", "n" to st.count(Decision.IGNORE_WHITELIST))
        Msg.line(s, " <gray>快取中的區塊：<n>", "n" to plugin.densityCache.size)
        val top = st.topRemovalChunks(5)
        Msg.line(s, " 移除最多的區塊（前 5）：")
        if (top.isEmpty()) Msg.line(s, "   <gray>（還沒有移除過任何豬布林～）")
        top.forEachIndexed { i, (c, n) ->
            Msg.line(
                s, "   <gray><i>.</gray> <gold><world> 區塊 (<cx>, <cz>)</gold> <gray>≈ 方塊 (<bx>, <bz>)</gray> × <aqua><n>",
                "i" to i + 1, "world" to c.world, "cx" to c.x, "cz" to c.z, "bx" to (c.x shl 4) + 8, "bz" to (c.z shl 4) + 8, "n" to n,
            )
        }
        return Command.SINGLE_SUCCESS
    }

    private fun reload(ctx: CommandContext<CommandSourceStack>): Int {
        val s = ctx.source.sender
        val warnings = plugin.reloadAll()
        if (warnings.isEmpty()) {
            Msg.send(s, "<green>(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧ 已重新載入 config.yml 和 areas.yml")
        } else {
            Msg.send(s, "<yellow>(・_・;) 已重新載入，但有 <n> 個警告：", "n" to warnings.size)
            warnings.forEach { Msg.line(s, "   <yellow>- <w>", "w" to it) }
        }
        return Command.SINGLE_SUCCESS
    }

    companion object {
        const val PERMISSION = "piglinguard.admin"
        const val MAX_AREA_RADIUS = 4096
    }
}
