package com.framebucket.dashboard.service;

import com.framebucket.customer.repository.CustomerRepository;
import com.framebucket.dashboard.api.DashboardDtos.ActivitySummary;
import com.framebucket.dashboard.api.DashboardDtos.DashboardSummaryResponse;
import com.framebucket.dashboard.api.DashboardDtos.RevenueByEventType;
import com.framebucket.enquiry.repository.EnquiryRepository;
import com.framebucket.event.repository.EventBookingRepository;
import com.framebucket.event.repository.EventBookingRepository.EventRevenueRow;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

@Service
public class DashboardService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final Pattern PRICE_VALUE = Pattern.compile("^\\d+(?:\\.\\d{1,2})?$");
    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

    private final CustomerRepository customerRepository;
    private final EnquiryRepository enquiryRepository;
    private final EventBookingRepository eventRepository;

    public DashboardService(
            CustomerRepository customerRepository,
            EnquiryRepository enquiryRepository,
            EventBookingRepository eventRepository
    ) {
        this.customerRepository = customerRepository;
        this.enquiryRepository = enquiryRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summaryForPhotographer(PhotographerPrincipal photographer) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate nextMonthStart = monthStart.plusMonths(1);
        int financialYearStart = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
        LocalDate financialYearStartDate = LocalDate.of(financialYearStart, 4, 1);

        Instant monthStartInstant = monthStart.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant nextMonthStartInstant = nextMonthStart.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant financialYearStartInstant = financialYearStartDate.atStartOfDay(BUSINESS_ZONE).toInstant();

        long enquiries = enquiryRepository.countByPhotographer_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                photographer.getId(), monthStartInstant, nextMonthStartInstant
        );
        long customers = customerRepository.countByPhotographer_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                photographer.getId(), monthStartInstant, nextMonthStartInstant
        );
        long events = eventRepository.countByPhotographer_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                photographer.getId(), monthStartInstant, nextMonthStartInstant
        );

        List<EventRevenueRow> revenueRows = eventRepository.findRevenueRows(
                photographer.getId(), financialYearStartInstant, nextMonthStartInstant
        );
        Map<String, BigDecimal> revenueByType = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (EventRevenueRow row : revenueRows) {
            BigDecimal price = parsePrice(row.getPrice());
            if (price == null || price.signum() <= 0) {
                continue;
            }
            revenueByType.merge(row.getEventTypeName(), price, BigDecimal::add);
        }

        BigDecimal revenueThisMonth = revenueRows.stream()
                .filter(row -> !row.getCreatedAt().isBefore(monthStartInstant)
                        && row.getCreatedAt().isBefore(nextMonthStartInstant))
                .map(row -> parsePrice(row.getPrice()))
                .filter(price -> price != null && price.signum() > 0)
                .reduce(ZERO_MONEY, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal revenueThisFinancialYear = revenueByType.values().stream()
                .reduce(ZERO_MONEY, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        List<RevenueByEventType> breakdown = new ArrayList<>();
        revenueByType.forEach((eventTypeName, revenue) -> breakdown.add(
                new RevenueByEventType(eventTypeName, revenue.setScale(2, RoundingMode.HALF_UP))
        ));

        int nextFinancialYear = financialYearStart + 1;
        String financialYearLabel = financialYearStart + "–" + String.format(Locale.ROOT, "%02d", nextFinancialYear % 100);
        return new DashboardSummaryResponse(
                MONTH_LABEL.format(today),
                financialYearLabel,
                new ActivitySummary(enquiries, customers, events),
                revenueThisMonth,
                revenueThisFinancialYear,
                List.copyOf(breakdown)
        );
    }

    private static BigDecimal parsePrice(String submittedPrice) {
        if (submittedPrice == null || submittedPrice.isBlank()) {
            return null;
        }
        String normalized = submittedPrice.trim()
                .replaceAll("(?i)^(?:INR|RS\\.?|₹)\\s*", "")
                .replaceAll("(?i)\\s*(?:INR|RS\\.?)$", "")
                .replace(",", "")
                .replace("/-", "")
                .trim();
        if (!PRICE_VALUE.matcher(normalized).matches()) {
            return null;
        }
        try {
            return new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
