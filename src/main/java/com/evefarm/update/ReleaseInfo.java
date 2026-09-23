package com.evefarm.update;

public record ReleaseInfo(
        String version,
        String title,
        String notes,
        String pageUrl,
        String zipName,
        String zipUrl,
        long zipSize,
        String signatureUrl
) {
}
