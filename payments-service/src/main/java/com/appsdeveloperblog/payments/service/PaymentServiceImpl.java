package com.appsdeveloperblog.payments.service;

import com.appsdeveloperblog.core.dto.Payment;
import com.appsdeveloperblog.payments.dao.jpa.entity.PaymentEntity;
import com.appsdeveloperblog.payments.dao.jpa.repository.PaymentRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PaymentServiceImpl implements PaymentService {
    // Numero sintetico: no supera el algoritmo de Luhn y su prefijo no pertenece a
    // ninguna red real, asi que no puede corresponder a una tarjeta existente.
    public static final String SAMPLE_CREDIT_CARD_NUMBER = "9999888877776666";
    private final PaymentRepository paymentRepository;
    private final CreditCardProcessorRemoteService ccpRemoteService;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              CreditCardProcessorRemoteService ccpRemoteService) {
        this.paymentRepository = paymentRepository;
        this.ccpRemoteService = ccpRemoteService;
    }

    @Override
    public Payment process(Payment payment) {
        BigDecimal totalPrice = payment.getProductPrice()
                .multiply(new BigDecimal(payment.getProductQuantity()));
        ccpRemoteService.process(new BigInteger(SAMPLE_CREDIT_CARD_NUMBER), totalPrice);
        PaymentEntity paymentEntity = new PaymentEntity();
        BeanUtils.copyProperties(payment, paymentEntity);
        paymentRepository.save(paymentEntity);

        var processedPayment = new Payment();
        BeanUtils.copyProperties(payment, processedPayment);
        processedPayment.setId(paymentEntity.getId());
        return processedPayment;
    }

    @Override
    public List<Payment> findAll() {
        return paymentRepository.findAll().stream().map(entity -> new Payment(entity.getId(), entity.getOrderId(), entity.getProductId(), entity.getProductPrice(), entity.getProductQuantity())
        ).collect(Collectors.toList());
    }
}
