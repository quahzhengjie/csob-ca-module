package com.csob.ca.persistence.readonly;

/**
 * Marker: this package holds READ-ONLY views into the upstream legacy KYC
 * store. No @Entity classes here may be annotated as writable. Used by
 * ca-tools adapters. No @OneToMany / @ManyToOne cascades.
 *
 * Implementations live alongside this marker and are purely projections.
 */
public interface KycReadOnlyView {
}
