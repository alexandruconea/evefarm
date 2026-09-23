package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FuzzworkBlueprintDto(
        @JsonProperty("requestedid") long requestedId,
        @JsonProperty("blueprintDetails") BlueprintDetails blueprintDetails,
        @JsonProperty("activityMaterials") Map<String, List<MaterialEntry>> activityMaterials
) {

    public static final String ACTIVITY_MANUFACTURING = "1";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BlueprintDetails(
            @JsonProperty("productTypeID") Integer productTypeId,
            @JsonProperty("productTypeName") String productTypeName,
            @JsonProperty("productQuantity") Integer productQuantity
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MaterialEntry(
            @JsonProperty("typeid") int typeId,
            @JsonProperty("name") String name,
            @JsonProperty("quantity") long quantity
    ) {
    }
}
