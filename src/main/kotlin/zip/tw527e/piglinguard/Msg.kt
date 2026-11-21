package zip.tw527e.piglinguard

import net.kyori.adventure.audience.Audience
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

    private fun resolver(args: Array<out Pair<String, Any?>>): TagResolver =
        TagResolver.resolver(args.map { (k, v) -> Placeholder.unparsed(k, v.toString()) })
}
