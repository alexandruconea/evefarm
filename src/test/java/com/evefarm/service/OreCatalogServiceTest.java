package com.evefarm.service;

import com.evefarm.model.Ore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OreCatalogServiceTest {

    private static final String GROUPS = """
            ﻿groupID,categoryID,groupName,iconID,useBasePrice,anchored,anchorable,fittableNonSingleton,published
            462,25,Veldspar,None,1,0,0,0,1
            465,25,Ice,None,1,0,0,0,1
            711,2,Harvestable Cloud,None,1,0,0,0,1
            4168,2,Compressed Gas,None,1,0,0,0,1
            10,2,Stargate,None,0,1,0,0,1
            25,6,Frigate,None,0,0,0,0,1
            """;

    private static final String TYPES = """
            typeID,groupID,typeName,description,mass,volume,capacity,portionSize,raceID,basePrice,published,marketGroupID,iconID,soundID,graphicID
            1230,462,Veldspar,"Common ore, found everywhere",1e+35,0.1,0,100,None,2,1,516,None,None,None
            28432,462,Compressed Veldspar,,1e+35,0.001,0,1,None,200,1,1942,None,None,None
            16262,465,Clear Icicle,,1000,1000,0,1,None,0,1,1855,None,None,None
            99999,462,Test Veldspar,,1e+35,0.1,0,100,None,0,0,None,None,None,None
            62516,462,Batch Compressed Veldspar,,1e+35,0.01,0,1,None,0,1,1942,None,None,None
            30370,711,Fullerite-C50,,0,1,0,1,None,0,1,1859,None,None,None
            62399,4168,Compressed Fullerite-C50,,0,0.1,0,1,None,0,1,1859,None,None,None
            29624,10,Stargate (Caldari System),,0,0,0,1,None,0,1,None,None,None,None
            587,25,Rifter,,1067000,27289,140,1,2,0,1,64,None,None,None
            """;

    private static final String MATERIALS = """
            typeID,materialTypeID,quantity
            1230,34,400
            16262,16274,69
            16262,16275,35
            587,34,9000
            """;

    @Test
    void rawOresGetTheirCompressedFormAndWhatTheyRefineInto() {
        List<Ore> ores = OreCatalogService.parseOres(GROUPS, TYPES, MATERIALS);

        assertEquals(List.of(1230, 16262, 30370), ores.stream().map(Ore::typeId).toList());
        Ore gas = ores.get(2);
        assertEquals(62399, gas.compressedTypeId());
        assertEquals(Map.of(), gas.materials(), "gas can't be reprocessed");
        Ore veldspar = ores.getFirst();
        assertEquals(100, veldspar.portionSize());
        assertEquals(28432, veldspar.compressedTypeId());
        assertEquals(Map.of(34, 400L), veldspar.materials());
        Ore ice = ores.get(1);
        assertEquals(1, ice.portionSize());
        assertNull(ice.compressedTypeId(), "no compressed ice in this export");
        assertEquals(Map.of(16274, 69L, 16275, 35L), ice.materials());
    }

    @Test
    void anEmptyExportGivesNoOres() {
        assertEquals(List.of(), OreCatalogService.parseOres("", "", ""));
    }
}
