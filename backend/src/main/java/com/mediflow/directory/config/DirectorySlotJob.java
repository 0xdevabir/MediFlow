package com.mediflow.directory.config;

import com.mediflow.directory.service.SlotGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Nightly (and on-startup) materialisation of slots {@code slotHorizonDays} ahead. */
@Configuration
@EnableScheduling
public class DirectorySlotJob {

  private static final Logger log = LoggerFactory.getLogger(DirectorySlotJob.class);

  private final SlotGenerationService generator;
  private final DirectoryProperties props;

  public DirectorySlotJob(SlotGenerationService generator, DirectoryProperties props) {
    this.generator = generator;
    this.props = props;
  }

  @Scheduled(cron = "${mediflow.directory.slot-cron:0 15 2 * * *}", zone = "${mediflow.directory.zone:Asia/Dhaka}")
  public void nightly() {
    run("nightly");
  }

  /** Runs after the dev seeder (order 0). */
  @EventListener(ApplicationReadyEvent.class)
  @Order(100)
  public void onStartup() {
    if (props.generateOnStartup()) {
      run("startup");
    }
  }

  private void run(String trigger) {
    try {
      int created = generator.reconcileAll();
      log.info("Directory slot job ({}): {} slots created", trigger, created);
    } catch (RuntimeException e) {
      log.error("Directory slot job ({}) failed", trigger, e);
    }
  }
}
