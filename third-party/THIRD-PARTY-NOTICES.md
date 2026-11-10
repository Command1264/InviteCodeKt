# InviteCode third-party notices

InviteCode's own source code is licensed under Apache-2.0. The bundled components below retain their respective terms. License texts are in the adjacent licenses directory. In the plugin JAR, this file and those texts are under META-INF/InviteCode. The JAR's root LICENSE is also retained from MySQL Connector/J.

| Component | Version | License and attribution |
| --- | --- | --- |
| Kotlin standard library | 2.3.21 | Apache-2.0, JetBrains and contributors. The Kotlin distribution notice is included for attribution. |
| JetBrains Annotations | 13.0 | Apache-2.0, JetBrains and contributors. See the published [POM](https://repo.maven.apache.org/maven2/org/jetbrains/annotations/13.0/annotations-13.0.pom). |
| Gson | 2.14.0 | Apache-2.0, Google and contributors. Gson classes are relocated in the plugin JAR. |
| Error Prone Annotations | 2.48.0 | Apache-2.0, The Error Prone Authors. |
| SQLite JDBC | 3.53.4.0 | Apache-2.0, Xerial and contributors, with the original Zentus license retained. The native SQLite engine is [public domain](https://www.sqlite.org/copyright.html). |
| MySQL Connector/J | 26.7.0 | GPL-2.0-only with Oracle's Universal FOSS Exception 1.0 and additional terms in the supplied licensing manual. |
| Protocol Buffers Java | 4.31.1 | BSD-3-Clause, Google and contributors. |
| HikariCP | 4.0.3 | Apache-2.0, Brett Wooldridge and contributors. |
| SLF4J API and JDK14 binding | 1.7.36 | MIT, QOS.ch and contributors. |

## Source availability

Distribute the complete corresponding InviteCode source from the commit recorded in the plugin manifest alongside the binary. For the bundled MySQL Connector/J, provide the companion `mysql-connector-j-26.7.0-source.zip` alongside the binary at the same download location. This archive is the upstream [26.7.0 tag](https://github.com/mysql/mysql-connector-j/tree/26.7.0) at commit `559f62df01d4f618440da174ff45ba87f7b3b2a8`. It includes the source, LICENSE and Ant build scripts. InviteCode bundles the upstream driver without relocating its package.

- [Pinned upstream source ZIP](https://codeload.github.com/mysql/mysql-connector-j/zip/559f62df01d4f618440da174ff45ba87f7b3b2a8)
- Source ZIP SHA-256: `598964de0e7f03dbea7cab18977d911f5f969bb78a91ab39ee345e68ce1231c2`
- [Connector/J build instructions](https://dev.mysql.com/doc/connector-j/en/connector-j-installing-source.html)
- [Oracle Universal FOSS Exception](https://oss.oracle.com/licenses/universal-foss-exception/)

The upstream URL records the source origin. The release distribution should provide its own copy of the companion archive.
