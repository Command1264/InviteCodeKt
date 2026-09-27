-- @table invitecode_reserved_code_hashes
CREATE TABLE invitecode_reserved_code_hashes (
    code_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    key_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (code_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
