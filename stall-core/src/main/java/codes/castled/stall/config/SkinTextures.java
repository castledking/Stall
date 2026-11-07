package codes.castled.stall.config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes Minecraft skin textures (URLs, raw hex hashes, or minecraft-heads.com Base64 dev strings)
 * into Base64 profile properties or direct texture URLs.
 */
public final class SkinTextures
{
    private static final Pattern URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern HEX_PATTERN = Pattern.compile("^[0-9a-fA-F]{32,64}$");

    private SkinTextures()
    {
    }

    /**
     * Converts a texture input (Base64 string, texture hash, or texture URL) into a standard Base64
     * textures property value.
     */
    public static String toBase64(String input)
    {
        if (input == null || input.isBlank())
        {
            return "";
        }
        String trimmed = input.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://"))
        {
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + trimmed + "\"}}}";
            return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        }
        if (HEX_PATTERN.matcher(trimmed).matches())
        {
            String url = "http://textures.minecraft.net/texture/" + trimmed.toLowerCase(Locale.ROOT);
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
            return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        }
        // Assume already Base64 (such as minecraft-heads.com 64bit string)
        return trimmed;
    }

    /**
     * Extracts or converts the texture input into a direct texture URL.
     */
    public static String toUrl(String input)
    {
        if (input == null || input.isBlank())
        {
            return "";
        }
        String trimmed = input.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://"))
        {
            return trimmed;
        }
        if (HEX_PATTERN.matcher(trimmed).matches())
        {
            return "http://textures.minecraft.net/texture/" + trimmed.toLowerCase(Locale.ROOT);
        }
        try
        {
            byte[] decoded = Base64.getDecoder().decode(trimmed);
            String json = new String(decoded, StandardCharsets.UTF_8);
            Matcher matcher = URL_PATTERN.matcher(json);
            if (matcher.find())
            {
                return matcher.group(1);
            }
        }
        catch (IllegalArgumentException ignored)
        {
        }
        return "";
    }
}
