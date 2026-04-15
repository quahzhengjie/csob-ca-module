-- CSOB CA Module - schema v1
--
-- Two tables for minimal persistence in this milestone:
--   csob_ca_packs      - one row per pack. Written once by PersistStep.
--                        Nested DTOs serialise to JSON/CLOB columns to keep
--                        the schema compact; normalisation is future work.
--   csob_ca_audit_log  - append-only, hash-chained event trail per pack.
--
-- Database: H2 for this milestone. SQL is kept dialect-agnostic where
-- possible so the same migration can run against MSSQL with minor tweaks.
--
-- Row-level append-only / immutability is enforced by application code
-- (JpaPackRepository never updates a pack row once inserted). The DB
-- schema does not hard-lock rows because ops tooling may need to correct
-- state under operator authority.

CREATE TABLE csob_ca_packs (
    pack_id                 VARCHAR(64)  NOT NULL PRIMARY KEY,
    pack_version            INT          NOT NULL,
    party_id                VARCHAR(64)  NOT NULL,
    status                  VARCHAR(32)  NOT NULL,
    created_by              VARCHAR(128) NOT NULL,
    created_at              TIMESTAMP    NOT NULL,
    checklist_version       VARCHAR(32)  NOT NULL,
    prompt_version          VARCHAR(32)  NOT NULL,
    model_id                VARCHAR(128),
    model_version           VARCHAR(64),
    tool_outputs_hash_root  VARCHAR(64)  NOT NULL,
    party_facts_json        CLOB         NOT NULL,
    checklist_result_json   CLOB         NOT NULL,
    tool_outputs_json       CLOB         NOT NULL,
    raw_ai_output_json      CLOB,
    validation_report_json  CLOB,
    final_summary_json      CLOB,
    reviewer_actions_json   CLOB         NOT NULL,
    sign_off_chain_json     CLOB         NOT NULL
);

CREATE INDEX idx_csob_ca_packs_party_id   ON csob_ca_packs(party_id);
CREATE INDEX idx_csob_ca_packs_created_at ON csob_ca_packs(created_at);

CREATE TABLE csob_ca_audit_log (
    event_id     VARCHAR(64)  NOT NULL PRIMARY KEY,
    pack_id      VARCHAR(64)  NOT NULL,
    event_type   VARCHAR(64)  NOT NULL,
    event_json   CLOB         NOT NULL,
    prev_hash    VARCHAR(64),
    hash         VARCHAR(64)  NOT NULL,
    occurred_at  TIMESTAMP    NOT NULL
);

CREATE INDEX idx_csob_ca_audit_log_pack_id
    ON csob_ca_audit_log(pack_id);
CREATE INDEX idx_csob_ca_audit_log_pack_time
    ON csob_ca_audit_log(pack_id, occurred_at);
