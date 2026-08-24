package net.datasa.tanoshimi.domain.dto;

/** 마이페이지 "내 예약" 목록용 뷰. */
public record MyReservationView(
        Long reservationId, String tourTitle, String tourThumbnail, String region,
        String departureDate, String status, String myPaymentStatus, String myPaymentCurrency, Integer myPaymentAmount
) {
}
