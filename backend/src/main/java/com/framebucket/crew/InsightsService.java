package com.framebucket.crew;

import com.framebucket.crew.CrewDtos.EventProfit;
import com.framebucket.crew.CrewDtos.Insights;
import com.framebucket.crew.CrewDtos.MonthAmount;
import com.framebucket.crew.CrewDtos.MonthCount;
import com.framebucket.crew.CrewDtos.OutstandingEvent;
import com.framebucket.crew.CrewDtos.SourceStat;
import com.framebucket.studio.StudioEvents;
import com.framebucket.subscription.EntitlementService;
import com.framebucket.subscription.Feature;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Business insights: bookings and shoots by month, seasonality, conversion by lead source, AOV, outstanding balances, profit. */
@Service
public class InsightsService {
    private final JdbcTemplate jdbc;
    private final StudioEvents events;
    private final EntitlementService entitlements;

    public InsightsService(JdbcTemplate jdbc, StudioEvents events, EntitlementService entitlements) {
        this.jdbc = jdbc;
        this.events = events;
        this.entitlements = entitlements;
    }

    @Transactional(readOnly = true)
    public Insights insights(UUID photographerId) {
        entitlements.require(photographerId, Feature.INSIGHTS);
        LocalDate today = EntitlementService.today();
        YearMonth thisMonth = YearMonth.from(today);
        List<StudioEvents.EventInfo> all = events.allForPhotographer(photographerId);

        // Bookings made per month (by created date) over the last 12 months.
        Map<YearMonth, Long> created = new HashMap<>();
        jdbc.query("SELECT created_at FROM fb_event_booking WHERE photographer_id = ? AND created_at >= ?",
                rs -> {
                    YearMonth month = YearMonth.from(rs.getTimestamp(1).toInstant().atZone(java.time.ZoneId.of("Asia/Kolkata")));
                    created.merge(month, 1L, Long::sum);
                }, photographerId, java.sql.Timestamp.valueOf(thisMonth.minusMonths(11).atDay(1).atStartOfDay()));
        List<MonthCount> bookingsByMonth = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            YearMonth month = thisMonth.minusMonths(i);
            bookingsByMonth.add(new MonthCount(label(month), created.getOrDefault(month, 0L)));
        }

