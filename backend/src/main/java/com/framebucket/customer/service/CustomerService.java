package com.framebucket.customer.service;

import com.framebucket.customer.api.CustomerDtos.CustomerRegistrationRequest;
import com.framebucket.customer.api.CustomerDtos.CustomerResponse;
import com.framebucket.customer.model.Customer;
import com.framebucket.customer.repository.CustomerRepository;
import com.framebucket.common.money.PriceAmountParser;
import com.framebucket.common.phone.IndianMobileNumber;
import com.framebucket.event.model.EventBooking;
import com.framebucket.event.repository.EventBookingRepository;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import com.framebucket.user.auth.repository.PhotographerUserRepository;
import com.framebucket.user.auth.service.SubIdService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class CustomerService {
    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

    private final CustomerRepository customerRepository;
    private final EventBookingRepository eventRepository;
    private final PhotographerUserRepository photographerRepository;
    private final SubIdService subIdService;

    public CustomerService(
            CustomerRepository customerRepository,
            EventBookingRepository eventRepository,
            PhotographerUserRepository photographerRepository,
            SubIdService subIdService
    ) {
        this.customerRepository = customerRepository;
        this.eventRepository = eventRepository;
        this.photographerRepository = photographerRepository;
        this.subIdService = subIdService;
    }

    @Transactional
    public CustomerResponse register(PhotographerPrincipal photographer, CustomerRegistrationRequest request) {
        var owner = photographerRepository.findByIdForUpdate(photographer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photographer account not found."));
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String name = request.name().trim();
        String mobileNumber;
        try {
            mobileNumber = IndianMobileNumber.normalize(request.mobileNumber());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        if (customerRepository.existsByPhotographer_IdAndNameIgnoreCaseAndEmailIgnoreCaseAndMobileNumber(
                photographer.getId(), name, email, mobileNumber
        )) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This customer is already in your customer list.");
        }

        Customer customer = new Customer(
                owner,
                subIdService.getPlainSubId(owner) + "_" + String.format(Locale.ROOT, "%03d", allocateCustomerSequence(owner)),
                request.name(),
                email,
                mobileNumber,
                request.dateOfBirth(),
                request.address()
        );
        return toResponse(customerRepository.save(customer), ZERO_MONEY);
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> listForPhotographer(PhotographerPrincipal photographer) {
        List<Customer> customers = customerRepository.findAllByPhotographer_IdOrderByCreatedAtDesc(photographer.getId());
        Map<UUID, BigDecimal> revenueByCustomer = new HashMap<>();
        for (EventBooking event : eventRepository.findAllByPhotographer_IdOrderByEventDateAscCreatedAtAsc(photographer.getId())) {
            BigDecimal price = PriceAmountParser.parse(event.getPrice());
            if (price != null && price.signum() > 0) {
                revenueByCustomer.merge(event.getCustomer().getId(), price, BigDecimal::add);
            }
        }
        return customers.stream()
                .map(customer -> toResponse(
                        customer,
                        revenueByCustomer.getOrDefault(customer.getId(), ZERO_MONEY).setScale(2, RoundingMode.HALF_UP)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse getForPhotographer(PhotographerPrincipal photographer, UUID customerId) {
        Customer customer = customerRepository.findByIdAndPhotographer_Id(customerId, photographer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found."));
        BigDecimal totalRevenue = eventRepository.findAllByCustomer_IdAndPhotographer_Id(customerId, photographer.getId())
                .stream()
                .map(EventBooking::getPrice)
                .map(PriceAmountParser::parse)
                .filter(price -> price != null && price.signum() > 0)
                .reduce(ZERO_MONEY, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        return toResponse(customer, totalRevenue);
    }

    private static int allocateCustomerSequence(com.framebucket.user.auth.model.PhotographerUser owner) {
        try {
            return owner.allocateCustomerSequence();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
    }

    private static CustomerResponse toResponse(Customer customer, BigDecimal totalRevenue) {
        return new CustomerResponse(
                customer.getId(),
                customer.getCustomerCode(),
                customer.getName(),
                customer.getEmail(),
                customer.getMobileNumber(),
                customer.getDateOfBirth(),
                customer.getAddress(),
                customer.getEventsBooked(),
                totalRevenue,
                customer.getCreatedAt()
        );
    }
}
