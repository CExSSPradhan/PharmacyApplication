package com.example.pharmacy.config;

import com.example.pharmacy.common.Store;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration
@ConditionalOnProperty(name = "app.messaging-enabled", havingValue = "true")
public class MessagingConfig {

  @Bean
  Declarables queues() {
    var exchange = new DirectExchange("pharmacy.events", true, false);
    var dead = new DirectExchange("pharmacy.dead", true, false);
    var queue = QueueBuilder.durable("pharmacy.alerts")
      .deadLetterExchange("pharmacy.dead")
      .deadLetterRoutingKey("dead")
      .build();
    var dlq = new Queue("pharmacy.alerts.dlq", true);
    return new Declarables(
      exchange,
      dead,
      queue,
      dlq,
      BindingBuilder.bind(queue).to(exchange).with("events"),
      BindingBuilder.bind(dlq).to(dead).with("dead")
    );
  }

  @Bean
  Listener listener(Store db) {
    return new Listener(db);
  }

  public static class Listener {

    private final Store db;

    Listener(Store db) {
      this.db = db;
    }

    @RabbitListener(queues = "pharmacy.alerts")
    public void receive(Message message) {
      long event = Long.parseLong(message.getMessageProperties().getMessageId());
      long low = db.number(
        "SELECT count(*) FROM (" +
          com.example.pharmacy.inventory.InventoryService.STOCK +
          ") s WHERE status='ACTIVE' AND available_quantity<=low_stock_threshold"
      );
      long expiry = db.number(
        "SELECT count(*) FROM batches WHERE current_quantity>0 AND expiry_date<=CURRENT_DATE+30"
      );
      db.update(
        "INSERT INTO notifications(event_id,message) VALUES (?,?) ON CONFLICT(event_id) DO NOTHING",
        event,
        "Inventory check: " +
          low +
          " low-stock medicines, " +
          expiry +
          " batches expired or expiring within 30 days."
      );
    }
  }
}
