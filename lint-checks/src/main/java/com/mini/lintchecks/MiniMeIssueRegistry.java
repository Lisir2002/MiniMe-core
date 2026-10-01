package com.mini.lintchecks;

import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.detector.api.Issue;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * MiniMe-core 自定义 Lint 规则注册表。
 *
 * <p>新增自定义检查时：1) 编写 Detector；2) 创建 {@link Issue}；3) 在此处 {@link #getIssues()} 注册。
 * lint 通过 META-INF/services 发现本类，:app:lintRelease 运行时自动加载。
 */
public class MiniMeIssueRegistry extends IssueRegistry {

    @Override
    public @NotNull List<Issue> getIssues() {
        List<Issue> issues = new ArrayList<>();
        issues.add(SystemOutDetector.ISSUE);
        return issues;
    }
}
