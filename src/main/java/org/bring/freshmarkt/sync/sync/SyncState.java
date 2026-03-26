package org.bring.freshmarkt.sync.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "sync_state")
@Getter
@Setter
public class SyncState {

    @Id
    private Long id;

    @Column(nullable = false)
    private Instant lastSuccessfulSync;

    protected SyncState() {

    }

    public SyncState(Instant lastSuccessfulSync) {
        this.id = 1L;
        this.lastSuccessfulSync = lastSuccessfulSync;
    }

}
