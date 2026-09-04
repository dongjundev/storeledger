package com.storeledger.sale;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PeriodTest {

    private static final LocalDate FRIDAY = LocalDate.of(2026, 9, 4);

    @Test
    void 일별_구간() {
        assertEquals(FRIDAY, Period.DAILY.bucketStart(FRIDAY));
        assertEquals(LocalDate.of(2026, 9, 5), Period.DAILY.next(FRIDAY));
        assertEquals("2026-09-04", Period.DAILY.label(FRIDAY));
        assertEquals(LocalDate.of(2026, 8, 6), Period.DAILY.defaultFrom(FRIDAY)); // 최근 30일
    }

    @Test
    void 주별_구간은_월요일에_시작한다() {
        LocalDate monday = LocalDate.of(2026, 8, 31);
        assertEquals(monday, Period.WEEKLY.bucketStart(FRIDAY));
        assertEquals(monday, Period.WEEKLY.bucketStart(monday));
        assertEquals(LocalDate.of(2026, 9, 7), Period.WEEKLY.next(monday));
        assertEquals("2026-W36", Period.WEEKLY.label(monday));
        assertEquals(LocalDate.of(2026, 6, 15), Period.WEEKLY.defaultFrom(FRIDAY)); // 최근 12주
    }

    @Test
    void 월별_구간() {
        LocalDate first = LocalDate.of(2026, 9, 1);
        assertEquals(first, Period.MONTHLY.bucketStart(FRIDAY));
        assertEquals(LocalDate.of(2026, 10, 1), Period.MONTHLY.next(first));
        assertEquals("2026-09", Period.MONTHLY.label(first));
        assertEquals(LocalDate.of(2025, 10, 1), Period.MONTHLY.defaultFrom(FRIDAY)); // 최근 12개월
    }

    @Test
    void 연도별_구간() {
        LocalDate first = LocalDate.of(2026, 1, 1);
        assertEquals(first, Period.YEARLY.bucketStart(FRIDAY));
        assertEquals(LocalDate.of(2027, 1, 1), Period.YEARLY.next(first));
        assertEquals("2026", Period.YEARLY.label(first));
        assertEquals(LocalDate.of(2022, 1, 1), Period.YEARLY.defaultFrom(FRIDAY)); // 최근 5년
    }

    @Test
    void 연초의_주는_ISO_주기준_연도를_따른다() {
        // 2027-01-01(금)은 2026년 53주차에 속한다
        LocalDate monday = Period.WEEKLY.bucketStart(LocalDate.of(2027, 1, 1));
        assertEquals(LocalDate.of(2026, 12, 28), monday);
        assertEquals("2026-W53", Period.WEEKLY.label(monday));
    }
}
