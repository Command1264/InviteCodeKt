-- @table invitecode_players
CREATE TABLE invitecode_players (
    player_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    first_seen_at VARCHAR(64) CHARACTER SET ascii NOT NULL,
    last_known_name VARCHAR(64) NULL,
    eligibility_generation INT NOT NULL DEFAULT 0,
    PRIMARY KEY (player_id),
    KEY invitecode_query_players_by_name (last_known_name, player_id),
    CONSTRAINT invitecode_players_generation CHECK (eligibility_generation >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- @table invitecode_metadata
CREATE TABLE invitecode_metadata (
    singleton INT NOT NULL,
    network_key_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (singleton),
    CONSTRAINT invitecode_metadata_singleton CHECK (singleton = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- @table invitecode_codes
CREATE TABLE invitecode_codes (
    code VARCHAR(512) COLLATE utf8mb4_bin NOT NULL,
    inviter_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    activated_at VARCHAR(64) CHARACTER SET ascii NOT NULL,
    retired_at VARCHAR(64) CHARACTER SET ascii NULL,
    active_inviter_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS (IF(retired_at IS NULL, inviter_id, NULL)) STORED,
    PRIMARY KEY (code),
    UNIQUE KEY invitecode_one_active_code_per_inviter (active_inviter_id),
    KEY invitecode_codes_by_inviter (inviter_id),
    CONSTRAINT invitecode_codes_inviter_fk FOREIGN KEY (inviter_id) REFERENCES invitecode_players(player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- @table invitecode_invitations
CREATE TABLE invitecode_invitations (
    invitation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    inviter_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    inviter_eligibility_generation INT NOT NULL,
    invitee_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    invitee_eligibility_generation INT NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii NOT NULL,
    claimed_at VARCHAR(64) CHARACTER SET ascii NOT NULL,
    qualified_at VARCHAR(64) CHARACTER SET ascii NULL,
    qualification_policy_id VARCHAR(191) NULL,
    qualification_revision VARCHAR(191) CHARACTER SET ascii NULL,
    PRIMARY KEY (invitation_id),
    UNIQUE KEY invitecode_one_invitation_per_generation (invitee_id, invitee_eligibility_generation),
    KEY invitecode_query_invitations_by_inviter (inviter_id, claimed_at, invitation_id, status),
    CONSTRAINT invitecode_invitations_inviter_fk FOREIGN KEY (inviter_id) REFERENCES invitecode_players(player_id),
    CONSTRAINT invitecode_invitations_invitee_fk FOREIGN KEY (invitee_id) REFERENCES invitecode_players(player_id),
    CONSTRAINT invitecode_invitations_qualification CHECK (
        (qualified_at IS NULL AND qualification_policy_id IS NULL AND qualification_revision IS NULL) OR
        (qualified_at IS NOT NULL AND qualification_policy_id IS NOT NULL AND qualification_revision IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- @table invitecode_qualification_facts
CREATE TABLE invitecode_qualification_facts (
    fact_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    invitation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    policy_id VARCHAR(191) NOT NULL,
    qualification_revision VARCHAR(191) CHARACTER SET ascii NOT NULL,
    condition_id VARCHAR(191) NOT NULL,
    outcome VARCHAR(32) CHARACTER SET ascii NOT NULL,
    recorded_at VARCHAR(64) CHARACTER SET ascii NOT NULL,
    PRIMARY KEY (fact_id),
    KEY invitecode_query_qualification_facts (invitation_id, recorded_at, fact_id),
    CONSTRAINT invitecode_facts_invitation_fk FOREIGN KEY (invitation_id) REFERENCES invitecode_invitations(invitation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- @table invitecode_reward_deliveries
CREATE TABLE invitecode_reward_deliveries (
    delivery_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    invitation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    reward_id VARCHAR(191) NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii NOT NULL,
    idempotency_key TEXT NOT NULL,
    idempotency_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS (SHA2(idempotency_key, 256)) STORED,
    created_at VARCHAR(64) CHARACTER SET ascii NOT NULL,
    updated_at VARCHAR(64) CHARACTER SET ascii NOT NULL,
    PRIMARY KEY (delivery_id),
    UNIQUE KEY invitecode_delivery_idempotency (idempotency_hash),
    KEY invitecode_query_reward_deliveries_by_invitation (invitation_id, created_at, delivery_id),
    CONSTRAINT invitecode_delivery_invitation_fk FOREIGN KEY (invitation_id) REFERENCES invitecode_invitations(invitation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- @table invitecode_reward_actions
CREATE TABLE invitecode_reward_actions (
    action_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reward_delivery_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    invitation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    recipient_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    position INT NOT NULL,
    action_type VARCHAR(64) CHARACTER SET ascii NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii NOT NULL,
    idempotency_key TEXT NOT NULL,
    idempotency_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS (SHA2(idempotency_key, 256)) STORED,
    created_at VARCHAR(64) CHARACTER SET ascii NOT NULL,
    updated_at VARCHAR(64) CHARACTER SET ascii NOT NULL,
    last_error TEXT NULL,
    PRIMARY KEY (action_id),
    UNIQUE KEY invitecode_action_idempotency (idempotency_hash),
    UNIQUE KEY invitecode_action_position (reward_delivery_id, position),
    KEY invitecode_pending_reward_actions_by_recipient (recipient_id, status, created_at),
    KEY invitecode_query_reward_actions_by_status (status),
    KEY invitecode_action_invitation (invitation_id),
    CONSTRAINT invitecode_action_delivery_fk FOREIGN KEY (reward_delivery_id) REFERENCES invitecode_reward_deliveries(delivery_id),
    CONSTRAINT invitecode_action_invitation_fk FOREIGN KEY (invitation_id) REFERENCES invitecode_invitations(invitation_id),
    CONSTRAINT invitecode_action_recipient_fk FOREIGN KEY (recipient_id) REFERENCES invitecode_players(player_id),
    CONSTRAINT invitecode_action_position_nonnegative CHECK (position >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- @table invitecode_audit_records
CREATE TABLE invitecode_audit_records (
    audit_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    action VARCHAR(64) CHARACTER SET ascii NOT NULL,
    actor VARCHAR(191) NOT NULL,
    reason TEXT NOT NULL,
    occurred_at VARCHAR(64) CHARACTER SET ascii NOT NULL,
    invitation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    player_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    details_kind VARCHAR(32) CHARACTER SET ascii NOT NULL,
    previous_inviter_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    new_inviter_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    qualification_policy_id VARCHAR(191) NULL,
    qualification_revision VARCHAR(191) CHARACTER SET ascii NULL,
    PRIMARY KEY (audit_id),
    KEY invitecode_query_audit_by_invitation (invitation_id, occurred_at, audit_id),
    KEY invitecode_query_audit_by_player (player_id, occurred_at, audit_id),
    CONSTRAINT invitecode_audit_invitation_fk FOREIGN KEY (invitation_id) REFERENCES invitecode_invitations(invitation_id),
    CONSTRAINT invitecode_audit_player_fk FOREIGN KEY (player_id) REFERENCES invitecode_players(player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- @table invitecode_reward_delivery_transitions
CREATE TABLE invitecode_reward_delivery_transitions (
    transition_id BIGINT NOT NULL AUTO_INCREMENT,
    delivery_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    previous_status VARCHAR(32) CHARACTER SET ascii NULL,
    current_status VARCHAR(32) CHARACTER SET ascii NOT NULL,
    occurred_at VARCHAR(64) CHARACTER SET ascii NOT NULL,
    actor VARCHAR(191) NULL,
    reason TEXT NULL,
    PRIMARY KEY (transition_id),
    KEY invitecode_reward_delivery_transition_history (delivery_id, transition_id),
    CONSTRAINT invitecode_transition_delivery_fk FOREIGN KEY (delivery_id) REFERENCES invitecode_reward_deliveries(delivery_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- @table invitecode_reward_action_transitions
CREATE TABLE invitecode_reward_action_transitions (
    transition_id BIGINT NOT NULL AUTO_INCREMENT,
    action_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    previous_status VARCHAR(32) CHARACTER SET ascii NULL,
    current_status VARCHAR(32) CHARACTER SET ascii NOT NULL,
    occurred_at VARCHAR(64) CHARACTER SET ascii NOT NULL,
    last_error TEXT NULL,
    actor VARCHAR(191) NULL,
    reason TEXT NULL,
    PRIMARY KEY (transition_id),
    KEY invitecode_reward_action_transition_history (action_id, transition_id),
    CONSTRAINT invitecode_transition_action_fk FOREIGN KEY (action_id) REFERENCES invitecode_reward_actions(action_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- @table invitecode_player_login_days
CREATE TABLE invitecode_player_login_days (
    player_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    login_date CHAR(10) CHARACTER SET ascii NOT NULL,
    PRIMARY KEY (player_id, login_date),
    CONSTRAINT invitecode_login_player_fk FOREIGN KEY (player_id) REFERENCES invitecode_players(player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- @table invitecode_player_network_hashes
CREATE TABLE invitecode_player_network_hashes (
    player_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    key_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    network_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    observed_at VARCHAR(64) CHARACTER SET ascii NOT NULL,
    expires_at VARCHAR(64) CHARACTER SET ascii NOT NULL,
    PRIMARY KEY (player_id, key_id, network_hash),
    KEY invitecode_network_hash_expiry (expires_at),
    KEY invitecode_network_hash_match (key_id, network_hash, player_id, expires_at),
    CONSTRAINT invitecode_network_player_fk FOREIGN KEY (player_id) REFERENCES invitecode_players(player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
