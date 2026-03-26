package org.bring.freshmarkt.sync.sync;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncStateRepository extends JpaRepository<SyncState, Long> {
}
