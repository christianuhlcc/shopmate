package com.shopmate.infrastructure.config;

import com.shopmate.adapter.out.persistence.entity.ShoppingItemEntity;
import com.shopmate.adapter.out.persistence.repository.SpringDataShoppingItemRepository;
import com.shopmate.domain.section.Section;
import com.shopmate.domain.section.SectionClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * One-time prod backfill for items created before the SECTION field existed
 * (ADR-0012 / section-grouping plan Phase 2).
 *
 * Idempotent: targets only items still at the seed timestamp
 * {@code section_ts = 0}. Writes classified sections at
 * {@code section_ts = 1} so any later real user change (always stamped with
 * {@code System.currentTimeMillis()}, which vastly exceeds 1) wins the LWW
 * merge forever. Runs on every boot but is a no-op once every item has been
 * classified at least once.
 *
 * Infrastructure may depend on adapters directly (ArchUnit only constrains
 * domain/application) — this keeps the migration itself pure DDL.
 */
@Component
public class SectionBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SectionBackfillRunner.class);

    /** Any real user change is stamped with epoch millis, dwarfing this — see class javadoc. */
    static final long BACKFILL_TIMESTAMP = 1L;
    private static final long UNCLASSIFIED_TS = 0L;

    private final SpringDataShoppingItemRepository itemRepository;
    private final SectionClassifier sectionClassifier;

    public SectionBackfillRunner(SpringDataShoppingItemRepository itemRepository,
                                  SectionClassifier sectionClassifier) {
        this.itemRepository = itemRepository;
        this.sectionClassifier = sectionClassifier;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<ShoppingItemEntity> unclassified = itemRepository.findBySectionTs(UNCLASSIFIED_TS);
        if (unclassified.isEmpty()) {
            return;
        }
        for (ShoppingItemEntity item : unclassified) {
            Section section = sectionClassifier.classify(item.getNameValue());
            item.setSectionValue(section.name());
            item.setSectionTs(BACKFILL_TIMESTAMP);
            item.setSectionModifiedBy(item.getNameModifiedBy());
        }
        itemRepository.saveAll(unclassified);
        log.info("Section backfill classified {} item(s)", unclassified.size());
    }
}