        // Shoots by event month: 6 months back to 5 months ahead (the season ahead).
        Map<YearMonth, Long> shoots = new HashMap<>();
        long[] seasonCounts = new long[12];
        for (StudioEvents.EventInfo event : all) {
            if (event.eventDate() == null) continue;
            shoots.merge(YearMonth.from(event.eventDate()), 1L, Long::sum);
            seasonCounts[event.eventDate().getMonthValue() - 1]++;
        }
        List<MonthCount> shootsByMonth = new ArrayList<>();
        for (int i = -6; i <= 5; i++) {
            YearMonth month = thisMonth.plusMonths(i);
            shootsByMonth.add(new MonthCount(label(month), shoots.getOrDefault(month, 0L)));
        }
        List<MonthCount> seasonality = new ArrayList<>();
        for (Month month : Month.values()) {
            seasonality.add(new MonthCount(month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), seasonCounts[month.ordinal()]));
        }

        // Revenue received per month (client payments), last 12 months.
        Map<YearMonth, BigDecimal> revenue = new HashMap<>();
        jdbc.query("SELECT paid_on, amount FROM fb_event_payment WHERE photographer_id = ? AND paid_on >= ?",
                rs -> {
                    revenue.merge(YearMonth.from(rs.getDate(1).toLocalDate()), rs.getBigDecimal(2), BigDecimal::add);
                }, photographerId, java.sql.Date.valueOf(thisMonth.minusMonths(11).atDay(1)));
        List<MonthAmount> revenueByMonth = new ArrayList<>();
        BigDecimal revenue12m = BigDecimal.ZERO;
        for (int i = 11; i >= 0; i--) {
            YearMonth month = thisMonth.minusMonths(i);
            BigDecimal amount = revenue.getOrDefault(month, BigDecimal.ZERO);
            revenue12m = revenue12m.add(amount);
            revenueByMonth.add(new MonthAmount(label(month), amount));
        }
        java.sql.Date yearAgo = java.sql.Date.valueOf(today.minusMonths(12));
        BigDecimal expenses12m = jdbc.queryForObject("SELECT COALESCE(sum(amount), 0) FROM fb_expense WHERE photographer_id = ? AND spent_on >= ?",
                BigDecimal.class, photographerId, yearAgo);
        BigDecimal crew12m = jdbc.queryForObject("SELECT COALESCE(sum(amount), 0) FROM fb_crew_payout WHERE photographer_id = ? AND paid_on >= ?",
                BigDecimal.class, photographerId, yearAgo);
        BigDecimal costs12m = expenses12m.add(crew12m);

        // Enquiry conversion overall and by lead source.
        Map<String, long[]> bySource = new LinkedHashMap<>();
        jdbc.query("""
                SELECT source, count(*), count(*) FILTER (WHERE created_event_id IS NOT NULL)
                FROM fb_enquiries WHERE photographer_id = ? AND created_at >= now() - interval '12 months'
                GROUP BY source ORDER BY count(*) DESC
                """, rs -> {
            bySource.put(rs.getString(1), new long[]{rs.getLong(2), rs.getLong(3)});
        }, photographerId);
        long enquiries = bySource.values().stream().mapToLong(v -> v[0]).sum();
        long booked = bySource.values().stream().mapToLong(v -> v[1]).sum();
        List<SourceStat> sources = bySource.entrySet().stream()
                .map(e -> new SourceStat(e.getKey(), e.getValue()[0], e.getValue()[1], percent(e.getValue()[1], e.getValue()[0])))
                .toList();

        // Average order value over events with a numeric agreed price, created in the last 12 months.
        Map<UUID, BigDecimal> paidByEvent = new HashMap<>();
        jdbc.query("SELECT event_id, sum(amount) FROM fb_event_payment WHERE photographer_id = ? AND purpose <> 'DOWNLOAD' GROUP BY event_id",
                rs -> {
                    paidByEvent.put(rs.getObject(1, UUID.class), rs.getBigDecimal(2));
                }, photographerId);
        Map<UUID, BigDecimal> costByEvent = new HashMap<>();
        jdbc.query("""
                SELECT event_id, sum(amount) FROM (
                    SELECT event_id, amount FROM fb_expense WHERE photographer_id = ? AND event_id IS NOT NULL
                    UNION ALL
                    SELECT event_id, fee FROM fb_event_crew WHERE photographer_id = ?
                ) costs GROUP BY event_id
                """, rs -> {
            costByEvent.put(rs.getObject(1, UUID.class), rs.getBigDecimal(2));
        }, photographerId, photographerId);

        BigDecimal priced = BigDecimal.ZERO;
        int pricedCount = 0;
        BigDecimal outstandingTotal = BigDecimal.ZERO;
        List<OutstandingEvent> outstanding = new ArrayList<>();
        List<EventProfit> profits = new ArrayList<>();
        for (StudioEvents.EventInfo event : all) {
            BigDecimal agreed = event.totalAmount();
            if (agreed.signum() > 0 && event.eventDate() != null && !event.eventDate().isBefore(today.minusMonths(12))) {
                priced = priced.add(agreed);
                pricedCount++;
            }
            BigDecimal received = paidByEvent.getOrDefault(event.id(), BigDecimal.ZERO);
            BigDecimal balance = agreed.subtract(received);
            if (agreed.signum() > 0 && balance.signum() > 0) {
                outstandingTotal = outstandingTotal.add(balance);
                outstanding.add(new OutstandingEvent(event.id(), event.eventCode(), event.customerName(), event.eventTypeName(),
                        event.eventDate(), agreed, received, balance));
            }
            if (event.eventDate() != null && !event.eventDate().isAfter(today) && !event.eventDate().isBefore(today.minusMonths(12))) {
                BigDecimal costs = costByEvent.getOrDefault(event.id(), BigDecimal.ZERO);
                profits.add(new EventProfit(event.id(), event.eventCode(), event.customerName(), event.eventTypeName(), event.eventDate(),
                        agreed, costs, agreed.subtract(costs)));
            }
        }
        outstanding.sort(Comparator.comparing(OutstandingEvent::balance).reversed());
        profits.sort(Comparator.comparing(EventProfit::eventDate).reversed());
        BigDecimal aov = pricedCount == 0 ? BigDecimal.ZERO : priced.divide(BigDecimal.valueOf(pricedCount), 0, RoundingMode.HALF_UP);

        return new Insights(bookingsByMonth, shootsByMonth, seasonality, revenueByMonth, enquiries, booked, percent(booked, enquiries),
                aov, revenue12m, costs12m, revenue12m.subtract(costs12m), outstandingTotal,
                sources, outstanding.stream().limit(15).toList(), profits.stream().limit(30).toList());
    }

    private static BigDecimal percent(long part, long whole) {
        return whole == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(part * 100.0 / whole).setScale(1, RoundingMode.HALF_UP);
    }

    private static String label(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + String.valueOf(month.getYear()).substring(2);
    }
}
