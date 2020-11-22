package com.liang.study.entities;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author liangyt
 */
@NoArgsConstructor
@Data
public class Order {
    private Long id;

    private String  orderName;
    private Integer orderStatus; // 0待支付  1已支付 2已超时
    private String  orderToken;
    private String  orderSerial;

    public Order(String orderName, Integer orderStatus, String orderToken, String orderSerial)
    {
        this.orderName = orderName;
        this.orderStatus = orderStatus;
        this.orderToken = orderToken;
        this.orderSerial = orderSerial;
    }
}
