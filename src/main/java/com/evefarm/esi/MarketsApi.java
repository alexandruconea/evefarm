package com.evefarm.esi;

import com.evefarm.esi.dto.MarketHistoryDto;
import com.evefarm.esi.dto.MarketOrderDto;
import com.evefarm.esi.dto.MarketPriceDto;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;

public final class MarketsApi {

    private final EsiHttpClient client;

    public MarketsApi(EsiHttpClient client) {
        this.client = client;
    }

    public List<MarketOrderDto> listCharacterOrders(long characterId, String accessToken) {
        String body = client.get("/characters/" + characterId + "/orders/", accessToken, Map.of());
        return client.read(body, new TypeReference<List<MarketOrderDto>>() {
        }, "market orders");
    }

    public List<MarketOrderDto> listCharacterOrderHistory(long characterId, String accessToken) {
        List<String> pages = client.getAllPages("/characters/" + characterId + "/orders/history/", accessToken,
                Map.of());
        return client.readPages(pages, new TypeReference<List<MarketOrderDto>>() {
        }, "the market order history");
    }

    public List<MarketOrderDto> listRegionOrders(long regionId, int typeId) {
        return listRegionOrders(regionId, typeId, "all");
    }

    public List<MarketOrderDto> listRegionOrders(long regionId, int typeId, String orderType) {
        List<String> pages = client.getAllPages("/markets/" + regionId + "/orders/", null,
                Map.of("order_type", orderType, "type_id", String.valueOf(typeId)));
        return client.readPages(pages, new TypeReference<List<MarketOrderDto>>() {
        }, "the market orders of type " + typeId);
    }

    public List<MarketHistoryDto> listRegionHistory(long regionId, int typeId) {
        String body = client.get("/markets/" + regionId + "/history/", null,
                Map.of("type_id", String.valueOf(typeId)));
        return client.read(body, new TypeReference<List<MarketHistoryDto>>() {
        }, "the market history of type " + typeId);
    }

    public List<MarketPriceDto> listPrices() {
        String body = client.get("/markets/prices/", null, Map.of());
        return client.read(body, new TypeReference<List<MarketPriceDto>>() {
        }, "market prices");
    }
}
