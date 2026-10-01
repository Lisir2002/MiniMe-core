package com.mini.lintchecks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;

import org.jetbrains.uast.UCallExpression;

import java.util.EnumSet;
import java.util.List;

/**
 * 自定义检查演示：禁止在主业务代码中直接使用 System.out/err 控制台输出。
 *
 * <p>此为 custom_lint 框架的首个规则，severity=WARNING（不阻断发版），仅作提示与示范。
 * 后续可在此模块内新增更多 Detector，并在 {@link MiniMeIssueRegistry} 注册。
 */
public class SystemOutDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "SystemOutUsage",
            "不应直接使用 System.out/err 控制台输出",
            "应改用项目统一的 Logger 记录日志；直接控制台输出在 release 中无意义且可能泄漏内部状态。",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(SystemOutDetector.class, EnumSet.of(Scope.JAVA_FILE))
    );

    @Override
    public List<String> getApplicableMethodNames() {
        return List.of("print", "println");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        if (method == null) {
            return;
        }
        String fq = method.getContainingClass() != null
                ? method.getContainingClass().getQualifiedName()
                : "";
        // 仅命中 java.io.PrintStream（System.out / System.err 的运行时类型）。
        if ("java.io.PrintStream".equals(fq)) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "直接控制台输出，建议改用项目 Logger"
            );
        }
    }
}
