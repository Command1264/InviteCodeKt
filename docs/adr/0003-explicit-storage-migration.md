# Switch storage only through explicit migration

SQLite and MySQL are production backends, while JSON is limited to small installations and interchange. Changing backend configuration never migrates data during ordinary startup or reload; an explicit preflight, migration, verification, and cutover workflow preserves the source on failure and prevents silent split-brain data.
