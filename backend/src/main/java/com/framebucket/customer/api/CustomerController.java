package com.framebucket.customer.api;

import com.framebucket.customer.api.CustomerDtos.CustomerRegistrationRequest;
import com.framebucket.customer.api.CustomerDtos.CustomerResponse;
import com.framebucket.customer.service.CustomerService;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public List<CustomerResponse> listCustomers(@AuthenticationPrincipal PhotographerPrincipal photographer) {
        return customerService.listForPhotographer(photographer);
    }

    @GetMapping("/{customerId}")
    public CustomerResponse getCustomer(
            @AuthenticationPrincipal PhotographerPrincipal photographer,
            @PathVariable java.util.UUID customerId
    ) {
        return customerService.getForPhotographer(photographer, customerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse registerCustomer(
            @AuthenticationPrincipal PhotographerPrincipal photographer,
            @Valid @RequestBody CustomerRegistrationRequest request
    ) {
        return customerService.register(photographer, request);
    }
}
