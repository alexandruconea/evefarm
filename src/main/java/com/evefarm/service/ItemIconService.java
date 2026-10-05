package com.evefarm.service;

import com.evefarm.esi.EsiConfig;
import com.evefarm.util.AppPaths;

import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ItemIconService {

    private static final Logger LOG = Logger.getLogger(ItemIconService.class.getName());
    private static final String ICON_URL = "https://images.evetech.net/types/%d/icon?size=32";
    private static final String BLUEPRINT_COPY_URL = "https://images.evetech.net/types/%d/bpc?size=32";
    private static final String BLUEPRINT_URL = "https://images.evetech.net/types/%d/bp?size=64";
    private static final String LARGE_ICON_URL = "https://images.evetech.net/types/%d/icon?size=64";
    public static final int BLUEPRINT_SIZE = 40;
    private static final int CONCURRENCY = 6;
    public static final int RENDER_SIZE = 20;

    private static final ImageIcon BLANK_PLACEHOLDER =
            new ImageIcon(new BufferedImage(RENDER_SIZE, RENDER_SIZE, BufferedImage.TYPE_INT_ARGB));

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY, r -> {
        Thread t = new Thread(r, "item-icon-fetch");
        t.setDaemon(true);
        return t;
    });
    private final Map<Integer, ImageIcon> memoryCache = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> inFlight = new ConcurrentHashMap<>();
    private final Map<Integer, ImageIcon> blueprintCache = new ConcurrentHashMap<>();

    public ImageIcon iconOrPlaceholder(int typeId) {
        return memoryCache.getOrDefault(typeId, BLANK_PLACEHOLDER);
    }

    public void loadAsync(int typeId, Runnable onLoaded) {
        if (memoryCache.containsKey(typeId) || inFlight.putIfAbsent(typeId, Boolean.TRUE) != null) {
            return;
        }
        executor.submit(() -> {
            try {
                ImageIcon icon = fetch(typeId);
                if (icon != null) {
                    memoryCache.put(typeId, icon);
                    SwingUtilities.invokeLater(onLoaded);
                }
            } finally {
                inFlight.remove(typeId);
            }
        });
    }

    public void loadBlueprintAsync(int blueprintTypeId, Consumer<ImageIcon> onLoaded) {
        ImageIcon cached = blueprintCache.get(blueprintTypeId);
        if (cached != null) {
            onLoaded.accept(cached);
            return;
        }
        executor.submit(() -> {
            ImageIcon icon = fetchBlueprint(blueprintTypeId);
            if (icon != null) {
                blueprintCache.put(blueprintTypeId, icon);
                SwingUtilities.invokeLater(() -> onLoaded.accept(icon));
            }
        });
    }

    private ImageIcon fetchBlueprint(int typeId) {
        Path cacheFile = AppPaths.iconCacheDir().resolve(typeId + "-bp.png");
        try {
            if (Files.exists(cacheFile)) {
                return toSharpIcon(Files.readAllBytes(cacheFile), BLUEPRINT_SIZE);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to read cached blueprint image for type " + typeId, e);
        }
        byte[] bytes = download(String.format(BLUEPRINT_URL, typeId));
        if (bytes == null) {
            bytes = download(String.format(LARGE_ICON_URL, typeId));
        }
        if (bytes == null) {
            return null;
        }
        try {
            Files.createDirectories(AppPaths.iconCacheDir());
            Files.write(cacheFile, bytes);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to cache blueprint image for type " + typeId, e);
        }
        return toSharpIcon(bytes, BLUEPRINT_SIZE);
    }

    private ImageIcon fetch(int typeId) {
        Path cacheFile = AppPaths.iconCacheDir().resolve(typeId + ".png");
        try {
            if (Files.exists(cacheFile)) {
                return toScaledIcon(Files.readAllBytes(cacheFile));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to read cached icon for type " + typeId, e);
        }

        byte[] bytes = download(String.format(ICON_URL, typeId));
        if (bytes == null) {
            bytes = download(String.format(BLUEPRINT_COPY_URL, typeId));
        }
        if (bytes == null) {
            return null;
        }
        try {
            Files.createDirectories(AppPaths.iconCacheDir());
            Files.write(cacheFile, bytes);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to cache icon for type " + typeId, e);
        }
        return toScaledIcon(bytes);
    }

    private byte[] download(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", EsiConfig.USER_AGENT)
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return response.statusCode() / 100 == 2 ? response.body() : null;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to fetch " + url, e);
            return null;
        }
    }

    private ImageIcon toScaledIcon(byte[] bytes) {
        return new ImageIcon(scaled(new ImageIcon(bytes).getImage(), RENDER_SIZE));
    }

    private static ImageIcon toSharpIcon(byte[] bytes, int size) {
        Image raw = new ImageIcon(bytes).getImage();
        int rawSize = Math.max(size, raw.getWidth(null));
        return new ImageIcon(new BaseMultiResolutionImage(scaled(raw, size), scaled(raw, rawSize)));
    }

    private static BufferedImage scaled(Image raw, int size) {
        BufferedImage buffered = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = buffered.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(raw, 0, 0, size, size, null);
        g2.dispose();
        return buffered;
    }
}
