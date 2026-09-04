package com.storeledger.sale;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;

/** 매출 집계 단위. 주는 월요일 시작(ISO 주). */
public enum Period {
    DAILY, WEEKLY, MONTHLY, YEARLY;

    /** 날짜가 속한 구간의 시작일. */
    public LocalDate bucketStart(LocalDate date) {
        return switch (this) {
            case DAILY -> date;
            case WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> date.withDayOfMonth(1);
            case YEARLY -> date.withDayOfYear(1);
        };
    }

    /** 다음 구간의 시작일. */
    public LocalDate next(LocalDate bucketStart) {
        return switch (this) {
            case DAILY -> bucketStart.plusDays(1);
            case WEEKLY -> bucketStart.plusWeeks(1);
            case MONTHLY -> bucketStart.plusMonths(1);
            case YEARLY -> bucketStart.plusYears(1);
        };
    }

    /** 구간 라벨: 2026-09-04 / 2026-W36 / 2026-09 / 2026 */
    public String label(LocalDate bucketStart) {
        return switch (this) {
            case DAILY -> bucketStart.toString();
            case WEEKLY -> String.format("%d-W%02d",
                    bucketStart.get(IsoFields.WEEK_BASED_YEAR), bucketStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case MONTHLY -> String.format("%d-%02d", bucketStart.getYear(), bucketStart.getMonthValue());
            case YEARLY -> String.valueOf(bucketStart.getYear());
        };
    }

    /** 시작일 생략 시 기본 조회 범위: 최근 30일 / 12주 / 12개월 / 5년. */
    public LocalDate defaultFrom(LocalDate to) {
        return switch (this) {
            case DAILY -> to.minusDays(29);
            case WEEKLY -> bucketStart(to).minusWeeks(11);
            case MONTHLY -> bucketStart(to).minusMonths(11);
            case YEARLY -> bucketStart(to).minusYears(4);
        };
    }
}
