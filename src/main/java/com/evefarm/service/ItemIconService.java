package com.evefarm.service;

import com.evefarm.esi.EsiConfig;
import com.evefarm.util.AppPaths;

import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
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
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ItemIconService {

    private static final Logger LOG = Logger.getLogger(ItemIconService.class.getName());
    private static final String ICON_URL = "https://images.evetech.net/types/%d/icon?size=32";
    private static final String BLUEPRINT_COPY_URL = "https://images.evetech.net/types/%d/bpc?size=32";
    private static final int CONCURRENCY = 6;
    public static final int RENDER_SIZE = 20;

    public static final ImageIcon BLANK_PLACEHOLDER =
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

    public ImageIcon getIfLoaded(int typeId) {
        return memoryCache.get(typeId);
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
        Image raw = new ImageIcon(bytes).getImage();
        BufferedImage buffered = new BufferedImage(RENDER_SIZE, RENDER_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = buffered.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(raw, 0, 0, RENDER_SIZE, RENDER_SIZE, null);
        g2.dispose();
        return new ImageIcon(buffered);
    }
}
