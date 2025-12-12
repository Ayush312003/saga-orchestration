package com.supplychain.order.controller;

import com.supplychain.common.dto.OrderRequestDto;
import com.supplychain.order.entity.PurchaseOrder;
import com.supplychain.order.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping("/create")
    public PurchaseOrder createOrder(@RequestBody OrderRequestDto orderRequest) {
        return orderService.createOrder(orderRequest);
    }
}