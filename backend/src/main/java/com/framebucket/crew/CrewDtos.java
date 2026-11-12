package com.framebucket.crew;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class CrewDtos {
    private CrewDtos() {
    }

    public record Member(String memberType, UUID memberId, String name, String mobileNumber, String status) {
    }

    public record CrewView(UUID id, String memberType, UUID memberId, String name, String mobileNumber, String role,
                           LocalTime callTime, BigDecimal fee, String notes, List<String> warnings) {
    }

    public record CrewRequest(
            @NotBlank @Pattern(regexp = "TEAM|FREELANCER") String memberType,
            @NotNull UUID memberId,
            @NotBlank @Size(max = 80) String role,
            LocalTime callTime,
            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal fee,
            @Size(max = 500) String notes
    ) {
    }

    public record AvailabilityCheck(String memberType, UUID memberId, List<String> warnings) {
    }

    public record UnavailableView(UUID id, String memberType, UUID memberId, String name, LocalDate date, String note) {
    }

    public record UnavailableRequest(
            @NotBlank @Pattern(regexp = "TEAM|FREELANCER") String memberType,
            @NotNull UUID memberId,
            @NotNull LocalDate date,
            @Size(max = 200) String note
    ) {
    }

    public record PayoutView(UUID id, String memberType, UUID memberId, String name, UUID eventId, String eventCode,
                             BigDecimal amount, LocalDate paidOn, String method, String reference, String notes) {
    }

    public record PayoutRequest(
            @NotBlank @Pattern(regexp = "TEAM|FREELANCER") String memberType,
            @NotNull UUID memberId,
            UUID eventId,
            @NotNull @DecimalMin("1.00") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotNull LocalDate paidOn,
            @NotBlank @Pattern(regexp = "UPI|CASH|BANK_TRANSFER|CARD|CHEQUE|OTHER") String method,
            @Size(max = 120) String reference,
            @Size(max = 500) String notes
    ) {
    }

    public record Balance(String memberType, UUID memberId, String name, String mobileNumber, long assignments,
                          BigDecimal earned, BigDecimal upcoming, BigDecimal paid, BigDecimal due) {
    }

    // ---- gear -------------------------------------------------------------------------------------

    public record GearItem(UUID id, String name, String category, String serialNumber, boolean inDefaultKit, boolean active,
                           String notes) {
    }

    public record GearRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 40) String category,
            @Size(max = 80) String serialNumber,
            boolean inDefaultKit,
            boolean active,
            @Size(max = 300) String notes
    ) {
    }

    public record EventGear(UUID gearId, String name, String category, String serialNumber, boolean packed, boolean returned) {
    }

    public record EventGearRequest(@Size(max = 200) List<UUID> gearIds, boolean defaultKit) {
    }

    public record EventGearUpdate(Boolean packed, Boolean returned) {
    }

    // ---- costs & profit ---------------------------------------------------------------------------

    public record CostLine(UUID id, String category, BigDecimal amount, LocalDate spentOn, String vendor, String notes) {
    }

    public record EventCosts(
            BigDecimal agreedPrice,
            BigDecimal received,
            BigDecimal outstanding,
            List<CostLine> expenses,
            BigDecimal expenseTotal,
            BigDecimal crewFees,
            BigDecimal totalCosts,
            BigDecimal profit,
            BigDecimal marginPercent
    ) {
    }

    public record CostRequest(
            @Size(max = 40) String category,
            @NotNull @DecimalMin("1.00") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotNull LocalDate spentOn,
            @Size(max = 160) String vendor,
            @Size(max = 500) String notes
    ) {
    }

    // ---- insights ---------------------------------------------------------------------------------

    public record MonthCount(String month, long count) {
    }

    public record MonthAmount(String month, BigDecimal amount) {
    }

    public record SourceStat(String source, long enquiries, long booked, BigDecimal conversionPercent) {
    }

    public record OutstandingEvent(UUID eventId, String eventCode, String customerName, String eventType, LocalDate eventDate,
                                   BigDecimal agreed, BigDecimal received, BigDecimal balance) {
    }

    public record EventProfit(UUID eventId, String eventCode, String customerName, String eventType, LocalDate eventDate,
                              BigDecimal agreed, BigDecimal costs, BigDecimal profit) {
    }

    public record Insights(
            List<MonthCount> bookingsByMonth,
            List<MonthCount> shootsByMonth,
            List<MonthCount> seasonality,
            List<MonthAmount> revenueByMonth,
            long enquiries12m,
            long booked12m,
            BigDecimal conversionPercent,
            BigDecimal averageOrderValue,
            BigDecimal revenue12m,
            BigDecimal costs12m,
            BigDecimal profit12m,
            BigDecimal outstandingTotal,
            List<SourceStat> sources,
            List<OutstandingEvent> outstanding,
            List<EventProfit> profitByEvent
    ) {
    }
}
