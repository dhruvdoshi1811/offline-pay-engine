package com.dhruv.offlinepayment_relay.controller;

import com.dhruv.offlinepayment_relay.dto.SimulateDuplicateDeliveryRequest;
import com.dhruv.offlinepayment_relay.dto.SimulateDuplicateDeliveryResponse;
import com.dhruv.offlinepayment_relay.service.DuplicateDeliveryDemoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
public class DemoController {

    private final DuplicateDeliveryDemoService demoService;

    public DemoController(DuplicateDeliveryDemoService demoService) {
        this.demoService = demoService;
    }

    @PostMapping("/simulate-duplicate-delivery")
    public ResponseEntity<SimulateDuplicateDeliveryResponse> simulateDuplicateDelivery(
            @Valid @RequestBody SimulateDuplicateDeliveryRequest request
    ) {
        return ResponseEntity.ok(demoService.simulate(request));
    }
}
