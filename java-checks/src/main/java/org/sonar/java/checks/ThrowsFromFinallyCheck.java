/*
 * SonarQube Java
 * Copyright (C) 2012 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.java.checks;

import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.check.BelongsToProfile;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.plugins.java.api.JavaFileScanner;
import org.sonar.plugins.java.api.JavaFileScannerContext;
import org.sonar.plugins.java.api.tree.BaseTreeVisitor;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.ThrowStatementTree;
import org.sonar.plugins.java.api.tree.TryStatementTree;
import org.sonar.squidbridge.annotations.SqaleConstantRemediation;
import org.sonar.squidbridge.annotations.SqaleSubCharacteristic;

@Rule(
  key = ThrowsFromFinallyCheck.KEY,
  name = "Exceptions should not be thrown in finally blocks",
  priority = Priority.MAJOR)
@BelongsToProfile(title = "Sonar way", priority = Priority.MAJOR)
@SqaleSubCharacteristic(value = RulesDefinition.SubCharacteristics.EXCEPTION_HANDLING)
@SqaleConstantRemediation(value = "20min")
public class ThrowsFromFinallyCheck extends BaseTreeVisitor implements JavaFileScanner {

  public static final String KEY = "S1163";
  private static final RuleKey RULEKEY = RuleKey.of(CheckList.REPOSITORY_KEY, KEY);
  private JavaFileScannerContext context;

  private int finallyLevel = 0;
  private boolean isInMethodWithinFinally;

  @Override
  public void scanFile(JavaFileScannerContext context) {
    this.context = context;
    scan(context.getTree());
  }

  @Override
  public void visitTryStatement(TryStatementTree tree) {
    scan(tree.resources());
    scan(tree.block());
    scan(tree.catches());
    finallyLevel++;
    scan(tree.finallyBlock());
    finallyLevel--;
  }

  @Override
  public void visitThrowStatement(ThrowStatementTree tree) {
    if(isInFinally() && !isInMethodWithinFinally){
      context.addIssue(tree, RULEKEY, "Refactor this code to not throw exceptions in finally blocks.");
    }
    super.visitThrowStatement(tree);
  }

  @Override
  public void visitMethod(MethodTree tree) {
    isInMethodWithinFinally = isInFinally();
    super.visitMethod(tree);
    isInMethodWithinFinally = false;
  }

  private boolean isInFinally(){
    return finallyLevel>0;
  }

}
