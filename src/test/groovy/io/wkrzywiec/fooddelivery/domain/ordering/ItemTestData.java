package io.wkrzywiec.fooddelivery.domain.ordering;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
class ItemTestData {

    private String name = "Pizza Margherita";
    private int amount = 1;
    private BigDecimal pricePerItem = new BigDecimal(10);

    private ItemTestData() {};

    Item entity() {
        return Item.builder()
                .name(name)
                .amount(amount)
                .pricePerItem(pricePerItem)
                .build();
    }

    io.wkrzywiec.fooddelivery.domain.ordering.incoming.Item dto() {
        return new io.wkrzywiec.fooddelivery.domain.ordering.incoming.Item(name, amount, pricePerItem);
    }

    static ItemTestData anItem() {
        return new ItemTestData();
    }

    ItemTestData withName(String name) {
        this.name = name;
        return this;
    }

    ItemTestData withPricePerItem(double price) {
        this.pricePerItem = new BigDecimal(price);
        return this;
    }

    ItemTestData withAmount(int amount) {
        this.amount = amount;
        return this;
    }
}
