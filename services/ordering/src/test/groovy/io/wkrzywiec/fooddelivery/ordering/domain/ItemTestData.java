package io.wkrzywiec.fooddelivery.ordering.domain;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
class ItemTestData {

    private String name = "Pizza Margherita";
    private int amount = 1;
    private BigDecimal pricePerItem = new BigDecimal(10);

    private ItemTestData() {};

    public Item entity() {
        return Item.builder()
                .name(name)
                .amount(amount)
                .pricePerItem(pricePerItem)
                .build();
    }

    public io.wkrzywiec.fooddelivery.commons.model.Item dto() {
        return new io.wkrzywiec.fooddelivery.commons.model.Item(name, amount, pricePerItem);
    }

    public static ItemTestData anItem() {
        return new ItemTestData();
    }

    public ItemTestData withName(String name) {
        this.name = name;
        return this;
    }

    public ItemTestData withPricePerItem(double price) {
        this.pricePerItem = new BigDecimal(price);
        return this;
    }

    ItemTestData withAmount(int amount) {
        this.amount = amount;
        return this;
    }
}
