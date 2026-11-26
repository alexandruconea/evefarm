package com.evefarm.service;

import com.evefarm.db.dao.WalletJournalDao.MissionIncomeEntry;
import com.evefarm.model.LpGain;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntPredicate;

public final class MissionIncome {

    static final String REWARD = "agent_mission_reward";
    static final String TIME_BONUS = "agent_mission_time_bonus_reward";
    static final Duration TURN_IN_BEFORE = Duration.ofMinutes(30);
    static final Duration TURN_IN_AFTER = Duration.ofHours(1);

    public enum Kind {
        REWARD, TIME_BONUS, MISSION_NPCS, NEAR_TURN_IN, OTHER_BOUNTY
    }

    public record Payment(String characterName, Instant paidAt, Kind kind, double amount, String agentName,
                          boolean turnIn) {
    }

    public enum Period {
        DAY("Day"), WEEK("Week"), MONTH("Month"), THREE_MONTHS("3 Months"), SIX_MONTHS("6 Months"), YEAR("Year"),
        ALL_TIME("All Time");

        private final String label;

        Period(String label) {
            this.label = label;
        }

        public boolean followsCalendar() {
            return this != ALL_TIME;
        }

        public Grouping grouping() {
            return switch (this) {
                case DAY, WEEK, MONTH -> Grouping.DAY;
                case THREE_MONTHS -> Grouping.WEEK;
                case SIX_MONTHS, YEAR, ALL_TIME -> Grouping.MONTH;
            };
        }

