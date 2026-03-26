package org.bring.freshmarkt.sync.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bring.freshmarkt.sync.client.dto.ProductListResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class FreshMarktSyncService {

    private final SyncStateRepository syncStateRepository;
    private final FreshMarktProductPersistenceService persistenceService;
    private final FreshMarktSyncRetryHelper freshMarktSyncRetryHelper;
    private static final long MS_BETWEEN_REQUESTS = 1100L; // based on 60req/min


    @Scheduled(cron = "${freshmarkt.sync.cron}")
    public void sync() {
        log.info("Starting FreshMarkt sync");
        Instant updatedSince = getLastSuccessfulSync();
        try {
            syncAllPages(updatedSince);
            saveLastSuccessfulSync(Instant.now());
            log.info("FreshMarktSync completed successfully");
        } catch (Exception e) {
            log.error("FreshMarkt sync failed", e);
        }
    }
    private void syncAllPages(Instant updatedSince) {
        int page = 1;
        int totalPages;
        do {
            ProductListResponse response = freshMarktSyncRetryHelper.fetchWithRetry(page, updatedSince);
            persistenceService.processPage(response.data());
            totalPages = response.pagination().totalPages();
            page++;

            if (page <= totalPages) {
                throttle(MS_BETWEEN_REQUESTS); // proactive throttle;
            }
        } while (page <= totalPages);
        log.info("Sync complete. Reached last page: {}", page - 1);
    }

    private Instant getLastSuccessfulSync() {
        return syncStateRepository.findById(1L)
                .map(SyncState::getLastSuccessfulSync)
                .orElse(null); // null = first run, full sync
    }

    private void saveLastSuccessfulSync(Instant instant) {
        SyncState state = syncStateRepository.findById(1L)
                .orElse(new SyncState(instant));
        state.setLastSuccessfulSync(instant);
        syncStateRepository.save(state);
    }

    private void throttle(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during retry wait", ex);
        }
    }
}
