# Ship one cross-version plugin JAR

InviteCodeKt ships one installable JAR for Spigot and Paper 1.8.8 through the latest tested release. Platform-independent and legacy code targets Java 8 without NMS; references to newer server APIs live in isolated capability adapters so legacy JVMs never load incompatible classes. This keeps installation simple while accepting the cost of a representative real-server compatibility matrix.
