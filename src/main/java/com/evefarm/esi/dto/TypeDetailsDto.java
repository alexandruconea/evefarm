package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TypeDetailsDto(
        @JsonProperty("type_id") int typeId,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("group_id") int groupId,
        @JsonProperty("published") Boolean published,
        @JsonProperty("dogma_attributes") List<DogmaAttribute> dogmaAttributes
) {

    public Map<Integer, Double> attributes() {
        if (dogmaAttributes == null) {
            return Map.of();
        }
        return dogmaAttributes.stream()
                .collect(Collectors.toMap(DogmaAttribute::attributeId, DogmaAttribute::value, (a, b) -> a));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DogmaAttribute(
            @JsonProperty("attribute_id") int attributeId,
            @JsonProperty("value") double value
    ) {
    }
}
