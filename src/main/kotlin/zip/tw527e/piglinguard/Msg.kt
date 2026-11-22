package zip.tw527e.piglinguard

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver

/** MiniMessage helpers. Placeholders are inserted unparsed, so area names can't inject tags. */
object Msg {
    private val mm = MiniMessage.miniMessage()
    private const val PREFIX = "<gradient:#ff9a9e:#fbc2eb>[PiglinGuard]</gradient> "

    fun send(to: Audience, template: String, vararg args: Pair<String, Any?>) {
        to.sendMessage(mm.deserialize(PREFIX + template, resolver(args)))
    }

    /** Line without the prefix, for multi-line output. */
    fun line(to: Audience, template: String, vararg args: Pair<String, Any?>) {
        to.sendMessage(mm.deserialize(template, resolver(args)))
    }

    /** Help line: clicking the command puts [suggest] in the chat box. */
    fun command(to: Audience, display: String, suggest: String, description: String) {
        val cmd = Component.text(display, NamedTextColor.GOLD)
            .clickEvent(ClickEvent.suggestCommand(suggest))
            .hoverEvent(HoverEvent.showText(Component.text("點一下填入 $suggest", NamedTextColor.GRAY)))
        to.sendMessage(
            Component.text(" ").append(cmd).append(Component.text(" - $description", NamedTextColor.GRAY)),
        )
    }

    private fun resolver(args: Array<out Pair<String, Any?>>): TagResolver =
        TagResolver.resolver(args.map { (k, v) -> Placeholder.unparsed(k, v.toString()) })
}
