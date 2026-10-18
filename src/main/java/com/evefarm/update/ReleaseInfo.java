package com.evefarm.update;

public record ReleaseInfo(
        String version,
        String notes,
        String pageUrl,
        String assetName,
        String assetUrl,
        long assetSize,
        String signatureUrl
) {
}
