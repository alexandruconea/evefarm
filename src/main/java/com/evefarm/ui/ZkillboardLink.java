package com.evefarm.ui;

record ZkillboardLink(String name, String kind, Long id) implements Comparable<ZkillboardLink> {

    static ZkillboardLink system(String name, Long id) {
        return new ZkillboardLink(name, "system", id);
    }

    static ZkillboardLink constellation(String name, Long id) {
        return new ZkillboardLink(name, "constellation", id);
    }

    static ZkillboardLink region(String name, Long id) {
        return new ZkillboardLink(name, "region", id);
    }

    boolean linked() {
        return id != null && name != null && !name.isBlank();
    }

    String url() {
        return "https://zkillboard.com/" + kind + "/" + id + "/";
    }

    @Override
    public int compareTo(ZkillboardLink other) {
        return String.CASE_INSENSITIVE_ORDER.compare(toString(), other.toString());
    }

    @Override
    public String toString() {
        return name == null ? "" : name;
    }
}
