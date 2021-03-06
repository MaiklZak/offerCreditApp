package com.app.offerCreditApp.service;

import com.app.offerCreditApp.dto.SchedulePaymentDto;
import com.app.offerCreditApp.entity.Offer;
import com.app.offerCreditApp.entity.SchedulePayment;
import com.app.offerCreditApp.repository.SchedulePaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class SchedulePaymentService {

    private Logger logger = LoggerFactory.getLogger(SchedulePaymentService.class);

    private final SchedulePaymentRepository schedulePaymentRepository;

    @Autowired
    public SchedulePaymentService(SchedulePaymentRepository schedulePaymentRepository) {
        this.schedulePaymentRepository = schedulePaymentRepository;
    }

    public Set<SchedulePayment> generateScheduleForOffer(Offer offer, List<SchedulePaymentDto> scheduleDtoList) {
        logger.info("Generate schedule for offer");
        return scheduleDtoList.stream()
                .map(SchedulePayment::new)
                .map(schedulePayment -> {
                    schedulePayment.setOffer(offer);
                    return schedulePaymentRepository.save(schedulePayment);
                }).collect(Collectors.toSet());
    }

    public List<SchedulePaymentDto> generateSchedule(BigDecimal amountCredit,
                                                  BigDecimal paymentMonth,
                                                  BigDecimal interestRate,
                                                  Integer countMonth,
                                                  Integer datePayment) {
        BigDecimal monthRate = computeMonthRate(interestRate);
        List<SchedulePaymentDto> scheduleList = new ArrayList<>();
        LocalDate localDate = LocalDate.of(LocalDate.now().getYear(), LocalDate.now().getMonth(), datePayment);

        for (int i = 0; i < countMonth; i++) {
            localDate = localDate.plusMonths(1);
            BigDecimal interestRepayment = amountCredit.multiply(monthRate);
            BigDecimal bodyRepayment = paymentMonth.subtract(interestRepayment);
            Date date = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

            scheduleList.add(new SchedulePaymentDto(
                    date,
                    paymentMonth.setScale(2, RoundingMode.HALF_EVEN),
                    bodyRepayment.setScale(2, RoundingMode.HALF_EVEN),
                    interestRepayment.setScale(2, RoundingMode.HALF_EVEN)
            ));

            amountCredit = amountCredit.subtract(bodyRepayment);
        }

        return scheduleList;
    }

    /* Payment = Limit * (MonthRate + MonthRate / (1 + MonthRate) ^ CountMonth - 1)
    MonthRate = AnnualRate / 100 / 12  */
    public BigDecimal computeMonthlyPayment(BigDecimal limitOn, BigDecimal interestRate, Integer countMonth) {
        BigDecimal monthRate = computeMonthRate(interestRate);

        BigDecimal denominator = monthRate.add(BigDecimal.ONE)
                .pow(countMonth)
                .subtract(BigDecimal.ONE);

        return monthRate.divide(denominator, 8, RoundingMode.HALF_UP)
                .add(monthRate)
                .multiply(limitOn);
    }

    private BigDecimal computeMonthRate(BigDecimal interestRate) {
        return interestRate.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(12), 8, RoundingMode.HALF_UP);
    }
}
