package gnu.client.render.graphics;

import gnu.client.mixin.impl.accessors.IAccessorAbstractResourcePack;
import gnu.client.mixin.impl.accessors.IAccessorFallbackResourceManager;
import gnu.client.mixin.impl.accessors.IAccessorSimpleReloadableResourceManager;
import gnu.client.render.graphics.properties.PropertiesFile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.AbstractResourcePack;
import net.minecraft.client.resources.FallbackResourceManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.util.ResourceLocation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Resolves OptiFine resource-pack paths. Tries {@code assets/minecraft/optifine/} first,
 * then legacy {@code assets/minecraft/mcpatcher/}.
 */
public final class GraphicsPackRoots {

    public static final String OPTIFINE = "optifine/";
    public static final String MCPATCHER = "mcpatcher/";

    private GraphicsPackRoots() {}

    public static ResourceLocation optifine(String relative) {
        return new ResourceLocation("minecraft", OPTIFINE + stripLeading(relative));
    }

    public static ResourceLocation mcpatcher(String relative) {
        return new ResourceLocation("minecraft", MCPATCHER + stripLeading(relative));
    }

    /**
     * First existing resource among optifine then mcpatcher for {@code relative}
     * (e.g. {@code sky/world0/sky1.properties}).
     */
    public static ResourceLocation find(String relative) {
        IResourceManager rm = manager();
        if (rm == null) {
            return null;
        }
        ResourceLocation of = optifine(relative);
        if (exists(rm, of)) {
            return of;
        }
        ResourceLocation mp = mcpatcher(relative);
        if (exists(rm, mp)) {
            return mp;
        }
        return null;
    }

    /**
     * OptiFineDoc {@code syntax.html} + {@code custom_sky.html}: omitted {@code source}
     * is the PNG next to the properties file; {@code ./} is that folder; {@code ~/} and
     * paths with {@code /} are under {@code optifine/} then {@code mcpatcher/}; a bare
     * filename is tried next to the properties file first (what sky packs actually ship).
     * Returns null when none of the candidates exist — never a missing-texture location.
     */
    public static ResourceLocation resolveTexture(ResourceLocation propsLoc, String source) {
        IResourceManager rm = manager();
        if (rm == null || propsLoc == null) {
            return null;
        }
        String trimmed = source == null ? "" : source.trim();
        if (trimmed.contains(":") && !trimmed.startsWith("~/") && !trimmed.startsWith("./")) {
            ResourceLocation namespaced = new ResourceLocation(trimmed);
            return exists(rm, namespaced) ? namespaced : null;
        }
        for (String path : textureCandidates(propsLoc.getResourcePath(), source)) {
            ResourceLocation loc = new ResourceLocation(propsLoc.getResourceDomain(), path);
            if (exists(rm, loc)) {
                return loc;
            }
        }
        return null;
    }

    public static List<String> textureCandidates(String propsPath, String source) {
        String dir = dirOf(propsPath);
        String implied = propsPath.endsWith(".properties")
                ? propsPath.substring(0, propsPath.length() - ".properties".length()) + ".png"
                : propsPath + ".png";
        if (source == null || source.trim().isEmpty()) {
            return Collections.singletonList(implied);
        }
        String s = normalizePng(source.trim().replace('\\', '/'));
        List<String> out = new ArrayList<String>();
        if (s.startsWith("./")) {
            out.add(dir + s.substring(2));
            return out;
        }
        if (s.startsWith("~/")) {
            out.addAll(optifineThenMcpatcher(s.substring(2)));
            return out;
        }
        s = stripLeadingSlashOnly(s);
        if (s.startsWith("assets/minecraft/")) {
            out.add(s.substring("assets/minecraft/".length()));
            return out;
        }
        if (s.contains("/")) {
            out.addAll(optifineThenMcpatcher(s));
            if (!s.startsWith("optifine/") && !s.startsWith("mcpatcher/")) {
                out.add(dir + s);
            }
            return out;
        }
        out.add(dir + s);
        out.addAll(optifineThenMcpatcher(s));
        return out;
    }

    static List<String> optifineThenMcpatcher(String rest) {
        rest = stripLeadingSlashOnly(rest);
        List<String> out = new ArrayList<String>(2);
        if (rest.startsWith("optifine/") || rest.startsWith("mcpatcher/")) {
            out.add(rest);
            if (rest.startsWith("optifine/")) {
                out.add("mcpatcher/" + rest.substring("optifine/".length()));
            } else {
                out.add("optifine/" + rest.substring("mcpatcher/".length()));
            }
            return out;
        }
        out.add(OPTIFINE + rest);
        out.add(MCPATCHER + rest);
        return out;
    }

    private static String normalizePng(String s) {
        if (s.endsWith(".properties")) {
            return s.substring(0, s.length() - ".properties".length()) + ".png";
        }
        int slash = s.lastIndexOf('/');
        String name = slash >= 0 ? s.substring(slash + 1) : s;
        if (!name.contains(".")) {
            return s + ".png";
        }
        return s;
    }

    private static String dirOf(String resourcePath) {
        if (resourcePath == null) {
            return "";
        }
        String s = resourcePath.replace('\\', '/');
        int slash = s.lastIndexOf('/');
        return slash >= 0 ? s.substring(0, slash + 1) : "";
    }

