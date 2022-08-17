package io.wkrzywiec.fooddelivery.ordering


import io.wkrzywiec.fooddelivery.commons.infra.messaging.FakeMessagePublisher
import io.wkrzywiec.fooddelivery.commons.infra.messaging.Message
import io.wkrzywiec.fooddelivery.commons.incoming.AddTip
import io.wkrzywiec.fooddelivery.commons.incoming.CancelOrder
import io.wkrzywiec.fooddelivery.ordering.incoming.FoodDelivered
import io.wkrzywiec.fooddelivery.ordering.incoming.FoodInPreparation
import io.wkrzywiec.fooddelivery.ordering.outgoing.OrderCanceled
import io.wkrzywiec.fooddelivery.ordering.outgoing.OrderCompleted
import io.wkrzywiec.fooddelivery.ordering.outgoing.OrderCreated
import io.wkrzywiec.fooddelivery.ordering.outgoing.OrderInProgress
import io.wkrzywiec.fooddelivery.ordering.outgoing.OrderProcessingError
import io.wkrzywiec.fooddelivery.ordering.outgoing.TipAddedToOrder
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Title

import java.time.Clock
import java.time.Instant

import static ItemTestData.anItem
import static OrderTestData.anOrder

@Subject(OrderingFacade)
@Title("Specification for ordering process")
class OrderingFacadeSpec extends Specification {

    private final String ORDERS_CHANNEL = "orders"

    OrderingFacade facade
    InMemoryOrderingRepository repository
    FakeMessagePublisher publisher

    var testTime = Instant.parse("2022-08-08T05:30:24.00Z")
    Clock testClock = Clock.fixed(testTime)

    def setup() {
        repository = new InMemoryOrderingRepository()
        publisher = new FakeMessagePublisher()
        facade = new OrderingFacade(repository, publisher, testClock)
    }

    def "Create an order"() {
        given:
        var order = anOrder()
                .withItems(
                        anItem().withName("Pizza").withPricePerItem(2.5),
                        anItem().withName("Spaghetti").withPricePerItem(3.0)
                )

        when:
        facade.handle(order.createOrder())

        then: "Order is saved"
        with(repository.database.values().find() as Order) { savedOrder ->
            savedOrder.id != null
            savedOrder.customerId == order.getCustomerId()
            savedOrder.restaurantId == order.getRestaurantId()
            savedOrder.address == order.getAddress()
            savedOrder.items == order.getItems().stream().map(ItemTestData::entity).toList()
            savedOrder.status == OrderStatus.CREATED
            savedOrder.deliveryCharge == order.getDeliveryCharge()
            savedOrder.tip == 0
            savedOrder.total == 5.5 + order.getDeliveryCharge()
        }


        and: "OrderCreated event is published on 'orders' channel"
        String orderId = repository.database.values().find().id
        with(publisher.messages.get(ORDERS_CHANNEL).get(0)) {event ->

            verifyEventHeader(event, order.id, "OrderCreated")

            def body = event.body() as OrderCreated
            body.orderId() == orderId
            body.customerId() == order.getCustomerId()
            body.restaurantId() == order.getRestaurantId()
            body.address() == order.getAddress()
            body.items() == order.getItems().stream().map(ItemTestData::dto).toList()
            body.deliveryCharge() == order.getDeliveryCharge()
            body.total() == 5.5 + order.getDeliveryCharge()
        }
    }

    def "Cancel an order"() {
        given:
        var order = anOrder()
        repository.save(order.entity())

        and:
        var cancellationReason = "Not hungry anymore"
        var cancelOrder = new CancelOrder(order.id, cancellationReason)

        when:
        facade.handle(cancelOrder)

        then: "Order is canceled"
        with(repository.findById(order.id).get()) { cancelledOrder ->
            cancelledOrder.status == OrderStatus.CANCELED
            cancelledOrder.metadata.get("cancellationReason") == cancellationReason
        }

        and: "OrderCancelled event is published on 'orders' channel"
        with(publisher.messages.get(ORDERS_CHANNEL).get(0)) {event ->

            verifyEventHeader(event, order.id, "OrderCancelled")

            def body = event.body() as OrderCanceled
            body.id() == order.id
            body.reason() == cancellationReason
        }
    }

    def "Fail to cancel a #status order"() {
        given:
        var order = anOrder().withStatus(status)
        repository.save(order.entity())

        and:
        var cancelOrder = new CancelOrder(order.id, "Not hungry anymore")

        when:
        facade.handle(cancelOrder)

        then: "Order is not canceled"
        with(repository.findById(order.id).get()) { cancelledOrder ->
            cancelledOrder.status == order.getStatus()
        }

        and: "OrderProcessingError event is published on 'orders' channel"
        with(publisher.messages.get(ORDERS_CHANNEL).get(0)) {event ->

            verifyEventHeader(event, order.id, "OrderProcessingError")

            def body = event.body() as OrderProcessingError
            body.id() == order.id
            body.details() == "Failed to cancel an $order.id order. It's not possible to cancel an order with '$status' status"
        }

        where:
        status << [OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED, OrderStatus.CANCELED]
    }