        public LocalDate start(LocalDate date, DayOfWeek firstDayOfWeek) {
            return switch (this) {
                case DAY, ALL_TIME -> date;
                case WEEK -> date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek));
                case MONTH -> date.withDayOfMonth(1);
                case THREE_MONTHS -> firstMonthOf(date, 3);
                case SIX_MONTHS -> firstMonthOf(date, 6);
                case YEAR -> date.withDayOfYear(1);
            };
        }

        public LocalDate end(LocalDate start) {
            return followsCalendar() ? shift(start, 1).minusDays(1) : start;
        }

        public LocalDate shift(LocalDate start, int periods) {
            return switch (this) {
                case DAY -> start.plusDays(periods);
                case WEEK -> start.plusWeeks(periods);
                case MONTH -> start.plusMonths(periods);
                case THREE_MONTHS -> start.plusMonths(3L * periods);
                case SIX_MONTHS -> start.plusMonths(6L * periods);
                case YEAR -> start.plusYears(periods);
                case ALL_TIME -> start;
            };
        }

        private static LocalDate firstMonthOf(LocalDate date, int months) {
            return date.withDayOfMonth(1).withMonth((date.getMonthValue() - 1) / months * months + 1);
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Grouping {
        DAY, WEEK, MONTH;

        public LocalDate start(LocalDate date, DayOfWeek firstDayOfWeek) {
            return switch (this) {
                case DAY -> date;
                case WEEK -> date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek));
                case MONTH -> date.withDayOfMonth(1);
            };
        }
    }

    public record Totals(int missions, double rewards, double timeBonuses, double missionNpcBounties,
                         double nearTurnInBounties, double otherBounties, long lp) {

        public Totals plus(Totals other) {
            return new Totals(missions + other.missions, rewards + other.rewards, timeBonuses + other.timeBonuses,
                    missionNpcBounties + other.missionNpcBounties, nearTurnInBounties + other.nearTurnInBounties,
                    otherBounties + other.otherBounties, lp + other.lp);
        }

        public double bounties() {
            return missionNpcBounties + nearTurnInBounties;
        }

        public double isk() {
            return rewards + timeBonuses + bounties();
        }
    }

    public record DayTotals(LocalDate date, Totals totals) {
    }

    public record AgentTotals(String agentName, int missions, double rewards, double timeBonuses, double bounties) {

        public double isk() {
            return rewards + timeBonuses + bounties;
        }

        public double perMission() {
            return missions == 0 ? 0 : isk() / missions;
        }
    }

    public record CorporationLp(String corporationName, long points) {
    }

    public record Report(LocalDate from, LocalDate to, Totals totals, List<DayTotals> days, List<AgentTotals> agents,
                         List<CorporationLp> corporations, Instant lpEarnedSince) {
    }

    public static List<Payment> classify(List<MissionIncomeEntry> entries, IntPredicate missionNpc) {
        Map<Long, NavigableMap<Instant, String>> turnIns = new HashMap<>();
        for (MissionIncomeEntry entry : entries) {
            if (REWARD.equals(entry.refType()) || TIME_BONUS.equals(entry.refType())) {
                turnIns.computeIfAbsent(entry.characterId(), id -> new TreeMap<>())
                        .put(entry.date(), entry.agentName());
            }
        }
        Set<List<Object>> counted = new HashSet<>();
        List<Payment> payments = new ArrayList<>();
        for (MissionIncomeEntry entry : entries) {
            Kind kind;
            String agent = entry.agentName();
            boolean turnIn = false;
            if (REWARD.equals(entry.refType()) || TIME_BONUS.equals(entry.refType())) {
                kind = REWARD.equals(entry.refType()) ? Kind.REWARD : Kind.TIME_BONUS;
                turnIn = counted.add(Arrays.asList(entry.characterId(), entry.date(), agent));
            } else {
                Map.Entry<Instant, String> nearest = nearestTurnIn(turnIns.get(entry.characterId()), entry.date());
                boolean missionNpcs = OfficerService.parseReason(entry.reason()).keySet().stream()
                        .anyMatch(missionNpc::test);
                kind = missionNpcs ? Kind.MISSION_NPCS : nearest != null ? Kind.NEAR_TURN_IN : Kind.OTHER_BOUNTY;
                agent = nearest == null ? null : nearest.getValue();
            }
            payments.add(new Payment(entry.characterName(), entry.date(), kind, entry.amount(), agent, turnIn));
        }
        return payments;
    }

    static Map.Entry<Instant, String> nearestTurnIn(NavigableMap<Instant, String> turnIns, Instant paidAt) {
        if (turnIns == null) {
            return null;
        }
        Map.Entry<Instant, String> before = turnIns.floorEntry(paidAt);
        Map.Entry<Instant, String> after = turnIns.higherEntry(paidAt);
        if (before != null && before.getKey().isBefore(paidAt.minus(TURN_IN_BEFORE))) {
            before = null;
        }
        if (after != null && after.getKey().isAfter(paidAt.plus(TURN_IN_AFTER))) {
            after = null;
        }
        if (before == null || after == null) {
            return before == null ? after : before;
        }
        return Duration.between(before.getKey(), paidAt).compareTo(Duration.between(paidAt, after.getKey())) <= 0
                ? before : after;
    }

    public static Report report(List<Payment> payments, List<LpGain> gains, LocalDate from, LocalDate to,
                                ZoneId zone) {
        Sum total = new Sum();
        Map<LocalDate, Sum> days = new TreeMap<>();
        Map<String, AgentSum> agents = new LinkedHashMap<>();
        Map<String, Long> corporations = new HashMap<>();
        for (Payment payment : payments) {
            LocalDate date = payment.paidAt().atZone(zone).toLocalDate();
            if (date.isBefore(from) || date.isAfter(to) || payment.amount() == 0 && !payment.turnIn()) {
                continue;
            }
            total.add(payment);
            days.computeIfAbsent(date, d -> new Sum()).add(payment);
            if (payment.kind() != Kind.OTHER_BOUNTY) {
                agents.computeIfAbsent(payment.agentName(), name -> new AgentSum()).add(payment);
            }
        }
        Instant lpEarnedSince = null;
        for (LpGain gain : gains) {
            LocalDate date = gain.seenAt().atZone(zone).toLocalDate();
            if (date.isBefore(from) || date.isAfter(to)) {
                continue;
            }
            total.lp += gain.points();
            days.computeIfAbsent(date, d -> new Sum()).lp += gain.points();
            corporations.merge(gain.corporationName(), gain.points(), Long::sum);
            if (LpProduction.spansEarlierDays(gain, zone)
                    && (lpEarnedSince == null || gain.since().isBefore(lpEarnedSince))) {
                lpEarnedSince = gain.since();
            }
        }
        List<DayTotals> dayTotals = days.entrySet().stream()
                .map(day -> new DayTotals(day.getKey(), day.getValue().totals()))
                .toList();
        List<AgentTotals> agentTotals = agents.entrySet().stream()
                .map(agent -> agent.getValue().totals(agent.getKey()))
                .sorted(Comparator.comparingDouble(AgentTotals::isk).reversed())
                .toList();
        List<CorporationLp> corporationLp = corporations.entrySet().stream()
                .map(corporation -> new CorporationLp(corporation.getKey(), corporation.getValue()))
                .sorted(Comparator.comparingLong(CorporationLp::points).reversed()
                        .thenComparing(CorporationLp::corporationName))
                .toList();
        return new Report(from, to, total.totals(), dayTotals, agentTotals, corporationLp, lpEarnedSince);
    }

    public static List<DayTotals> group(List<DayTotals> days, Grouping grouping, DayOfWeek firstDayOfWeek) {
        if (grouping == Grouping.DAY) {
            return days;
        }
        Map<LocalDate, Totals> groups = new TreeMap<>();
        for (DayTotals day : days) {
            groups.merge(grouping.start(day.date(), firstDayOfWeek), day.totals(), Totals::plus);
        }
        return groups.entrySet().stream().map(group -> new DayTotals(group.getKey(), group.getValue())).toList();
    }

    private static final class Sum {
        private int missions;
        private double rewards;
        private double timeBonuses;
        private double missionNpcBounties;
        private double nearTurnInBounties;
        private double otherBounties;
        private long lp;

        void add(Payment payment) {
            if (payment.turnIn()) {
                missions++;
            }
            switch (payment.kind()) {
                case REWARD -> rewards += payment.amount();
                case TIME_BONUS -> timeBonuses += payment.amount();
                case MISSION_NPCS -> missionNpcBounties += payment.amount();
                case NEAR_TURN_IN -> nearTurnInBounties += payment.amount();
                case OTHER_BOUNTY -> otherBounties += payment.amount();
            }
        }

        Totals totals() {
            return new Totals(missions, rewards, timeBonuses, missionNpcBounties, nearTurnInBounties, otherBounties,
                    lp);
        }
    }

    private static final class AgentSum {
        private int missions;
        private double rewards;
        private double timeBonuses;
        private double bounties;

        void add(Payment payment) {
            if (payment.turnIn()) {
                missions++;
            }
            switch (payment.kind()) {
                case REWARD -> rewards += payment.amount();
                case TIME_BONUS -> timeBonuses += payment.amount();
                default -> bounties += payment.amount();
            }
        }

        AgentTotals totals(String agentName) {
            return new AgentTotals(agentName, missions, rewards, timeBonuses, bounties);
        }
    }

    private MissionIncome() {
    }
}