    private static String stripLeadingSlashOnly(String path) {
        if (path == null) {
            return "";
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    public static PropertiesFile loadProperties(String relative) {
        ResourceLocation loc = find(relative);
        if (loc == null) {
            return PropertiesFile.empty();
        }
        return loadProperties(loc);
    }

    public static PropertiesFile loadProperties(ResourceLocation loc) {
        IResourceManager rm = manager();
        if (rm == null || loc == null) {
            return PropertiesFile.empty();
        }
        InputStream in = null;
        try {
            IResource res = rm.getResource(loc);
            in = res.getInputStream();
            return PropertiesFile.parse(in);
        } catch (Exception e) {
            return PropertiesFile.empty();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static boolean exists(String relative) {
        return find(relative) != null;
    }

    public static boolean exists(IResourceManager rm, ResourceLocation loc) {
        if (rm == null || loc == null) {
            return false;
        }
        try {
            return rm.getResource(loc) != null;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * List {@code .properties} under {@code optifine/<folder>/} and {@code mcpatcher/<folder>/}
     * by walking active resource packs (zip + folder).
     */
    public static List<ResourceLocation> listProperties(String folder) {
        String rel = stripLeading(folder);
        if (!rel.endsWith("/")) {
            rel = rel + "/";
        }
        Set<ResourceLocation> out = new LinkedHashSet<ResourceLocation>();
        collectFromPacks(OPTIFINE + rel, out);
        collectFromPacks(MCPATCHER + rel, out);
        return new ArrayList<ResourceLocation>(out);
    }

    /**
     * Numbered files {@code prefix + n + suffix} (e.g. sky1.properties) until a gap of 8.
     */
    public static List<ResourceLocation> listNumbered(String folder, String prefix, String suffix) {
        List<ResourceLocation> out = new ArrayList<ResourceLocation>();
        int miss = 0;
        for (int n = 0; n <= 256 && miss < 8; n++) {
            ResourceLocation loc = find(folder + "/" + prefix + n + suffix);
            if (loc == null) {
                miss++;
                continue;
            }
            miss = 0;
            out.add(loc);
        }
        return out;
    }

    private static void collectFromPacks(String assetsRelative, Set<ResourceLocation> out) {
        for (IResourcePack pack : activePacks()) {
            File file = packFile(pack);
            if (file == null || !file.exists()) {
                continue;
            }
            String prefix = "assets/minecraft/" + assetsRelative;
            if (file.isDirectory()) {
                walkFolder(new File(file, prefix.replace('/', File.separatorChar)), assetsRelative, out);
            } else {
                walkZip(file, prefix, assetsRelative, out);
            }
        }
    }

    private static void walkFolder(File dir, String domainPath, Set<ResourceLocation> out) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                walkFolder(f, domainPath + f.getName() + "/", out);
            } else if (f.getName().endsWith(".properties")) {
                out.add(new ResourceLocation("minecraft", domainPath + f.getName()));
            }
        }
    }

    private static void walkZip(File zip, String prefix, String domainPath, Set<ResourceLocation> out) {
        ZipFile zf = null;
        try {
            zf = new ZipFile(zip);
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) {
                    continue;
                }
                String name = e.getName();
                if (!name.startsWith(prefix) || !name.endsWith(".properties")) {
                    continue;
                }
                String rel = name.substring("assets/minecraft/".length());
                out.add(new ResourceLocation("minecraft", rel));
            }
        } catch (IOException ignored) {
        } finally {
            if (zf != null) {
                try {
                    zf.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static File packFile(IResourcePack pack) {
        if (pack instanceof AbstractResourcePack) {
            try {
                return ((IAccessorAbstractResourcePack) pack).getResourcePackFile();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<IResourcePack> activePacks() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return Collections.emptyList();
        }
        List<IResourcePack> packs = new ArrayList<IResourcePack>();
        try {
            ResourcePackRepository repo = mc.getResourcePackRepository();
            if (repo != null) {
                packs.add(repo.rprDefaultResourcePack);
                List<ResourcePackRepository.Entry> entries = repo.getRepositoryEntries();
                if (entries != null) {
                    for (ResourcePackRepository.Entry e : entries) {
                        packs.add(e.getResourcePack());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        IResourceManager rm = mc.getResourceManager();
        if (rm instanceof SimpleReloadableResourceManager) {
            try {
                Map<String, FallbackResourceManager> map =
                        ((IAccessorSimpleReloadableResourceManager) rm).getDomainResourceManagers();
                if (map != null) {
                    FallbackResourceManager minecraft = map.get("minecraft");
                    if (minecraft instanceof IAccessorFallbackResourceManager) {
                        List<IResourcePack> domain =
                                ((IAccessorFallbackResourceManager) minecraft).getResourcePacks();
                        if (domain != null) {
                            for (IResourcePack p : domain) {
                                if (!packs.contains(p)) {
                                    packs.add(p);
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return packs;
    }

    private static IResourceManager manager() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc == null ? null : mc.getResourceManager();
    }

    private static String stripLeading(String relative) {
        if (relative == null) {
            return "";
        }
        String s = relative.replace('\\', '/');
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        if (s.startsWith("assets/minecraft/")) {
            s = s.substring("assets/minecraft/".length());
        }
        if (s.startsWith("optifine/")) {
            s = s.substring("optifine/".length());
        } else if (s.startsWith("mcpatcher/")) {
            s = s.substring("mcpatcher/".length());
        }
        return s;
    }
}