    def "Set order to IN_PROGRESS"() {
        given:
        var order = anOrder()
        repository.save(order.entity())

        and:
        var foodInPreparation = new FoodInPreparation(order.id)

        when:
        facade.handle(foodInPreparation)

        then: "Order is IN_PROGRESS"
        with(repository.findById(order.id).get()) { cancelledOrder ->
            cancelledOrder.status == OrderStatus.IN_PROGRESS
        }

        and: "OrderInProgress event is published on 'orders' channel"
        with(publisher.messages.get(ORDERS_CHANNEL).get(0)) {event ->

            verifyEventHeader(event, order.id, "OrderInProgress")

            def body = event.body() as OrderInProgress
            body.id() == order.id
        }
    }

    def "Fail to set IN_PROGRESS a #status order"() {
        given:
        var order = anOrder().withStatus(status)
        repository.save(order.entity())

        and:
        var foodInPreparation = new FoodInPreparation(order.id)

        when:
        facade.handle(foodInPreparation)

        then: "Order has not changed a status"
        with(repository.findById(order.id).get()) { cancelledOrder ->
            cancelledOrder.status == order.getStatus()
        }

        and: "OrderProcessingError event is published on 'orders' channel"
        with(publisher.messages.get(ORDERS_CHANNEL).get(0)) {event ->

            verifyEventHeader(event, order.id, "OrderProcessingError")

            def body = event.body() as OrderProcessingError
            body.id() == order.id
            body.details() == "Failed to set an '$order.id' order to IN_PROGRESS. It's not allowed to do it for an order with '$status' status"
        }

        where:
        status << [OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED, OrderStatus.CANCELED]
    }

    def "Add tip to an order"() {
        given:
        double itemCost = 10
        double deliveryCharge = 5

        var order = anOrder()
                .withItems(anItem().withPricePerItem(itemCost))
                .withDeliveryCharge(deliveryCharge)
        repository.save(order.entity())

        and:
        double tip = 20
        var addTip = new AddTip(order.id, new BigDecimal(tip))

        when:
        facade.handle(addTip)

        then: "Tip was added"
        double total = itemCost + deliveryCharge + tip
        with(repository.findById(order.id).get()) { orderEntity ->
            orderEntity.tip.doubleValue() == tip
            orderEntity.total.doubleValue() == total
        }

        and: "TipAddedToOrder event is published on 'orders' channel"
        with(publisher.messages.get(ORDERS_CHANNEL).get(0)) {event ->

            verifyEventHeader(event, order.id, "TipAddedToOrder")

            def body = event.body() as TipAddedToOrder
            body.orderId() == order.id
            body.tip().doubleValue() == tip
            body.total().doubleValue() == total
        }
    }

    def "Complete an order"() {
        given:
        var order = anOrder().withStatus(OrderStatus.IN_PROGRESS)
        repository.save(order.entity())

        and:
        var foodDelivered = new FoodDelivered(order.id)

        when:
        facade.handle(foodDelivered)

        then: "Order is completed"
        with(repository.findById(order.id).get()) { cancelledOrder ->
            cancelledOrder.status == OrderStatus.COMPLETED
        }

        and: "OrderCompleted event is published on 'orders' channel"
        with(publisher.messages.get(ORDERS_CHANNEL).get(0)) {event ->

            verifyEventHeader(event, order.id, "OrderCompleted")

            def body = event.body() as OrderCompleted
            body.orderId() == order.id
        }
    }

    def "Fail to complete a #status order"() {
        given:
        var order = anOrder().withStatus(status)
        repository.save(order.entity())

        and:
        var foodDelivered = new FoodDelivered(order.id)

        when:
        facade.handle(foodDelivered)

        then: "Order has not changed a status"
        with(repository.findById(order.id).get()) { cancelledOrder ->
            cancelledOrder.status == order.getStatus()
        }

        and: "OrderProcessingError event is published on 'orders' channel"
        with(publisher.messages.get(ORDERS_CHANNEL).get(0)) {event ->

            verifyEventHeader(event, order.id, "OrderProcessingError")

            def body = event.body() as OrderProcessingError
            body.id() == order.id
            body.details() == "Failed to set an '$order.id' order to COMPLETED. It's not allowed to do it for an order with '$status' status"
        }

        where:
        status << [OrderStatus.CREATED, OrderStatus.COMPLETED, OrderStatus.CANCELED]
    }

    private void verifyEventHeader(Message event, String orderId, String eventType) {
        def header = event.header()
        header.messageId() != null
        header.channel() == ORDERS_CHANNEL
        header.type() == eventType
        header.itemId() == orderId
        header.createdAt() == testClock.instant()
    }
}
