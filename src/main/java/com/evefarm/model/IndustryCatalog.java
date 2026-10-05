package com.evefarm.model;

import java.util.List;

public record IndustryCatalog(List<IndustryType> types, List<IndustryActivity> activities) {
}
