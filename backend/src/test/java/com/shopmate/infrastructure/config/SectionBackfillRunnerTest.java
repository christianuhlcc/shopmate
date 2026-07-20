package com.shopmate.infrastructure.config;

import com.shopmate.adapter.out.persistence.entity.ShoppingItemEntity;
import com.shopmate.adapter.out.persistence.entity.ShoppingListEntity;
import com.shopmate.adapter.out.persistence.repository.SpringDataShoppingItemRepository;
import com.shopmate.domain.section.SectionClassifier;
import com.shopmate.domain.section.SectionDictionary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SectionBackfillRunnerTest {

    @Mock SpringDataShoppingItemRepository itemRepository;

    private final SectionClassifier sectionClassifier = new SectionClassifier(new SectionDictionary());

    private ShoppingItemEntity itemWithNameAndSectionTs(String name, long sectionTs, UUID nameModifiedBy) {
        ShoppingListEntity list = new ShoppingListEntity(UUID.randomUUID(), "Groceries", nameModifiedBy,
            UUID.randomUUID(), Instant.now());
        ShoppingItemEntity item = new ShoppingItemEntity(
            UUID.randomUUID(), list,
            name, 100L, nameModifiedBy,
            "1", 100L, nameModifiedBy,
            false, 100L, nameModifiedBy,
            false, 100L, nameModifiedBy,
            "a0", 100L, nameModifiedBy,
            "SONSTIGES", 0L, nameModifiedBy);
        item.setSectionTs(sectionTs);
        return item;
    }

    @Test
    void classifiesAndStampsUnclassifiedItemsAtTimestampOne() {
        UUID user = UUID.randomUUID();
        ShoppingItemEntity item = itemWithNameAndSectionTs("Kirschtomaten", 0L, user);
        when(itemRepository.findBySectionTs(0L)).thenReturn(List.of(item));

        SectionBackfillRunner runner = new SectionBackfillRunner(itemRepository, sectionClassifier);
        runner.run(null);

        assertThat(item.getSectionValue()).isEqualTo("OBST_GEMUESE");
        assertThat(item.getSectionTs()).isEqualTo(SectionBackfillRunner.BACKFILL_TIMESTAMP);
        assertThat(item.getSectionModifiedBy()).isEqualTo(user);

        ArgumentCaptor<List<ShoppingItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(itemRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(item);
    }

    @Test
    void unknownNameFallsBackToSonstiges() {
        UUID user = UUID.randomUUID();
        ShoppingItemEntity item = itemWithNameAndSectionTs("Xyzzyfoobar123", 0L, user);
        when(itemRepository.findBySectionTs(0L)).thenReturn(List.of(item));

        new SectionBackfillRunner(itemRepository, sectionClassifier).run(null);

        assertThat(item.getSectionValue()).isEqualTo("SONSTIGES");
        assertThat(item.getSectionTs()).isEqualTo(SectionBackfillRunner.BACKFILL_TIMESTAMP);
    }

    @Test
    void isIdempotentNoOpWhenNothingUnclassified() {
        when(itemRepository.findBySectionTs(0L)).thenReturn(List.of());

        new SectionBackfillRunner(itemRepository, sectionClassifier).run(null);

        verify(itemRepository, never()).saveAll(eq(List.of()));
        verify(itemRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }
}
