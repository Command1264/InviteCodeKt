# Persist reward delivery before execution

Reward Delivery and each Reward Action are persisted before external commands or integrations run. A non-safe interruption while an action is processing produces an `UNCERTAIN` action that requires an administrator decision instead of an automatic retry, because Bukkit commands and third-party economy operations cannot provide end-to-end exactly-once execution.
