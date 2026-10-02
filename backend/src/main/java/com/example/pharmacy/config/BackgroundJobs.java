package com.example.pharmacy.config;

import com.example.pharmacy.common.Store;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BackgroundJobs {

  private final Store db;
  private final RabbitTemplate rabbit;
  private final boolean enabled;

  public BackgroundJobs(
    Store db,
    RabbitTemplate rabbit,
    @Value("${app.messaging-enabled}") boolean enabled
  ) {
    this.db = db;
    this.rabbit = rabbit;
    this.enabled = enabled;
  }

  @Scheduled(fixedDelayString = "${app.outbox-publish-delay}")
  @Transactional
  public void publish() {
    if (!enabled) return;
    for (var row : db.list(
      "SELECT * FROM outbox_events WHERE published_at IS NULL ORDER BY id LIMIT 20 FOR UPDATE SKIP LOCKED"
    )) {
      try {
        var correlation = new CorrelationData(row.get("id").toString());
        rabbit.convertAndSend(
          "pharmacy.events",
          "events",
          row.get("payload").toString(),
          m -> {
            m.getMessageProperties().setMessageId(row.get("id").toString());
            return m;
          },
          correlation
        );
        var confirm = correlation.getFuture().get(3, TimeUnit.SECONDS);
        if (!confirm.isAck() || correlation.getReturned() != null) throw new IllegalStateException(
          "Broker did not route/confirm event"
        );
        db.update("UPDATE outbox_events SET published_at=now() WHERE id=?", row.get("id"));
      } catch (Exception e) {
        org.slf4j.LoggerFactory.getLogger(getClass()).warn(
          "Outbox delivery pending for event {}",
          row.get("id")
        );
        break;
      }
    }
  }

  @Scheduled(fixedDelayString = "${app.maintenance-delay}")
  public void maintenance() {
    db.update("DELETE FROM auth_tokens WHERE expires_at<now()");
    db.update("DELETE FROM rate_limits WHERE window_end<now()-interval '1 day'");
    db.update(
      "UPDATE batches SET status='EXPIRED',updated_at=now() WHERE expiry_date<CURRENT_DATE AND status='ACTIVE'"
    );
  }
}
