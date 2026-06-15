# C6 JPA 残留清理与测试恢复报告 v2

**作者：Manus AI**  
**日期：2026-06-15**  
**适用范围：直播选秀项目后端 C6 卡密生成器模块**

## 一、执行背景

本次修复依据 Claude 的《C6 污染范围核查 + 批准修复方案》执行。Claude 的裁定明确指出，C2~C5 的已提交测试和业务代码未被污染，当前连锁失败的根因是移除 JPA 后未补充 `JdbcTemplate` 所需的轻量 JDBC starter。因此，本次执行严格遵守**最小变更原则**：不修改 C2~C5 测试与业务代码，只处理依赖替换、JPA 残留删除和 Token 实体中的 JPA 注解清理。

> Claude 裁定的核心约束是：**“C2~C5 测试与业务代码一律不动；pom.xml 删除 `spring-boot-starter-data-jpa`，新增 `spring-boot-starter-jdbc`；删除 `TokenRepository.java`；Token 实体移除 JPA 注解；全量 `mvn test`。”**

## 二、实际变更摘要

本次修复最终只保留了 3 个源码级变更文件，未保留此前本地试错过程中对 C2~C5 测试文件、应用入口类或 C6 业务逻辑的临时改动。这样可以确保修复范围与 Claude 裁定一致，避免把测试改坏或引入额外业务行为变化。

| 文件 | 变更类型 | 说明 |
|---|---:|---|
| `backend/redface-backend/pom.xml` | 依赖替换 | 删除 `spring-boot-starter-data-jpa`，新增 `spring-boot-starter-jdbc`，以保留 `JdbcTemplate` 能力但不引入 JPA/Hibernate。 |
| `backend/redface-backend/src/main/java/com/redface/repository/TokenRepository.java` | 删除文件 | 删除 C6 中遗留的 JPA Repository，避免项目架构继续混用 JPA 与 MyBatis。 |
| `backend/redface-backend/src/main/java/com/redface/model/Token.java` | 注解清理 | 移除 `jakarta.persistence` 相关 import 与 `@Entity`、`@Table`、`@Id`、`@Column` 等 JPA 注解，保留字段与 getter/setter，不改变业务数据结构。 |

## 三、未修改范围确认

根据 Claude 裁定，本次不应再触碰 C2~C5 的测试和业务代码。因此，我已恢复并清理本地试错期间产生的临时改动，最终提交范围不包含下列文件的修改。

| 文件或范围 | 当前处理方式 | 原因 |
|---|---|---|
| `PopularityServiceC2Test.java` | 不修改 | Claude 已核查提交版本未污染，失败根因不是测试逻辑。 |
| `PopularityServiceC3Test.java` | 不修改 | 保持原有 Spring Boot 集成测试结构，不改为 Mockito 单元测试。 |
| `PopularityServiceC4Test.java` | 不修改 | 避免破坏已审查通过的积分衰减测试。 |
| `TokenServiceC5Test.java` | 不修改当前提交版本 | 保留此前已提交且被 Claude 认可的 `created_at` 适配，不再额外改动。 |
| `PopularityService.java` / `TokenService.java` | 不修改 | 本次故障为依赖清理问题，不涉及业务逻辑修复。 |
| `RedfaceBackendApplication.java` | 不修改 | 已撤回此前临时加入的 `@MapperScan`，避免产生非必要配置变更。 |
| `TokenGeneratorServiceImpl.java` | 不修改 | 已撤回此前临时加入的边界值逻辑，遵守“一行业务代码都不要碰”的裁定。 |

## 四、JPA 残留核查结果

修复后已执行全局检索，确认主代码和测试代码中不再存在 JPA 关键引用。检索关键词包括 `org.springframework.data.jpa`、`JpaRepository`、`jakarta.persistence`、`@Entity`、`@Table` 和 `@Id`。

| 检查项 | 结果 |
|---|---:|
| `src/main/java` 下 `repository` 目录 | 已删除空目录。 |
| `TokenRepository.java` | 已删除。 |
| `JpaRepository` 引用 | 未发现。 |
| `org.springframework.data.jpa` 引用 | 未发现。 |
| `jakarta.persistence` 引用 | 未发现。 |
| `@Entity` / `@Table` / `@Id` JPA 注解 | 未发现。 |

## 五、全量测试结果

已在 `backend/redface-backend` 目录执行全量测试命令，并将完整输出保存到 `reports/C6_mvn_test_output_v1.txt`。

```bash
mvn test
```

最终测试结果如下，C2~C6 相关测试已恢复全绿。

```text
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  7.159 s
[INFO] Finished at: 2026-06-15T14:37:55Z
[INFO] ------------------------------------------------------------------------
```

| 测试范围 | 结果 |
|---|---:|
| `TokenGeneratorServiceTest` | 通过 |
| `PopularityServiceC2Test` | 通过 |
| `PopularityServiceC3Test` | 通过 |
| `PopularityServiceC4Test` | 通过 |
| `SchemaInitializationTest` | 通过 |
| `TokenServiceC5Test` | 通过 |
| 全量测试汇总 | `Tests run: 20, Failures: 0, Errors: 0, Skipped: 0` |

## 六、结论

本次修复确认了 Claude 的诊断：C2~C5 并未被提交层面的代码污染，连锁失败来自 JPA 依赖移除后未补充 `spring-boot-starter-jdbc`。通过新增 JDBC starter、删除 JPA Repository、清理 Token 实体 JPA 注解后，`mvn test` 已全量通过。

后续建议继续执行当前协作纪律：涉及技术判断、修复方案、架构取舍或测试失败分析时，先形成 Markdown 文件并上传 GitHub，由 John 转发 Claude 确认；确认后再实施代码变更。同时继续遵守**同一错误连续修复 3 次仍不过即停步上报**的规则。

## References

[1]: reports/C6_mvn_test_output_v1.txt "C6 Maven 全量测试输出 v1"
[2]: backend/redface-backend/pom.xml "后端 Maven 依赖配置"
[3]: backend/redface-backend/src/main/java/com/redface/model/Token.java "Token 模型类"
