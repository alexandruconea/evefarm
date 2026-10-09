package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.auth.OAuthConfig;
import com.evefarm.db.dao.NotificationDao;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.esi.ContractsApi;
import com.evefarm.esi.SkillsApi;
import com.evefarm.esi.dto.ContractDto;
import com.evefarm.esi.dto.MarketOrderDto;
import com.evefarm.esi.dto.SkillQueueDto;
import com.evefarm.model.EveCharacter;
import com.evefarm.model.IndustryJobRow;
import com.evefarm.util.IskFormatter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class NotificationService {

    private static final Logger LOG = Logger.getLogger(NotificationService.class.getName());
    private static final Duration SOON = Duration.ofHours(24);
    private static final int MAX_LINES = 4;
    private static final Set<String> ACCEPTED = Set.of("in_progress", "finished", "finished_issuer",
            "finished_contractor");

    public record Notification(String caption, String text) {
    }

    private final AuthService authService;
    private final CharacterService characterService;
    private final IndustryJobService industryJobService;
    private final MarketWatchService marketWatchService;
    private final SkillsApi skillsApi;
    private final ContractsApi contractsApi;
    private final TypeNameCacheService typeNameCacheService;
    private final NotificationDao notificationDao;
    private final SettingsDao settingsDao;
    private final Clock clock;
    private final List<Consumer<Notification>> listeners = new CopyOnWriteArrayList<>();

    public NotificationService(AuthService authService, CharacterService characterService,
                               IndustryJobService industryJobService, MarketWatchService marketWatchService,
                               SkillsApi skillsApi, ContractsApi contractsApi,
                               TypeNameCacheService typeNameCacheService, NotificationDao notificationDao,
                               SettingsDao settingsDao) {
        this(authService, characterService, industryJobService, marketWatchService, skillsApi, contractsApi,
                typeNameCacheService, notificationDao, settingsDao, Clock.systemUTC());
    }

    NotificationService(AuthService authService, CharacterService characterService,
                        IndustryJobService industryJobService, MarketWatchService marketWatchService,
                        SkillsApi skillsApi, ContractsApi contractsApi, TypeNameCacheService typeNameCacheService,
                        NotificationDao notificationDao, SettingsDao settingsDao, Clock clock) {
        this.authService = authService;
        this.characterService = characterService;
        this.industryJobService = industryJobService;
        this.marketWatchService = marketWatchService;
        this.skillsApi = skillsApi;
        this.contractsApi = contractsApi;
        this.typeNameCacheService = typeNameCacheService;
        this.notificationDao = notificationDao;
        this.settingsDao = settingsDao;
        this.clock = clock;
    }

    public void addListener(Consumer<Notification> listener) {
        listeners.add(listener);
    }

    public void check() {
        List<EveCharacter> characters = characterService.listCharacters();
        Map<EveCharacter, List<MarketWatchService.Outbid>> outbid = marketWatchService.check(characters);
        if (enabled(SettingsDao.NOTIFY_INDUSTRY_JOBS)) {
            send("Industry job ready", "industry jobs ready", readyJobs(characters));
        }
        if (enabled(SettingsDao.NOTIFY_OUTBID_ORDERS)) {
            send("Market order outbid", "market orders outbid", outbidLines(outbid));
        }
        if (enabled(SettingsDao.NOTIFY_SKILL_QUEUE)) {
            send("Skill queue ending soon", "skill queues ending soon", skillQueues(characters));
        }
        if (enabled(SettingsDao.NOTIFY_CONTRACTS)) {
            List<String> accepted = new ArrayList<>();
            List<String> expiring = new ArrayList<>();
            contracts(characters, accepted, expiring);
            send("Contract accepted", "contracts accepted", accepted);
            send("Contract expiring", "contracts expiring", expiring);
        }
    }

    private boolean enabled(String key) {
        return !"false".equals(settingsDao.getOrDefault(key, "true"));
    }

    private List<String> readyJobs(List<EveCharacter> characters) {
        List<String> lines = new ArrayList<>();
        for (EveCharacter character : characters) {
            try {
                industryJobService.refreshJobsForCharacter(character.characterId());
                Instant now = clock.instant();
                Map<String, String> ready = new LinkedHashMap<>();
                for (IndustryJobRow job : industryJobService.getJobRows(Set.of(character.characterId()))) {
                    if (isReady(job, now)) {
                        ready.put(String.valueOf(job.jobId()), jobLine(job));
                    }
                }
                lines.addAll(remember("industry_job", character.characterId(), ready));
            } catch (RuntimeException e) {
                failed("industry jobs", character, e);
            }
        }
        return lines;
    }

    static boolean isReady(IndustryJobRow job, Instant now) {
        if ("ready".equals(job.status())) {
            return true;
        }
        return "active".equals(job.status()) && parse(job.endDate()).map(end -> !end.isAfter(now)).orElse(false);
    }

    private static String jobLine(IndustryJobRow job) {
        String item = job.productName() != null ? job.productName() : job.blueprintName();
        String runs = job.runs() != null && job.runs() > 1 ? ", " + job.runs() + " runs" : "";
        return job.characterName() + ": " + item + runs;
    }

    private List<String> outbidLines(Map<EveCharacter, List<MarketWatchService.Outbid>> outbid) {
        List<String> lines = new ArrayList<>();
        outbid.forEach((character, orders) -> {
            try {
                Map<String, String> current = new LinkedHashMap<>();
                for (MarketWatchService.Outbid entry : orders) {
                    MarketOrderDto order = entry.order();
                    current.put(String.valueOf(order.orderId()), character.characterName() + ": "
                            + typeNameCacheService.resolveType(order.typeId()).name()
                            + (order.isBuyOrder() ? " buy order, " : " sell order, ")
                            + IskFormatter.format(entry.bestPrice()) + " vs your "
                            + IskFormatter.format(order.price()));
                }
                lines.addAll(remember("outbid_order", character.characterId(), current));
            } catch (RuntimeException e) {
                failed("market orders", character, e);
            }
        });
        return lines;
    }

    private List<String> skillQueues(List<EveCharacter> characters) {
        List<String> lines = new ArrayList<>();
        for (EveCharacter character : characters) {
            if (character.scopes() == null || !character.scopes().contains(OAuthConfig.SKILL_QUEUE_SCOPE)) {
                continue;
            }
            try {
                String token = authService.getValidAccessToken(character.characterId());
                List<SkillQueueDto> queue = skillsApi.getSkillQueue(character.characterId(), token);
                Instant now = clock.instant();
                Map<String, String> ending = new LinkedHashMap<>();
                queueEnd(queue).ifPresent(end -> {
                    if (!end.isAfter(now)) {
                        ending.put("empty", character.characterName() + ": the skill queue is empty");
                    } else if (!end.isAfter(now.plus(SOON))) {
                        ending.put("ends:" + end, character.characterName() + ": the skill queue ends in "
                                + timeLeft(Duration.between(now, end)));
                    }
                });
                lines.addAll(remember("skill_queue", character.characterId(), ending));
            } catch (RuntimeException e) {
                failed("skill queue", character, e);
            }
        }
        return lines;
    }

    static Optional<Instant> queueEnd(List<SkillQueueDto> queue) {
        if (queue.isEmpty()) {
            return Optional.of(Instant.EPOCH);
        }
        return queue.stream()
                .map(entry -> parse(entry.finishDate()))
                .flatMap(Optional::stream)
                .max(Instant::compareTo);
    }

    private void contracts(List<EveCharacter> characters, List<String> acceptedLines, List<String> expiringLines) {
        for (EveCharacter character : characters) {
            try {
                long characterId = character.characterId();
                String token = authService.getValidAccessToken(characterId);
                Instant now = clock.instant();
                Map<String, String> accepted = new LinkedHashMap<>();
                Map<String, String> expiring = new LinkedHashMap<>();
                for (ContractDto contract : contractsApi.listContracts(characterId, token)) {
                    if (contract.forCorporation() || contract.issuerId() == null
                            || contract.issuerId() != characterId) {
                        continue;
                    }
                    String key = String.valueOf(contract.contractId());
                    String label = character.characterName() + ": " + contractLabel(contract);
                    if (acceptedRecently(contract, now)) {
                        accepted.put(key, label + " was accepted");
                    }
                    Optional<Instant> expires = parse(contract.dateExpired());
                    if ("outstanding".equals(contract.status()) && expires.isPresent()
                            && expires.get().isAfter(now) && !expires.get().isAfter(now.plus(SOON))) {
                        expiring.put(key, label + " expires in " + timeLeft(Duration.between(now, expires.get())));
                    }
                }
                acceptedLines.addAll(remember("contract_accepted", characterId, accepted));
                expiringLines.addAll(remember("contract_expiring", characterId, expiring));
            } catch (RuntimeException e) {
                failed("contracts", character, e);
            }
        }
    }

    static boolean acceptedRecently(ContractDto contract, Instant now) {
        if (!ACCEPTED.contains(contract.status())) {
            return false;
        }
        Optional<Instant> at = parse(contract.dateAccepted()).or(() -> parse(contract.dateCompleted()));
        return at.map(time -> !time.isAfter(now) && time.isAfter(now.minus(SOON))).orElse(false);
    }

    private static String contractLabel(ContractDto contract) {
        if (contract.title() != null && !contract.title().isBlank()) {
            return "contract \"" + contract.title().strip() + "\"";
        }
        String type = contract.type() == null ? "" : contract.type().replace('_', ' ') + " ";
        return type + "contract";
    }

    private List<String> remember(String kind, long characterId, Map<String, String> current) {
        Set<String> sent = notificationDao.sentKeys(kind, characterId);
        List<String> fresh = current.entrySet().stream()
                .filter(entry -> !sent.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        if (!sent.equals(current.keySet())) {
            notificationDao.replace(kind, characterId, current.keySet());
        }
        return fresh;
    }

    private void send(String singular, String plural, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        Notification notification = new Notification(lines.size() == 1 ? singular : lines.size() + " " + plural,
                text(lines));
        for (Consumer<Notification> listener : listeners) {
            try {
                listener.accept(notification);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "A notification listener failed", e);
            }
        }
    }

    static String text(List<String> lines) {
        List<String> shown = new ArrayList<>(lines.subList(0, Math.min(MAX_LINES, lines.size())));
        if (lines.size() > MAX_LINES) {
            shown.add("and " + (lines.size() - MAX_LINES) + " more");
        }
        return String.join("\n", shown);
    }

    static String timeLeft(Duration duration) {
        long minutes = Math.max(1, (duration.toSeconds() + 59) / 60);
        long hours = minutes / 60;
        long rest = minutes % 60;
        if (hours == 0) {
            return rest + " min";
        }
        return rest == 0 ? hours + " h" : hours + " h " + rest + " min";
    }

    private static Optional<Instant> parse(String isoInstant) {
        if (isoInstant == null || isoInstant.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(isoInstant));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static void failed(String what, EveCharacter character, RuntimeException e) {
        LOG.warning("Couldn't check the " + what + " of " + character.characterName() + " for notifications: "
                + e.getMessage());
    }
}
