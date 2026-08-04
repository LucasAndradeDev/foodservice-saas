package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.CashRegisterSession;
import com.example.restaurant_saas.domain.entity.CashWithdrawal;
import com.example.restaurant_saas.domain.enums.CashRegisterSessionStatus;
import com.example.restaurant_saas.dto.request.CashWithdrawalRequest;
import com.example.restaurant_saas.dto.request.CloseCashRegisterRequest;
import com.example.restaurant_saas.dto.request.OpenCashRegisterRequest;
import com.example.restaurant_saas.dto.response.CashRegisterSessionResponse;
import com.example.restaurant_saas.dto.response.CashWithdrawalResponse;
import com.example.restaurant_saas.repository.CashRegisterSessionRepository;
import com.example.restaurant_saas.repository.CashWithdrawalRepository;
import com.example.restaurant_saas.repository.PaymentRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CashRegisterService {

    private final CashRegisterSessionRepository sessionRepository;
    private final CashWithdrawalRepository withdrawalRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public Optional<CashRegisterSessionResponse> getCurrentSession(UUID restaurantId) {
        return sessionRepository.findByRestaurantIdAndStatus(restaurantId, CashRegisterSessionStatus.OPEN)
                .map(session -> toResponse(session, calculateExpectedAmount(session, OffsetDateTime.now())));
    }

    @Transactional(readOnly = true)
    public List<CashRegisterSessionResponse> listHistory(UUID restaurantId) {
        return sessionRepository.findTop30ByRestaurantIdOrderByOpenedAtDesc(restaurantId).stream()
                .map(session -> toResponse(session, session.getExpectedAmount()))
                .toList();
    }

    @Transactional
    public CashRegisterSessionResponse openSession(UUID restaurantId, UUID userId, OpenCashRegisterRequest request) {
        if (sessionRepository.existsByRestaurantIdAndStatus(restaurantId, CashRegisterSessionStatus.OPEN)) {
            throw new IllegalStateException("Já existe um caixa aberto.");
        }

        CashRegisterSession session = CashRegisterSession.builder()
                .restaurant(restaurantRepository.getReferenceById(restaurantId))
                .openedAt(OffsetDateTime.now())
                .openedBy(userRepository.getReferenceById(userId))
                .openingAmount(request.getOpeningAmount())
                .status(CashRegisterSessionStatus.OPEN)
                .build();

        session = sessionRepository.save(session);
        return toResponse(session, session.getOpeningAmount());
    }

    @Transactional
    public CashRegisterSessionResponse closeSession(UUID restaurantId, UUID userId, CloseCashRegisterRequest request) {
        CashRegisterSession session = findOpenSession(restaurantId);

        OffsetDateTime now = OffsetDateTime.now();
        BigDecimal expectedAmount = calculateExpectedAmount(session, now);
        BigDecimal differenceAmount = request.getCountedAmount().subtract(expectedAmount);

        if (differenceAmount.compareTo(BigDecimal.ZERO) != 0
                && (request.getClosingNotes() == null || request.getClosingNotes().isBlank())) {
            throw new IllegalArgumentException("Informe uma observação quando o valor contado for diferente do esperado.");
        }

        session.setClosedAt(now);
        session.setClosedBy(userRepository.getReferenceById(userId));
        session.setCountedAmount(request.getCountedAmount());
        session.setExpectedAmount(expectedAmount);
        session.setDifferenceAmount(differenceAmount);
        session.setClosingNotes(request.getClosingNotes());
        session.setStatus(CashRegisterSessionStatus.CLOSED);

        session = sessionRepository.save(session);
        return toResponse(session, expectedAmount);
    }

    @Transactional
    public CashWithdrawalResponse addWithdrawal(UUID restaurantId, UUID userId, CashWithdrawalRequest request) {
        CashRegisterSession session = findOpenSession(restaurantId);

        CashWithdrawal withdrawal = CashWithdrawal.builder()
                .session(session)
                .amount(request.getAmount())
                .reason(request.getReason())
                .withdrawnBy(userRepository.getReferenceById(userId))
                .build();

        return toResponse(withdrawalRepository.save(withdrawal));
    }

    public void requireOpenSession(UUID restaurantId) {
        if (!sessionRepository.existsByRestaurantIdAndStatus(restaurantId, CashRegisterSessionStatus.OPEN)) {
            throw new IllegalStateException("Abra o caixa antes de receber pagamento em dinheiro.");
        }
    }

    private CashRegisterSession findOpenSession(UUID restaurantId) {
        return sessionRepository.findByRestaurantIdAndStatus(restaurantId, CashRegisterSessionStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException("Não há caixa aberto."));
    }

    private BigDecimal calculateExpectedAmount(CashRegisterSession session, OffsetDateTime until) {
        BigDecimal cashPayments = paymentRepository.sumActiveCashAmountByRestaurantIdAndPaidAtBetween(
                session.getRestaurant().getId(), session.getOpenedAt(), until);
        BigDecimal withdrawals = withdrawalRepository.findBySessionIdOrderByWithdrawnAtDesc(session.getId()).stream()
                .map(CashWithdrawal::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return session.getOpeningAmount().add(cashPayments).subtract(withdrawals);
    }

    private CashRegisterSessionResponse toResponse(CashRegisterSession session, BigDecimal expectedAmount) {
        List<CashWithdrawalResponse> withdrawals = withdrawalRepository.findBySessionIdOrderByWithdrawnAtDesc(session.getId()).stream()
                .map(this::toResponse)
                .toList();

        return CashRegisterSessionResponse.builder()
                .id(session.getId())
                .status(session.getStatus())
                .openedAt(session.getOpenedAt())
                .openedByName(session.getOpenedBy() != null ? session.getOpenedBy().getName() : null)
                .openingAmount(session.getOpeningAmount())
                .expectedAmount(expectedAmount)
                .countedAmount(session.getCountedAmount())
                .differenceAmount(session.getDifferenceAmount())
                .closedAt(session.getClosedAt())
                .closedByName(session.getClosedBy() != null ? session.getClosedBy().getName() : null)
                .closingNotes(session.getClosingNotes())
                .withdrawals(withdrawals)
                .build();
    }

    private CashWithdrawalResponse toResponse(CashWithdrawal withdrawal) {
        return CashWithdrawalResponse.builder()
                .id(withdrawal.getId())
                .amount(withdrawal.getAmount())
                .reason(withdrawal.getReason())
                .withdrawnByName(withdrawal.getWithdrawnBy() != null ? withdrawal.getWithdrawnBy().getName() : null)
                .withdrawnAt(withdrawal.getWithdrawnAt())
                .build();
    }
}
