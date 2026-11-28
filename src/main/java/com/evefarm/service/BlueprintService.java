package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.auth.OAuthConfig;
import com.evefarm.db.dao.BlueprintDao;
import com.evefarm.db.dao.CharacterDao;
import com.evefarm.esi.IndustryApi;
import com.evefarm.esi.dto.BlueprintDto;
import com.evefarm.model.BlueprintEntry;
import com.evefarm.model.BlueprintRow;

import java.util.List;
import java.util.stream.Collectors;

public final class BlueprintService {

    private final AuthService authService;
    private final CharacterDao characterDao;
    private final IndustryApi industryApi;
    private final TypeNameCacheService typeNameCacheService;
    private final BlueprintDao blueprintDao;

    public BlueprintService(AuthService authService, CharacterDao characterDao, IndustryApi industryApi,
                            TypeNameCacheService typeNameCacheService, BlueprintDao blueprintDao) {
        this.authService = authService;
        this.characterDao = characterDao;
        this.industryApi = industryApi;
        this.typeNameCacheService = typeNameCacheService;
        this.blueprintDao = blueprintDao;
    }

    public void refreshBlueprintsForCharacter(long characterId) {
        CharacterScopes.require(characterDao, characterId, OAuthConfig.BLUEPRINTS_SCOPE, "blueprints");
        String accessToken = authService.getValidAccessToken(characterId);
        List<BlueprintDto> blueprints = industryApi.listBlueprints(characterId, accessToken);
        typeNameCacheService.resolveTypes(blueprints.stream().map(BlueprintDto::typeId).collect(Collectors.toSet()));
        blueprintDao.replaceForCharacter(characterId, blueprints.stream().map(BlueprintService::entry).toList());
    }

    static BlueprintEntry entry(BlueprintDto blueprint) {
        return new BlueprintEntry(blueprint.itemId(), blueprint.typeId(), blueprint.locationId(),
                blueprint.locationFlag(), blueprint.quantity(), blueprint.materialEfficiency(),
                blueprint.timeEfficiency(), blueprint.runs());
    }

    public List<BlueprintRow> getBlueprintRows() {
        return blueprintDao.listRows();
    }
}
