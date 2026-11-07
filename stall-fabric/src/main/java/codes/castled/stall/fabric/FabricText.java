package codes.castled.stall.fabric;

import codes.castled.stall.config.LegacyText;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Turns {@code §}-coded text from {@code lang.yml} into Minecraft {@link Component}s. */
final class FabricText
{
    private FabricText()
    {
    }

    /** A chat message or menu title. */
    static MutableComponent chat(String text)
    {
        return parse(text, false);
    }

    /** An item's name or lore line: non-italic unless {@code &o} is in the text. */
    static Component itemLine(String text)
    {
        return parse(text, true);
    }

    static List<Component> itemLines(List<String> lines)
    {
        return lines.stream().map(FabricText::itemLine).toList();
    }

    /** Sends {@code message} unless it is empty, so setting a message to {@code ""} in {@code lang.yml} silences it. */
    static void send(ServerPlayer player, String message)
    {
        if (message != null && !message.isEmpty())
        {
            player.sendSystemMessage(chat(message));
        }
    }

    static void sendSuccess(CommandSourceStack source, String message, boolean broadcast)
    {
        if (message != null && !message.isEmpty())
        {
            Component component = chat(message);
            source.sendSuccess(() -> component, broadcast);
        }
    }

    static void sendFailure(CommandSourceStack source, String message)
    {
        if (message != null && !message.isEmpty())
        {
            source.sendFailure(chat(message));
        }
    }

    private static MutableComponent parse(String text, boolean item)
    {
        List<LegacyText.Span> spans = LegacyText.parse(text);
        if (spans.isEmpty())
        {
            MutableComponent empty = Component.empty();
            return item ? empty.withStyle(style -> style.withItalic(false)) : empty;
        }
        if (spans.size() == 1)
        {
            return span(spans.getFirst(), item);
        }
        MutableComponent root = Component.empty();
        if (item)
        {
            root.withStyle(style -> style.withItalic(false));
        }
        for (LegacyText.Span span : spans)
        {
            root.append(span(span, false));
        }
        return root;
    }

    private static MutableComponent span(LegacyText.Span span, boolean explicitNonItalic)
    {
        return Component.literal(span.text()).withStyle(style -> apply(style, span, explicitNonItalic));
    }

    private static Style apply(Style style, LegacyText.Span span, boolean explicitNonItalic)
    {
        Style result = style;
        if (span.color() != null)
        {
            if (span.color().startsWith("#"))
            {
                result = TextColor.parseColor(span.color()).result().map(result::withColor).orElse(result);
            }
            else
            {
                char code = LegacyText.code(span.color());
                ChatFormatting color = code == 0 ? null : ChatFormatting.getByCode(code);
                if (color != null)
                {
                    result = result.withColor(color);
                }
            }
        }
        if (span.bold())
        {
            result = result.withBold(true);
        }
        if (span.italic())
        {
            result = result.withItalic(true);
        }
        else if (explicitNonItalic)
        {
            result = result.withItalic(false);
        }
        if (span.underlined())
        {
            result = result.withUnderlined(true);
        }
        if (span.strikethrough())
        {
            result = result.withStrikethrough(true);
        }
        if (span.obfuscated())
        {
            result = result.withObfuscated(true);
        }
        return result;
    }
}
