package com.storeledger.sale;

import java.time.LocalDate;

/** 한 구간(일/주/월/년)의 매출·이익·판매 수량. start/end는 조회 범위로 잘라낸 실제 구간. */
public record SummaryPoint(String label, LocalDate start, LocalDate end, long revenue, long profit, long quantity) {
}
