package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.auth.OAuthConfig;
import com.evefarm.db.dao.AcceleratorDao;
import com.evefarm.db.dao.CharacterDao;
import com.evefarm.db.dao.CharacterSkillDao;
import com.evefarm.db.dao.SkillPlanDao;
import com.evefarm.esi.ClonesApi;
import com.evefarm.esi.SkillsApi;
import com.evefarm.esi.dto.CharacterAttributesDto;
import com.evefarm.esi.dto.CharacterSkillsDto;
import com.evefarm.esi.dto.SkillQueueDto;
import com.evefarm.model.Accelerator;
import com.evefarm.model.CharacterAccelerator;
import com.evefarm.model.CharacterAttributes;
import com.evefarm.model.CharacterSkills;
import com.evefarm.model.OwnedSkill;
import com.evefarm.model.SkillPlan;
import com.evefarm.model.SkillPlanEntry;
import com.evefarm.model.SkillRequirement;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class SkillService {

    private final AuthService authService;
    private final CharacterDao characterDao;
    private final SkillsApi skillsApi;
    private final ClonesApi clonesApi;
    private final SkillCatalogService skillCatalogService;
    private final CharacterSkillDao characterSkillDao;
    private final SkillPlanDao skillPlanDao;
    private final AcceleratorDao acceleratorDao;

    public SkillService(AuthService authService, CharacterDao characterDao, SkillsApi skillsApi, ClonesApi clonesApi,
                        SkillCatalogService skillCatalogService, CharacterSkillDao characterSkillDao,
                        SkillPlanDao skillPlanDao, AcceleratorDao acceleratorDao) {
        this.authService = authService;
        this.characterDao = characterDao;
        this.skillsApi = skillsApi;
        this.clonesApi = clonesApi;
        this.skillCatalogService = skillCatalogService;
        this.characterSkillDao = characterSkillDao;
        this.skillPlanDao = skillPlanDao;
        this.acceleratorDao = acceleratorDao;
    }

    public void refreshSkillsForCharacter(long characterId) {
        skillCatalogService.refreshIfStale();
        String accessToken = authService.getValidAccessToken(characterId);
        CharacterSkillsDto skills = skillsApi.getCharacterSkills(characterId, accessToken);
        CharacterAttributesDto attributes = skillsApi.getAttributes(characterId, accessToken);
        List<Integer> implants = clonesApi.listImplants(characterId, accessToken);
        List<OwnedSkill> owned = skills.skills() == null ? List.of() : skills.skills().stream()
                .map(skill -> new OwnedSkill(skill.skillId(), skill.skillPoints(), skill.trainedLevel(),
                        skill.activeLevel()))
                .toList();
        CharacterAttributes values = new CharacterAttributes(attributes.charisma(), attributes.intelligence(),
                attributes.memory(), attributes.perception(), attributes.willpower());
        characterSkillDao.save(new CharacterSkills(characterId, owned, values, implants, skills.totalSp(),
                skills.unallocatedSp() == null ? 0 : skills.unallocatedSp(), attributes.bonusRemaps(),
                attributes.lastRemapDate(), attributes.remapCooldownDate(), Instant.now().toString()));
        trackAccelerator(characterId, values, implants, owned);
    }

    private void trackAccelerator(long characterId, CharacterAttributes values, List<Integer> implants,
                                  List<OwnedSkill> owned) {
        int bonus = SkillPlanner.acceleratorBonus(values, skillCatalogService.implantBonus(implants));
        if (bonus <= 0) {
            acceleratorDao.delete(characterId);
            return;
        }
        Accelerator likely = AcceleratorTracking.likelyAccelerator(skillCatalogService.accelerators(), bonus)
                .orElse(null);
        int biology = owned.stream().filter(skill -> skill.skillId() == AcceleratorTracking.BIOLOGY_SKILL_ID)
                .mapToInt(OwnedSkill::trainedLevel).findFirst().orElse(0);
        CharacterAccelerator tracked = AcceleratorTracking.track(acceleratorDao.find(characterId).orElse(null),
                characterId, bonus, likely, biology, Instant.now());
        acceleratorDao.save(tracked);
    }

    public Optional<CharacterAccelerator> accelerator(long characterId) {
        return acceleratorDao.find(characterId);
    }

    public void setAcceleratorEnd(CharacterAccelerator accelerator, Instant end) {
        acceleratorDao.save(accelerator.withEnd(end, true));
    }

    public Optional<CharacterSkills> skills(long characterId) {
        return characterSkillDao.find(characterId);
    }

    public List<SkillRequirement> skillQueue(long characterId) {
        CharacterScopes.require(characterDao, characterId, OAuthConfig.SKILL_QUEUE_SCOPE, "skill queue");
        String accessToken = authService.getValidAccessToken(characterId);
        return queuedLevels(skillsApi.getSkillQueue(characterId, accessToken), Instant.now());
    }

    static List<SkillRequirement> queuedLevels(List<SkillQueueDto> queue, Instant now) {
        return queue.stream()
                .filter(entry -> entry.finishDate() == null || Instant.parse(entry.finishDate()).isAfter(now))
                .sorted(Comparator.comparingInt(SkillQueueDto::queuePosition))
                .map(entry -> new SkillRequirement(entry.skillId(), entry.finishedLevel()))
                .toList();
    }

    public List<SkillPlan> plans(long characterId) {
        return skillPlanDao.listPlans(characterId);
    }

    public SkillPlan createPlan(long characterId, String name) {
        return skillPlanDao.create(characterId, name);
    }

    public SkillPlan copyPlan(SkillPlan plan, String name) {
        SkillPlan copy = skillPlanDao.create(plan.characterId(), name);
        skillPlanDao.replaceEntries(copy.planId(), skillPlanDao.entries(plan.planId()));
        return copy;
    }

    public void renamePlan(SkillPlan plan, String name) {
        skillPlanDao.rename(plan.planId(), name);
    }

    public void deletePlan(SkillPlan plan) {
        skillPlanDao.delete(plan.planId());
    }

    public List<SkillPlanEntry> entries(SkillPlan plan) {
        return skillPlanDao.entries(plan.planId());
    }

    public void saveEntries(SkillPlan plan, List<SkillPlanEntry> entries) {
        skillPlanDao.replaceEntries(plan.planId(), entries);
    }
}
