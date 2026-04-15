package com.csob.ca.persistence.repository;

import com.csob.ca.shared.dto.CaPackDto;
import com.csob.ca.shared.dto.ToolOutputDto;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port used by ca-orchestration (via PersistStep) and by the
 * read-side + replay endpoints in ca-api.
 *
 * v1 minimal surface:
 *   - {@link #persistPack(CaPackDto, List)} — writes the full aggregate
 *     (pack row + tool outputs serialised alongside as JSON) in one txn.
 *     Callers MUST NOT call this more than once for a given packId.
 *   - {@link #findById(String)} — loads the persisted CaPackDto.
 *   - {@link #findToolOutputsByPackId(String)} — loads the frozen tool
 *     outputs; needed for replay because CaPackDto does not carry them.
 *
 * Removed vs scaffold: {@code appendValidation}, {@code updateStatus},
 * {@code nextPackVersion}. These were speculative; for v1 persistence the
 * pack is written atomically once and not updated in place.
 */
public interface PackRepository {

    /**
     * Write the full pack aggregate + frozen ToolOutputs in a single
     * transaction. Throws {@link IllegalStateException} if a row already
     * exists for {@code pack.packId()} — pack persistence is append-only
     * at the application layer.
     */
    void persistPack(CaPackDto pack, List<ToolOutputDto> toolOutputs);

    /**
     * @return the persisted pack reconstructed into a {@link CaPackDto},
     *         or empty if no pack with that id exists.
     */
    Optional<CaPackDto> findById(String packId);

    /**
     * @return the frozen ToolOutputs persisted alongside this pack, in
     *         stable insertion order. Empty list if the pack is unknown.
     */
    List<ToolOutputDto> findToolOutputsByPackId(String packId);
}
